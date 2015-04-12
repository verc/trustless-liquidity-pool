#! /usr/bin/env python
"""
The MIT License (MIT)
Copyright (c) 2015 creon (creon.nu@gmail.com)

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
OR OTHER DEALINGS IN THE SOFTWARE.
"""

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

_wrappers = { 'bittrex' : Bittrex() } #, 'poloniex' : Poloniex(), 'ccedk' : CCEDK(), 'bitcoincoid' : BitcoinCoId(), 'bter' : BTER(), 'testing' : Peatio() }

_mainlogger = None
def getlogger():
  global _mainlogger
  if not _mainlogger: # initialize logger
    if not os.path.isdir('logs'):
      os.makedirs('logs')
    _mainlogger = logging.getLogger('Client')
    _mainlogger.setLevel(logging.DEBUG)
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
    _mainlogger.addHandler(sh)
    _mainlogger.addHandler(fh)
    _mainlogger.addHandler(ch)
  return _mainlogger


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
    response = self.conn.post('register', { 'address' : self.address, 'key' : self.key, 'name' : repr(self.exchange) }, trials = 3, timeout = 10)
    if response['code'] == 0: # reset sampling in case of server restart
      self.sampling = self.initsampling
    return response

  def submit(self):
    data, sign = self.exchange.create_request(self.unit, self.key, self.secret)
    params = { 'unit' : self.unit, 'user' : self.key, 'sign' : sign }
    params.update(data)
    params.update(self.cost)
    curtime = time.time()
    ret = self.conn.post('liquidity', params, trials = 1, timeout = 60 / self.sampling)
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


# actual client class which contains several (key,unit) pairs
class Client(ConnectionThread):
  def __init__(self, server, logger = None):
    self.logger = getlogger() if not logger else logger
    self.conn = Connection(server, logger)
    super(Client, self).__init__(self.conn, self.logger)
    self.basestatus = self.conn.get('status')
    self.exchangeinfo = self.conn.get('exchanges')
    self.sampling = min(240, 3 * self.basestatus['sampling'] / 2)
    self.users = {}
    self.lock = threading.Lock()

  def set(self, key, secret, address, name, unit, bid = None, ask = None, bot = 'pybot', ordermatch = False):
    if not name in self.exchangeinfo or not unit in self.exchangeinfo[name]:
      return False
    key = str(key)
    secret = str(secret)
    exchange = _wrappers[name]
    cost = { 'bid' : bid if bid else self.exchangeinfo[name][unit]['bid']['rate'],
             'ask' : ask if ask else self.exchangeinfo[name][unit]['ask']['rate'] }
    self.lock.acquire()
    if not key in self.users:
      self.users[key] = {}
    if unit in self.users[key]:
      self.shutdown(key, unit)
    self.users[key][unit] = { 'request' : RequestThread(self.conn, key, secret, exchange, unit, address, self.sampling, cost, self.logger) }
    self.users[key][unit]['request'].start()
    target = { 'bid': self.exchangeinfo[name][unit]['bid']['target'], 'ask': self.exchangeinfo[name][unit]['ask']['target'] }
    if not bot or bot == 'none':
      self.users[key][unit]['order'] = None
    elif bot == 'nubot':
      self.users[key][unit]['order'] = NuBot(self.conn, self.users[key][unit]['request'], key, secret, exchange, unit, target, self.logger, ordermatch)
    elif bot == 'pybot':
      self.users[key][unit]['order'] = PyBot(self.conn, self.users[key][unit]['request'], key, secret, exchange, unit, target, self.logger, ordermatch)
    else:
      self.logger.error("unknown order handler: %s", bot)
      self.users[key][unit]['order'] = None
    if self.users[key][unit]['order']:
      if self.users[key][unit]['order']:
        self.users[key][unit]['order'].start()
    self.lock.release()
    return True

  def shutdown(self, key = None, unit = None, join = True):
    if key == None:
      for key in self.users:
        self.shutdown(key, unit, False)
      if join:
        for key in self.users:
          self.shutdown(key, unit, True)
    elif unit == None:
      for unit in self.users[key]:
        self.shutdown(key, unit, False)
      if join:
        for unit in self.users[key]:
          self.shutdown(key, unit, True)
    else:
      while True:
        try:
          self.users[key][unit]['request'].stop()
          if self.users[key][unit]['order']:
            self.users[key][unit]['order'].stop()
          if join:
            self.users[key][unit]['request'].join()
            if self.users[key][unit]['order']:
              self.users[key][unit]['order'].join()
        except KeyboardInterrupt: continue
        break

  def run(self):
    starttime = time.time()
    curtime = time.time()
    efficiencies = []
    while self.active:
      sleep = 60 - time.time() + curtime
      while sleep > 0:
        step = min(sleep, 0.5)
        time.sleep(step)
        if not self.active: break
        sleep -= step
      if not self.active: break
      self.lock.acquire()
      try:
        time.sleep(max(60 - time.time() + curtime, 0))
        curtime = time.time()
        for user in self.users: # post some statistics
          response = self.conn.get(user, trials = 1)
          if 'error' in response:
            logger.error('unable to receive statistics for user %s: %s', user, response['message'])
            self.users[user].values()[0]['request'].register() # reassure to be registered
            newstatus = self.conn.get('status', trials = 3)
            if not 'error' in newstatus:
              basestatus = newstatus
              sampling = min(240, 3 * self.basestatus['sampling'] / 2)
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
            self.logger.info('%s - balance: %.8f rate: %.2f%% ppm: %.8f efficiency: %.2f%% rejects: %d missing: %d%s - %s', repr(self.users[user].values()[0]['request'].exchange),
              response['balance'], effective_rate * 100, effective_rate * total / float(60 * 24), response['efficiency'] * 100, response['rejects'], response['missing'], orderstring, user)
            if not efficiencies:
              efficiencies = [ response['efficiency'] for i in xrange(5) ]
            if curtime - starttime > 150:
              efficiencies = efficiencies[1:] + [response['efficiency']]
              if sorted(efficiencies)[2] < 0.95:
                for unit in response['units']:
                  if response['units'][unit]['rejects'] > 1 and response['units'][unit]['rejects'] / float(self.basestatus['sampling']) >= 0.05: # look for valid error and adjust nonce shift
                    if response['units'][unit]['last_error'] != "":
                      if 'deviates too much from current price' in response['units'][unit]['last_error']:
                        PyBot.pricefeed.price(unit, True) # force a price update
                        if self.users[user][unit]['order']: self.users[user][unit]['order'].shutdown()
                        self.logger.warning('price missmatch for %s on %s, forcing price update', unit, repr(self.users[user][unit]['request'].exchange))
                      else:
                        shift = self.users[user][unit]['request'].exchange._shift
                        self.users[user][unit]['request'].exchange.adjust(response['units'][unit]['last_error'])
                        if shift != self.users[user][unit]['request'].exchange._shift:
                          self.logger.warning('too many rejected requests for %s on %s, adjusting nonce shift to %d',
                            unit, repr(self.users[user][unit]['request'].exchange), self.users[user][unit]['request'].exchange._shift)
                    else:
                      if self.users[user][unit]['request'].sampling < 3 * sampling: # just send more requests
                        self.users[user][unit]['request'].sampling = self.users[user][unit]['request'].sampling + 1
                        self.logger.warning('increasing sampling to %d',
                          unit, repr(self.users[user][unit]['request'].exchange), self.users[user][unit]['request'].sampling)
                  if response['units'][unit]['missing'] / float(self.basestatus['sampling']) >= 0.05: # look for missing error and adjust sampling
                    if self.users[user][unit]['request'].sampling < 3 * self.sampling: # just send more requests
                      self.users[user][unit]['request'].sampling = self.users[user][unit]['request'].sampling + 1
                      self.logger.warning('too many missing requests for %s on %s, increasing sampling to %d',
                        unit, repr(self.users[user][unit]['request'].exchange), self.users[user][unit]['request'].sampling)
      except KeyboardInterrupt: break
      except Exception as e:
        self.logger.error('exception caught in main loop: %s', sys.exc_info()[1])
      self.lock.release()
    self.lock.acquire()
    logger.info('stopping trading bots, please allow the client up to 1 minute to terminate')
    self.shutdown()
    self.lock.release()


if __name__ == "__main__":
  logger = getlogger()
  userfile = 'pool.conf' if len(sys.argv) == 1 else sys.argv[1]
  if userfile == "-":
    userdata = [ line.strip().split('#')[0].split() for line in sys.stdin.readlines() if len(line.strip().split('#')[0].split()) >= 5 ]
  else:
    client = None
    try:
      userdata = [ line.strip().split('#')[0].split() for line in open(userfile).readlines() if len(line.strip().split('#')[0].split()) >= 5 ]
      if len(userdata) != 0: # try to interpret data as list of address unit exchange key secret  bid ask bot
        if len(sys.argv) == 1:
          logger.error('multi-key format in %s requires pool IP to be specified as second parameter to the client', userfile)
          sys.exit(1)
        client = Client(sys.argv[2])
        for user in userdata:
          key = user[3]
          secret = user[4]
          name = user[2].lower()
          if not name in _wrappers:
            logger.error("unknown exchange: %s", user[2])
            sys.exit(1)
          exchange = _wrappers[name]
          for unit in user[1].split(','):
            unit = unit.lower()
            if len(user) >= 6 and float(user[5]) != 0.0:
              bid = float(user[5]) / 100.0
              ask = float(user[5]) / 100.0
            if len(user) >= 7 and float(user[6]) != 0.0:
              ask = float(user[6]) / 100.0
            bot = 'pybot' if len(user) < 8 else user[7]
            ordermatch = False if len(user) < 9 else (user[8] == 'match')
            if not client.set(key, secret, user[0], name, unit, bid, ask, bot):
              logger.error("%s on %s not supported by pool", unit, name)
              sys.exit(1)
      else:
        configdata = dict([ ( v.strip() for v in line.strip().split('#')[0].split('=')) for line in open(userfile).readlines() if len(line.strip().split('#')[0].split('=')) == 2 ])
        if len(configdata.keys()) > 0:
          if 'interest' in configdata:
            bid = float(configdata['interest'].split(',')[0]) / 100.0
            ask = bid
            if ',' in configdata['interest']:
              ask = float(configdata['interest'].split(',')[1]) / 100.0
          else:
            bid = None
            ask = None
          bot = 'pybot' if not 'trading' in configdata else configdata['trading']
          ordermatch = False if not 'ordermatch' in configdata else (configdata['ordermatch'] in ['True', 'true', '1'])
          if 'server' in configdata:
            if 'apikey' in configdata:
              if 'apisecret' in configdata:
                if 'address' in configdata:
                  if 'unit' in configdata:
                    if 'exchange' in configdata:
                      name = configdata['exchange'].lower()
                      if name in _wrappers:
                        client = Client(configdata['server'], logger)
                        client.set(configdata['apikey'], configdata['apisecret'], configdata['address'], name, configdata['unit'].lower(), bid, ask, bot, ordermatch)
                      else:
                        logger.error("unknown exchange: %s", user[2])
                    else:
                      logger.error('exchange information missing in %s', userfile)
                  else:
                    logger.error('unit information missing in %s', userfile)
                else:
                  logger.error('address missing in %s', userfile)
              else:
                logger.error('apisecret missing in %s', userfile)
            else:
              logger.error('apikey missing in %s', userfile)
          else:
            logger.error('server missing in %s', userfile)
        else:
          logger.error('no valid user information could be found')
    except:
      logger.error("%s could not be read", userfile)
    if not client: sys.exit(1)
    logger.debug('starting liquidity operation with sampling %d' % client.sampling)
    client.start()
    stop = False
    while True:
      try:
        if stop:
          client.stop()
          client.join()
          break
        time.sleep(60)
      except KeyboardInterrupt: stop = True