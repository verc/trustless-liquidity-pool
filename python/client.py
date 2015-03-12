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

if len(sys.argv) < 2:
  print "usage:", sys.argv[0], "server[:port] [users.dat]"
  sys.exit(1)

if not os.path.isdir('logs'):
  os.makedirs('logs')

userfile = 'users.dat'
if len(sys.argv) == 3:
  userfile = sys.argv[2]
try:
  userdata = [ line.strip().split() for line in open(userfile).readlines() ] # address units exchange key secret [trader]
except:
  print "%s could not be read" % userfile
  sys.exit(1)

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

_server = sys.argv[1]
_wrappers = { 'poloniex' : Poloniex(), 'ccedk' : CCEDK(), 'bitcoincoid' : BitcoinCoId() }

class Connection():
  def __init__(self, server, logger = None):
    self.logger = logger
    self.server = server

  def json_request(self, request, method, params, headers):
    connection = httplib.HTTPConnection(self.server, timeout=60)
    try:
      connection.request(request, method, urllib.urlencode(params), headers = headers)
      response = connection.getresponse()
      content = response.read()
      return json.loads(content)
    except httplib.BadStatusLine:
      if self.logger: self.logger.error("%s: server could not be reached, retrying in 15 seconds ...", method)
    except ValueError:
      if self.logger: self.logger.error("%s: server response invalid, retrying in 15 seconds ... %s", method, content)
    except socket.error:
      if self.logger: self.logger.error("%s: socket error, retrying in 15 seconds ...", method)
    except:
      if self.logger: self.logger.error("%s: unknown connection error, retrying in 15 seconds ...", method)
    time.sleep(15)
    return self.json_request(request, method, params, headers)

  def get(self, method, params = None):
    if not params: params = {}
    return self.json_request('GET', '/' + method, params, {})

  def post(self, method, params = None):
    if not params: params = {}
    headers = { "Content-type": "application/x-www-form-urlencoded" }
    return self.json_request('POST', method, params, headers)

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
    if ret['code'] != 0 and self.logger: self.logger.error("register: %s" % ret['message'])
    while self.active:
      curtime = time.time()
      data, sign = self.exchange.create_request(self.unit, self.key, self.secret)
      params = { 'unit' : self.unit, 'user' : self.key, 'sign' : sign }
      params.update(data)
      ret = self.conn.post('liquidity', params)
      if ret['code'] != 0:
        if self.logger: self.logger.error("submit: %s" % ret['message'])
        if ret['code'] == 11: # user unknown, just register again
          self.conn.post('register', {'address' : self.address, 'key' : self.key, 'name' : repr(self.exchange)})
      time.sleep(max(60 / self.sampling - time.time() + curtime, 0))

# retreive initial data
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

try:
  while True: # print some info every minute until program terminates
    curtime = time.time()
    status = conn.get('status')
    passed = status['validations'] - validations
    validations = status['validations']
    if passed > 0:
      for user in users:
        stats = conn.get(user)
        units = {}
        rejects = 0
        missing = 0
        for unit in stats['units']:
          rejects += stats['units'][unit]['rejects']
          missing += stats['units'][unit]['missing']
          units[unit] = { 'bid' : sum([x[1] for x in stats['units'][unit]['bid']]),
                          'ask' : sum([x[1] for x in stats['units'][unit]['ask']]),
                          'last_error' : stats['units'][unit]['last_error'] }
        if validations - basestatus['validations'] > status['sampling']: # do not adjust in initial phase
          if missing - efficiency[user][0] > passed / 5:
            for unit in stats['units']:
              if users[user][unit]['request'].sampling < 45: # just send more requests
                users[user][unit]['request'].sampling += 1
                logger.warning('too many missing requests, adjusting sampling to %d', sampling)
              else: # just wait a little bit
                time.sleep(0.7)
                logger.warning('too many missing requests, sleeping a short while')
          if rejects - efficiency[user][1] > passed / 5: # look for valid error and adjust nonce shift
            for unit in stats['units']:
              if stats['units'][unit]['last_error'] != "":
                logger.warning('too many rejected requests on exchange %s, trying to adjust nonce of exchange to %d', repr(users[user]['request'].exchange), users[user]['request'].exchange._shift)
                if users[user][unit]['order']: users[user][unit]['order'].acquire_lock()
                users[user][unit]['order'].exchange.adjust(stats['units'][unit]['last_error'])
                if users[user][unit]['order']: users[user][unit]['order'].release_lock()
                break
        newmissing = missing - efficiency[user][0]
        newrejects = rejects - efficiency[user][1]
        logger.info("%s: balance: %.8f exchange: %s rejects: %d missing: %d efficiency: %.2f%% units: %s" % (user,
          stats['balance'], stats['name'], newrejects, newmissing, 100 * (1.0 - (newmissing + newrejects) / float(len(stats['units']) * passed)), units))
        efficiency[user][0] = missing
        efficiency[user][1] = rejects
    time.sleep(max(60 - time.time() + curtime, 0))
except KeyboardInterrupt: pass
except Exception as e:
  logger.error('exception caught: %s', str(e))
  pass

for user in users:
  for unit in users[user]:
    if users[user][unit]['order']:
      users[user][unit]['order'].shutdown()