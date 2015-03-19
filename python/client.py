#!/usr/bin/python

import os
import sys
import time
import json
import tempfile
import signal
import subprocess
import threading
import logging
import logging.handlers
import socket
from math import ceil
from exchanges import *
from trading import *
from utils import *

if len(sys.argv) < 2:
  print "usage:", sys.argv[0], "server[:port] [users.dat]"
  sys.exit(1)

if not os.path.isdir('logs'):
  os.makedirs('logs')

logger = logging.getLogger()
logger.setLevel(logging.DEBUG)
sh = logging.handlers.SocketHandler('', logging.handlers.DEFAULT_TCP_LOGGING_PORT)
sh.setLevel(logging.DEBUG)
fh = logging.FileHandler('logs/%d.log' % time.time())
fh.setLevel(logging.DEBUG)
ch = logging.StreamHandler()
ch.setLevel(logging.INFO)
formatter = logging.Formatter(fmt = '%(asctime)s %(levelname)s: %(message)s', datefmt="%Y/%m/%d-%H:%M:%S")
sh.setFormatter(formatter)
fh.setFormatter(formatter)
ch.setFormatter(formatter)
logger.addHandler(sh)
logger.addHandler(fh)
logger.addHandler(ch)

userfile = 'users.dat'
if len(sys.argv) == 3:
  userfile = sys.argv[2]
try:
  userdata = [ line.strip().split() for line in open(userfile).readlines() if len(line.strip().split()) >= 5 ] # address units exchange key secret [trader]
except:
  logger.error("%s could not be read", userfile)
  sys.exit(1)

_server = sys.argv[1]
_wrappers = { 'poloniex' : Poloniex(), 'ccedk' : CCEDK(), 'bitcoincoid' : BitcoinCoId(), 'bter' : BTER() }

# one request signer thread for each key and unit
class RequestThread(ConnectionThread):
  def __init__(self, conn, key, secret, exchange, unit, address, sampling, logger = None):
    super(RequestThread, self).__init__(conn, logger)
    self.key = key
    self.secret = secret
    self.exchange = exchange
    self.unit = unit
    self.initsampling = sampling
    self.sampling = sampling
    self.address = address
    self.errorflag = False
    self.trials = 0

  def register(self):
    response = self.conn.post('register', {'address' : self.address, 'key' : self.key, 'name' : repr(self.exchange)})
    if response['code'] == 0: # reset sampling in case of server restart
      self.sampling = self.initsampling
    return response

  def submit(self):
    data, sign = self.exchange.create_request(self.unit, self.key, self.secret)
    params = { 'unit' : self.unit, 'user' : self.key, 'sign' : sign }
    params.update(data)
    ret = self.conn.post('liquidity', params, 1)
    if ret['code'] != 0:
      self.trials += 1
      self.logger.error("submit: %s" % ret['message'])
      if ret['code'] == 11: # user unknown, just register again
        self.register()
    else:
      self.trials = 0
    self.errorflag = self.trials >= self.sampling * 2 # notify that something is wrong after 2 minutes of failures

  def run(self):
    ret = self.register()
    if ret['code'] != 0: self.logger.error("register: %s" % ret['message'])
    while self.active:
      curtime = time.time()
      self.submit()
      time.sleep(max(60.0 / self.sampling - time.time() + curtime, 0))

# retrieve initial data
conn = Connection(_server, logger)
basestatus = conn.get('status')
exchanges = conn.get('exchanges')
exchanges['time'] = time.time()
sampling = max(1, min(120, int(basestatus['sampling'] * 1.5)))

# parse user data
users = {}
for user in userdata:
  key = user[3]
  secret = user[4]
  name = user[2].lower()
  if not name in _wrappers:
    logger.error("unknown exchange: %s", user[2])
    sys.exit(2)
  if not name in exchanges:
    logger.error("exchange not supported by pool: %s", name)
    sys.exit(2)
  units = [ unit.lower() for unit in user[1].split(',') ]
  exchange = _wrappers[user[2].lower()]
  users[key] = {}
  for unit in user[1].split(','):
    unit = unit.lower()
    if not unit in exchanges[name]:
      logger.error("unit %s on exchange %s not supported by pool: %s", unit, name)
      sys.exit(2)
    users[key][unit] = { 'request' : RequestThread(conn, key, secret, exchange, unit, user[0], sampling, logger) }
    users[key][unit]['request'].start()
    bot = 'pybot' if len(user) < 6 else user[5]
    cost = exchanges[name][unit]['rate'] if len(user) < 7 else user[6]

    if bot == 'none':
      users[key][unit]['order'] = None
    elif bot == 'nubot':
      users[key][unit]['order'] = NuBot(conn, users[key][unit]['request'], key, secret, exchange, unit, logger)
    elif bot == 'pybot':
      users[key][unit]['order'] = PyBot(conn, users[key][unit]['request'], key, secret, exchange, unit, cost, logger)
    else:
      logger.error("unknown order handler: %s", bot)
      users[key][unit]['order'] = None
    if users[key][unit]['order']:
      if users[key][unit]['order']:
        users[key][unit]['order'].start()

logger.debug('starting liquidity propagation with sampling %d' % sampling)
starttime = time.time()
curtime = time.time()

while True: # print some info every minute until program terminates
  try:
    time.sleep(max(60 - time.time() + curtime, 0))
    curtime = time.time()
    for user in users: # post some statistics
      response = conn.get(user, trials = 1)
      if 'error' in response:
        logger.error('unable to receive statistics for user %s: %s', user, response['message'])
        users[user].values()[0]['request'].register() # reassure to be registered if 
      else:
        logger.info('%s - balance: %.8f efficiency: %.2f%% rejects: %d missing: %d units: %s - %s', repr(users[user].values()[0]['request'].exchange),
          response['balance'], response['efficiency'] * 100, response['rejects'], response['missing'], response['units'], user )
        if curtime - starttime > 90:
          if response['efficiency'] < 0.8:
            for unit in response['units']:
              if response['units'][unit]['rejects'] / float(basestatus['sampling']) >= 0.1: # look for valid error and adjust nonce shift
                if response['units'][unit]['last_error'] != "":
                  if 'deviates too much from current price' in response['units'][unit]['last_error']:
                    PyBot.pricefeed.price(unit, True) # Force a price update
                    logger.warning('price missmatch for unit %s on exchange %s, forcing price update', unit, repr(users[user][unit]['request'].exchange))
                  else:
                    users[user][unit]['request'].exchange.adjust(response['units'][unit]['last_error'])
                    logger.warning('too many rejected requests for unit %s on exchange %s, adjusting nonce to %d',
                      unit, repr(users[user][unit]['request'].exchange), users[user][unit]['request'].exchange._shift)
                    break
              if response['units'][unit]['missing'] / float(basestatus['sampling']) >= 0.1: # look for missing error and adjust sampling
                if users[user][unit]['request'].sampling < 120:  # just send more requests
                  users[user][unit]['request'].sampling = users[user][unit]['request'].sampling + 1
                  logger.warning('too many missing requests for unit %s on exchange %s, increasing sampling to %d',
                    unit, repr(users[user][unit]['request'].exchange), users[user][unit]['request'].sampling)
                else: # just wait a little bit
                  logger.warning('too many missing requests, sleeping a short while to synchronize')
                  curtime += 0.7
          elif response['efficiency'] >= 0.9 and response['efficiency'] < 1.0:
            for unit in response['units']:
              if (response['units'][unit]['rejects'] + response['units'][unit]['missing']) / float(basestatus['sampling']) >= 0.05:
                if users[user][unit]['request'].sampling < 100:  # send some more requests
                  users[user][unit]['request'].sampling = users[user][unit]['request'].sampling + 1
                  logger.warning('trying to optimize efficiency by increasing sampling of unit %s on exchange %s to %d',
                    unit, repr(users[user][unit]['request'].exchange), users[user][unit]['request'].sampling)

  except KeyboardInterrupt: break
  except Exception as e:
    logger.error('exception caught: %s', sys.exc_info()[1])

for user in users:
  for unit in users[user]:
    if users[user][unit]['order']:
      users[user][unit]['order'].shutdown()