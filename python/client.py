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

userfile = 'users.dat' if len(sys.argv) == 2 else sys.argv[2]
if userfile == "-":
  userdata = [ line.strip().split() for line in sys.stdin.readlines() if len(line.strip().split('#')[0].split()) >= 5 ]
else:
  try:
    userdata = [ line.strip().split() for line in open(userfile).readlines() if len(line.strip().split('#')[0].split()) >= 5 ] # address units exchange key secret [trader]
  except:
    logger.error("%s could not be read", userfile)
    sys.exit(1)

_server = sys.argv[1]
_wrappers = { 'poloniex' : Poloniex(), 'ccedk' : CCEDK(), 'bitcoincoid' : BitcoinCoId(), 'bter' : BTER(), 'testing' : Peatio() }

# one request signer thread for each key and unit
class RequestThread(ConnectionThread):
  def __init__(self, conn, key, secret, exchange, unit, address, sampling, cost, logger = None):
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
    self.exchangeupdate = 0
    self.cost = cost.copy()

  def register(self):
    response = self.conn.post('register', { 'address' : self.address, 'key' : self.key, 'name' : repr(self.exchange) })
    if response['code'] == 0: # reset sampling in case of server restart
      self.sampling = self.initsampling
    return response

  def submit(self):
    data, sign = self.exchange.create_request(self.unit, self.key, self.secret)
    params = { 'unit' : self.unit, 'user' : self.key, 'sign' : sign }
    params.update(data)
    params.update(self.cost)
    curtime = time.time()
    ret = self.conn.post('liquidity', params, trials = 1, timeout = 10)
    if ret['code'] != 0:
      self.trials += time.time() - curtime + 60.0 / self.sampling
      self.logger.error("submit: %s" % ret['message'])
      if ret['code'] == 11: # user unknown, just register again
        self.register()
    else:
      self.trials = 0
    self.errorflag = self.trials >= 120 # notify that something is wrong after 2 minutes of failures

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
exchangeinfo = conn.get('exchanges')
sampling = min(240, 3 * basestatus['sampling'] / 2)

# parse user data
users = {}
for user in userdata:
  key = user[3]
  secret = user[4]
  name = user[2].lower()
  if not name in _wrappers:
    logger.error("unknown exchange: %s", user[2])
    sys.exit(2)
  if not name in exchangeinfo:
    logger.error("exchange not supported by pool: %s", name)
    sys.exit(2)
  units = [ unit.lower() for unit in user[1].split(',') ]
  exchange = _wrappers[name]
  users[key] = {}
  for unit in user[1].split(','):
    unit = unit.lower()
    if not unit in exchangeinfo[name]:
      logger.error("unit %s on %s not supported by pool", unit, name)
      logger.info("supported units on %s are: %s", name, " ".join(exchangeinfo[name]))
      sys.exit(2)
    cost = { 'bid' : exchangeinfo[name][unit]['bid']['rate'], 'ask' : exchangeinfo[name][unit]['ask']['rate'] }
    if len(user) >= 6 and float(user[5]) != 0.0:
      cost['bid'] = float(user[5]) / 100.0
      cost['ask'] = float(user[5]) / 100.0
    if len(user) >= 7 and float(user[6]) != 0.0:
      cost['ask'] = float(user[6]) / 100.0
    bot = 'pybot' if len(user) < 8 else user[7]
    users[key][unit] = { 'request' : RequestThread(conn, key, secret, exchange, unit, user[0], sampling, cost, logger) }
    users[key][unit]['request'].start()
    target = { 'bid': exchangeinfo[name][unit]['bid']['target'], 'ask': exchangeinfo[name][unit]['ask']['target'] }
    if bot == 'none':
      users[key][unit]['order'] = None
    elif bot == 'nubot':
      users[key][unit]['order'] = NuBot(conn, users[key][unit]['request'], key, secret, exchange, unit, target, logger)
    elif bot == 'pybot':
      users[key][unit]['order'] = PyBot(conn, users[key][unit]['request'], key, secret, exchange, unit, target, logger)
    else:
      logger.error("unknown order handler: %s", bot)
      users[key][unit]['order'] = None
    if users[key][unit]['order']:
      if users[key][unit]['order']:
        users[key][unit]['order'].start()

logger.debug('starting liquidity propagation with sampling %d' % sampling)
starttime = time.time()
curtime = time.time()
effs = []

while True: # print some info every minute until program terminates
  try:
    time.sleep(max(60 - time.time() + curtime, 0))
    curtime = time.time()
    for user in users: # post some statistics
      response = conn.get(user, trials = 1)
      if 'error' in response:
        logger.error('unable to receive statistics for user %s: %s', user, response['message'])
        users[user].values()[0]['request'].register() # reassure to be registered if 
        newstatus = conn.get('status', trials = 3)
        if not 'error' in newstatus:
          basestatus = newstatus
          sampling = min(240, 3 * basestatus['sampling'] / 2)
      else:
        # collect user information
        effective_rate = 0.0
        total = 0.0
        for unit in response['units']:
          for side in [ 'bid', 'ask' ]:
            effective_rate += float(sum([ o['amount'] * o['cost'] for o in response['units'][unit][side] ]))
            total += float(sum([ o['amount'] for o in response['units'][unit][side] ]))
        if total > 0.0: effective_rate /= total
        orderstring = ""
        for unit in response['units']:
          unitstring = ""
          for side in ['bid', 'ask']:
            market = response['units'][unit][side]
            coststring = ""
            for order in response['units'][unit][side]:
              if order['amount'] > 0:
                coststring += " %.4f x %.2f%%," % (order['amount'], order['cost'] * 100.0)
            if len(coststring):
              unitstring += " - %s:%s" % (side, coststring[:-1])
          if len(unitstring):
            orderstring += " - %s%s" % (unit, unitstring)
        # print user information
        logger.info('%s - balance: %.8f rate: %.2f%% ppm: %.8f efficiency: %.2f%% rejects: %d missing: %d%s - %s', repr(users[user].values()[0]['request'].exchange),
          response['balance'], effective_rate * 100, effective_rate * total / float(60 * 24), response['efficiency'] * 100, response['rejects'], response['missing'], orderstring, user)
        if not effs:
          effs = [ response['efficiency'] for i in xrange(5) ]
        if curtime - starttime > 90:
          effs = effs[1:] + [response['efficiency']]
          if sorted(effs)[2] < 0.95:
            for unit in response['units']:
              if response['units'][unit]['rejects'] / float(basestatus['sampling']) >= 0.025: # look for valid error and adjust nonce shift
                if response['units'][unit]['last_error'] != "":
                  if 'deviates too much from current price' in response['units'][unit]['last_error']:
                    PyBot.pricefeed.price(unit, True) # force a price update
                    if users[user][unit]['order']: users[user][unit]['order'].shutdown()
                    logger.warning('price missmatch for %s on %s, forcing price update', unit, repr(users[user][unit]['request'].exchange))
                  else:
                    shift = users[user][unit]['request'].exchange._shift
                    users[user][unit]['request'].exchange.adjust(response['units'][unit]['last_error'])
                    if shift != users[user][unit]['request'].exchange._shift:
                      logger.warning('too many rejected requests for %s on %s, adjusting nonce shift to %d',
                        unit, repr(users[user][unit]['request'].exchange), users[user][unit]['request'].exchange._shift)
                else:
                  if users[user][unit]['request'].sampling < 2 * sampling: # just send more requests
                    users[user][unit]['request'].sampling = users[user][unit]['request'].sampling + 1
                    logger.warning('increasing sampling to %d',
                      unit, repr(users[user][unit]['request'].exchange), users[user][unit]['request'].sampling)
              if response['units'][unit]['missing'] / float(basestatus['sampling']) >= 0.025: # look for missing error and adjust sampling
                if users[user][unit]['request'].sampling < 2 * sampling: # just send more requests
                  users[user][unit]['request'].sampling = users[user][unit]['request'].sampling + 1
                  logger.warning('too many missing requests for %s on %s, increasing sampling to %d',
                    unit, repr(users[user][unit]['request'].exchange), users[user][unit]['request'].sampling)
                else: # just wait a little bit
                  logger.warning('too many missing requests, sleeping a short while to synchronize')
                  curtime += 0.7

  except KeyboardInterrupt: break
  except Exception as e:
    logger.error('exception caught in main loop: %s', sys.exc_info()[1])

logger.info('stopping trading bots, please allow the client up to 1 minute to terminate')
while True:
  try:
    for user in users:
      for unit in users[user]:
        users[user][unit]['request'].stop()
        if users[user][unit]['order']:
          users[user][unit]['order'].stop()
    for user in users:
      for unit in users[user]:
        users[user][unit]['request'].join()
        if users[user][unit]['order']:
          users[user][unit]['order'].join()
  except KeyboardInterrupt: continue
  break