#! /usr/bin/env python

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
sh = logging.handlers.SocketHandler('', 9463)
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
  userdata = [ line.strip().split() for line in open(userfile).readlines() ] # address units exchange key secret [trader]
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

  def run(self):
    ret = self.register()
    if ret['code'] != 0: self.logger.error("register: %s" % ret['message'])
    while self.active:
      curtime = time.time()
      data, sign = self.exchange.create_request(self.unit, self.key, self.secret)
      params = { 'unit' : self.unit, 'user' : self.key, 'sign' : sign }
      params.update(data)
      ret = self.conn.post('liquidity', params, 1)
      if ret['code'] != 0:
        self.trials += 1
        self.errorflag = self.trials >= self.sampling * 5 # notify that something is wrong after 5 minutes of failures
        self.logger.error("submit: %s" % ret['message'])
        if ret['code'] == 11: # user unknown, just register again
          self.register()
      else:
        self.trials = 0
        self.errorflag = False
      time.sleep(max(60 / self.sampling - time.time() + curtime, 0))

# retrieve initial data
conn = Connection(_server, logger)
basestatus = conn.get('status')
exchanges = conn.get('exchanges')
exchanges['time'] = time.time()
sampling = min(45, basestatus['sampling'] + 2)

# parse user data
users = {}
for user in userdata:
  key = user[3]
  secret = user[4]
  if not user[2].lower() in _wrappers:
    logger.error("unknown exchange: %s", user[2])
    sys.exit(2)
  units = [ unit.lower() for unit in user[1].split(',') ]
  exchange = _wrappers[user[2].lower()]
  users[key] = {}
  for unit in user[1].split(','):
    unit = unit.lower()
    users[key][unit] = { 'request' : RequestThread(conn, key, secret, exchange, unit, user[0], sampling, logger) }
    users[key][unit]['request'].start()
    bot = 'pybot'
    if len(user) == 6: bot = user[5]
    if bot == 'none':
      users[key][unit]['order'] = None
    elif bot == 'nubot': 
      users[key][unit]['order'] = NuBot(conn, key, secret, exchange, unit, logger)
    elif bot == 'pybot': 
      users[key][unit]['order'] = PyBot(conn, key, secret, exchange, unit, logger)
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
    for user in users:
      for unit in users[user]:
        # if request sender doesn't work correctly, shut down trading for now
        if not users[user][unit]['order'].pause and users[user][unit]['request'].errorflag:
          logger.warning('shutting down trading bot for user %s because of bad server communication', user)
          users[user][unit]['order'].shutdown()
        users[user][unit]['order'].pause = users[user][unit]['request'].errorflag
      # post some statistics
      response = conn.get(user, trials = 1)
      if 'error' in response:
        logger.error('unable to receive statistics for user %s: %s', user, response['message'])
        if response['error'] == 'socket error': # this could mean the server just went down
          users[user].values()[0]['request'].register()
      else:
        logger.info('%s - balance: %.8f efficiency: %.2f%% rejects: %d missing: %d units: %s - %s', repr(users[user].values()[0]['request'].exchange),
          response['balance'], response['efficiency'] * 100, response['rejects'], response['missing'], response['units'], user )
        if response['efficiency'] < 0.8 and curtime - starttime > 90:
          for unit in response['units']:
            if response['units'][unit]['rejects'] / float(basestatus['sampling']) >= 0.2: # look for valid error and adjust nonce shift
              if response['units'][unit]['last_error'] != "":
                if 'deviates too much from price' in response['units'][unit]['last_error']:
                  PyBot.pricefeed.price(unit, True) # Force a price update
                  logger.warning('price missmatch on exchange %s, forcing price update', repr(users[user][unit]['request'].exchange))
                else:
                  logger.warning('too many rejected requests on exchange %s, adjusting nonce to %d', repr(users[user][unit]['request'].exchange), users[user][unit]['request'].exchange._shift)
                  users[user][unit]['request'].exchange.acquire_lock()
                  users[user][unit]['request'].exchange.adjust(response['units'][unit]['last_error'])
                  users[user][unit]['request'].exchange.release_lock()
                  break
            if response['units'][unit]['missing'] / float(basestatus['sampling']) >= 0.2: # look for valid error and adjust nonce shift
              if users[user][unit]['request'].sampling < 45:  # just send more requests
                users[user][unit]['request'].sampling = users[user][unit]['request'].sampling + 1
                logger.warning('too many missing requests, increasing sampling to %d', users[user][unit]['request'].sampling)
              else: # just wait a little bit
                logger.warning('too many missing requests, sleeping a short while to synchronize')
                time.sleep(0.7)
  except KeyboardInterrupt: break
  except Exception as e:
    logger.error('exception caught: %s', sys.exc_info()[1])

for user in users:
  for unit in users[user]:
    if users[user][unit]['order']:
      users[user][unit]['order'].shutdown()