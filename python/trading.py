import os
import sys
import time
import json
import urllib
import urllib2
import threading
import subprocess
import logging
import tempfile
import signal
from math import ceil
from utils import *


class NuBot(ConnectionThread):
  def __init__(self, conn, requester, key, secret, exchange, unit, logger = None):
    super(NuBot, self).__init__(conn, logger)
    self.requester = requester
    self.process = None
    self.unit = unit
    self.running = False
    self.exchange = exchange
    self.options = {
      'exchangename' : repr(exchange),
      'apikey' : key,
      'apisecret' : secret,
      'txfee' : 0.2,
      'pair' : 'nbt_' + unit,
      'submit-liquidity' : False,
      'dualside' : True,
      'multiple-custodians' : True,
      'executeorders' : True,
      'mail-notifications' : False,
      'hipchat' : False
    }
    if unit != 'usd':
      if unit == 'btc':
        self.options['secondary-peg-options'] = { 
        'main-feed' : 'bitfinex',
        'backup-feeds' : {  
          'backup1' : { 'name' : 'blockchain' },
          'backup2' : { 'name' : 'coinbase' },
          'backup3' : { 'name' : 'bitstamp' }
        } }
      else:
        self.logger.error('no price feed available for %s', unit)
      self.options['secondary-peg-options']['wallshift-threshold'] = 0.3
      self.options['secondary-peg-options']['spread'] = 0.0

  def run(self):
    out = tempfile.NamedTemporaryFile(delete = False)
    out.write(json.dumps({ 'options' : self.options }))
    out.close()
    while self.active:
      if self.requester.errorflag:
        self.shutdown()
      elif not self.process:
        with open(os.devnull, 'w') as fp:
          self.logger.info("starting NuBot for %s on %s", self.unit, repr(self.exchange))
          self.process = subprocess.Popen("java -jar NuBot.jar %s" % out.name,
            stdout=fp, stderr=fp, shell=True, cwd = 'nubot')
      time.sleep(10)
    self.shutdown()

  def shutdown(self):
    if self.process:
      self.logger.info("stopping NuBot for unit %s on %s", self.unit, repr(self.exchange))
      self.process.terminate()
      #os.killpg(self.process.pid, signal.SIGTERM)
      self.process = None


class PyBot(ConnectionThread):
  def __init__(self, conn, requester, key, secret, exchange, unit, logger = None):
    super(PyBot, self).__init__(conn, logger)
    self.requester = requester
    self.key = key
    self.secret = secret
    self.exchange = exchange
    self.unit = unit
    self.orders = []
    self.limit = { 'bid' : self.requester.interest()['bid']['target'], 'ask' : self.requester.interest()['ask']['target'] }
    if not hasattr(PyBot, 'lock'):
      PyBot.lock = {}
    if not repr(exchange) in PyBot.lock:
      PyBot.lock[repr(exchange)] = threading.Lock()
    if not hasattr(PyBot, 'pricefeed'):
      PyBot.pricefeed = PriceFeed(30, logger)

  def cancel_orders(self, side = 'all', reset = True):
    try:
      response = self.exchange.cancel_orders(self.unit, side, self.key, self.secret)
    except:
      response = {'error' : 'exception caught: %s' % sys.exc_info()[1]}
    if 'error' in response:
      self.logger.error('unable to cancel %s orders for %s on %s: %s', side, self.unit, repr(self.exchange), response['error'])
      self.exchange.adjust(response['error'])
      self.logger.info('adjusting nonce of %s to %d', repr(self.exchange), self.exchange._shift)
    else:
      self.logger.info('successfully deleted %s orders for %s on %s', side, self.unit, repr(self.exchange))
      if reset:
        if side == 'all':
          self.limit = { 'bid' : self.requester.interest()['bid']['target'], 'ask' : self.requester.interest()['ask']['target'] }
        else:
          self.limit[side] = self.requester.interest()[side]['target']
    return response

  def shutdown(self):
    self.logger.info("stopping PyBot for %s on %s", self.unit, repr(self.exchange))
    trials = 0
    while trials < 10:
      response = self.cancel_orders(reset = False)
      if not 'error' in response: break
      trials = trials + 1

  def acquire_lock(self):
    PyBot.lock[repr(self.exchange)].acquire()

  def release_lock(self):
    PyBot.lock[repr(self.exchange)].release()

  def place(self, side, balance, price):
    try:
      response = self.exchange.place_order(self.unit, side, self.key, self.secret, balance, price)
    except KeyboardInterrupt: raise
    except: response = { 'error' : 'exception caught: %s' % sys.exc_info()[1] }
    if 'error' in response:
      self.logger.error('unable to place %s %s order of %.4f nbt at %.8f on %s: %s', side, self.unit, balance, price, repr(self.exchange), response['error'])
      self.exchange.adjust(response['error'])
    else:
      self.logger.info('successfully placed %s %s order of %.4f nbt at %.8f on %s', side, self.unit, balance, price, repr(self.exchange))
    return response

  def effective_interest(self, balance, orders, target, cost):
    mod = orders[:]
    for i in xrange(len(orders)):
      if mod[i]['id'] in self.orders:
        mod[i]['cost'] = cost
    mod.append({'amount' : balance, 'cost' : cost, 'id' : sys.maxint})
    mod.sort(key = lambda x: (x['cost'], x['id']))
    total = 0.0
    mass = 0.0
    for order in mod:
      if order['id'] in self.orders or order['id'] == sys.maxint:
        mass += max(min(order['amount'], target - total), 0.0)
      total += order['amount']
    return mass * cost

  def balance(self, exunit, price):
    try:
      response = self.exchange.get_balance(exunit, self.key, self.secret)
      if not 'error' in response:
        response['balance'] = response['balance'] if exunit == 'nbt' else response['balance'] / price
        response['balance'] = int(response['balance'] * 10**3) / float(10**3)
    except KeyboardInterrupt: raise
    except: response = { 'error' : 'exception caught: %s' % sys.exc_info()[1] }
    return response

  def place(self, side):
    price = self.serverprice
    spread = max(self.exchange.fee, 0.002)
    if side == 'ask':
      exunit = 'nbt'
      price *= (1.0 + spread)
    else:
      exunit = self.unit
      price *= (1.0 - spread)
    price = ceil(price * 10**8) / float(10**8) # truncate floating point precision after 8th position
    response = self.balance(exunit, price)
    if 'error' in response:
      self.logger.error('unable to receive balance for %s on %s: %s', exunit, repr(self.exchange), response['error'])
      self.exchange.adjust(response['error'])
      self.logger.info('adjusting nonce of %s to %d', repr(self.exchange), self.exchange._shift)
    elif response['balance'] > 0.1:
      amount = min(self.limit[side], response['balance'])
      if amount > 0.1:
        try:
          response = self.exchange.place_order(self.unit, side, self.key, self.secret, amount, price)
        except KeyboardInterrupt: raise
        except: response = { 'error' : 'exception caught: %s' % sys.exc_info()[1] }
        if 'error' in response:
          self.logger.error('unable to place %s %s order of %.4f nbt at %.8f on %s: %s',
            side, self.unit, amount, price, repr(self.exchange), response['error'])
          self.exchange.adjust(response['error'])
        else:
          self.logger.info('successfully placed %s %s order of %.4f nbt at %.8f on %s',
            side, self.unit, amount, price, repr(self.exchange))
          self.orders.append(response['id'])
          self.limit[side] -= amount
    return response

  def run(self):
    self.logger.info("starting PyBot for %s on %s", self.unit, repr(self.exchange))
    self.serverprice = self.conn.get('price/' + self.unit, trials = 3, timeout = 15)['price']
    trials = 0
    while trials < 10:
      response = self.cancel_orders(reset = False)
      if not 'error' in response: break
      trials = trials + 1
    self.place('bid')
    self.place('ask')
    prevprice = self.serverprice
    curtime = time.time()
    efftime = curtime
    lasttime = curtime
    lastdev = 1.0
    while self.active:
      try:
        time.sleep(max(1 - time.time() + curtime, 0))
        curtime = time.time()
        if curtime - lasttime < 30: continue
        lasttime = curtime
        if self.requester.errorflag:
          self.logger.error('server unresponsive for %s', repr(self.exchange))
          self.shutdown()
          efftime = curtime
        else:
          response = self.conn.get('price/' + self.unit, trials = 3, timeout = 15)
          if not 'error' in response:
            self.serverprice = response['price']
            userprice = PyBot.pricefeed.price(self.unit)
            if 1.0 - min(self.serverprice, userprice) / max(self.serverprice, userprice) > 0.005: # validate server price
              self.logger.error('server price %.8f for unit %s deviates too much from price %.8f received from ticker, will try to delete orders on %s',
                self.serverprice, self.unit, userprice, repr(self.exchange))
              self.shutdown()
              efftime = curtime
            else:
              deviation = 1.0 - min(prevprice, self.serverprice) / max(prevprice, self.serverprice)
              if deviation > 0.00375:
                self.logger.info('price of %s moved from %.8f to %.8f, will try to delete orders on %s', self.unit, prevprice, self.serverprice, repr(self.exchange))
                prevprice = self.serverprice
                self.cancel_orders()
                efftime = curtime
              elif curtime - efftime > 120:
                efftime = curtime
                response = self.conn.get(self.key, trials = 1)
                if 'error' in response:
                  self.logger.error('unable to receive statistics for user %s: %s', self.key, response['message'])
                else:
                  for side in [ 'bid', 'ask' ]:
                    effective_rate = 0.0
                    total = 0.0
                    effective_rate = float(sum([ o['amount'] * o['cost'] for o in response['units'][self.unit][side] ]))
                    total = float(sum([ o['amount'] for o in response['units'][self.unit][side] ]))
                    if total == 0:
                      self.limit[side] = self.requester.interest()[side]['target']
                    else:
                      effective_rate /= total
                      deviation = 1.0 - min(effective_rate, self.requester.cost[side]) / max(effective_rate, self.requester.cost[side])
                      if deviation > 0.02 and lastdev > 0.02:
                        if self.limit[side] >= 0.5 and effective_rate < self.requester.cost[side]:
                          funds = max(0.5, total * (1.0 - max(deviation, 0.1)))
                          self.logger.info("decreasing tier 1 %s limit of %s on %s from %.8f to %.8f", side, self.unit, repr(self.exchange), total, funds)
                          self.cancel_orders(side)
                          self.limit[side] = funds
                        elif self.limit[side] < total * deviation and effective_rate > self.requester.cost[side]:
                          self.logger.info("increasing tier 1 %s limit of %s on %s from %.8f to %.8f", side, self.unit, repr(self.exchange), total, total * (1.0 + deviation))
                          self.limit[side] = total * deviation
                      lastdev = deviation
              self.place('bid')
              self.place('ask')
          else:
            self.logger.error('unable to retrieve server price: %s', response['message'])
      except Exception as e:
        self.logger.error('exception caught in trading bot: %s', sys.exc_info()[1])
    time.sleep(1) # this is to ensure that the order book is updated
    self.shutdown()