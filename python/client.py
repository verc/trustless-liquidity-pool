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
fh = logging.FileHandler('logs/%d.log' % time.time())
fh.setLevel(logging.DEBUG)
ch = logging.StreamHandler()
ch.setLevel(logging.INFO)
formatter = logging.Formatter(fmt = '%(asctime)s %(levelname)s: %(message)s', datefmt="%Y/%m/%d-%H:%M:%S")
fh.setFormatter(formatter)
ch.setFormatter(formatter)
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
conn = Connection(_server)
basestatus = conn.get('status')
exchanges = conn.get('exchanges')
exchanges['time'] = time.time()
sampling = min(45, basestatus['sampling'] + 1)

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

validations = basestatus['validations']
efficiency = { user : [0,0] for user in users }
logger.debug('starting liquidity propagation with sampling %d' % sampling)

while True: # print some info every minute until program terminates
  try:
    curtime = time.time()
    for user in users:
      logger.info(conn.get(user))
        
    time.sleep(max(60 - time.time() + curtime, 0))
  except KeyboardInterrupt: break
  except Exception as e:
    logger.error('exception caught: %s', str(e))

for user in users:
  for unit in users[user]:
    if users[user][unit]['order']:
      users[user][unit]['order'].shutdown()