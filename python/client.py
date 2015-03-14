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
    self.sampling = sampling
    self.address = address

  def run(self):
    ret = self.conn.post('register', {'address' : self.address, 'key' : self.key, 'name' : repr(self.exchange)})
    if ret['code'] != 0: self.logger.error("register: %s" % ret['message'])
    while self.active:
      curtime = time.time()
      data, sign = self.exchange.create_request(self.unit, self.key, self.secret)
      params = { 'unit' : self.unit, 'user' : self.key, 'sign' : sign }
      params.update(data)
      ret = self.conn.post('liquidity', params)
      if ret['code'] != 0:
        self.logger.error("submit: %s" % ret['message'])
        if ret['code'] == 11: # user unknown, just register again
          self.conn.post('register', {'address' : self.address, 'key' : self.key, 'name' : repr(self.exchange)})
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
      users[key][unit]['order'].start()

logger.debug('starting liquidity propagation with sampling %d' % sampling)
starttime = time.time()

while True: # print some info every minute until program terminates
  try:
    curtime = time.time()
    for user in users:
      response = conn.get(user)
      logger.info('%s - balance: %.2f efficiency: %.2f%% rejects: %d missing: %d units: %s - %s', repr(users[user].values()[0]['request'].exchange),
        response['balance'], response['efficiency'] * 100, response['rejects'], response['missing'], response['units'], user )
      
      if response['efficiency'] < 0.8 and curtime - starttime > 90:
        for unit in response['units']:
          if response['units'][unit]['rejects'] / float(basestatus['sampling']) >= 0.2: # look for valid error and adjust nonce shift
            if response['units'][unit]['last_error'] != "":
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

    time.sleep(max(60 - time.time() + curtime, 0))
  except KeyboardInterrupt: break
  except Exception as e:
    logger.error('exception caught: %s', sys.exc_info()[1])

for user in users:
  for unit in users[user]:
    if users[user][unit]['order']:
      users[user][unit]['order'].shutdown()