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
          self.logger.info("starting NuBot for unit %s on exchange %s", self.unit, repr(self.exchange))
          self.process = subprocess.Popen("java -jar NuBot.jar %s" % out.name,
            stdout=fp, stderr=fp, shell=True, cwd = 'nubot')
      time.sleep(10)

  def shutdown(self):
    if self.process:
      self.logger.info("stopping NuBot for unit %s on exchange %s", self.unit, repr(self.exchange))
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
      self.logger.error('unable to cancel %s orders for unit %s on exchange %s: %s', side, self.unit, repr(self.exchange), response['error'])
      self.exchange.adjust(response['error'])
      self.logger.info('adjusting nonce of exchange %s to %d', repr(self.exchange), self.exchange._shift)
    else:
      self.logger.info('successfully deleted %s orders for unit %s on exchange %s', side, self.unit, repr(self.exchange))
      if reset:
        if side == 'all' or side == 'bid':
          self.limit['bid'] = self.requester.interest()['bid']['target']
        if side == 'all' or side == 'ask':
          self.limit['ask'] = self.requester.interest()['ask']['target']
    return response

  def shutdown(self):
    self.logger.info("stopping PyBot for unit %s on exchange %s", self.unit, repr(self.exchange))
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
      self.logger.error('unable to place %s %s order of %.4f nbt at %.8f on exchange %s: %s', side, self.unit, balance, price, repr(self.exchange), response['error'])
      self.exchange.adjust(response['error'])
    else:
      self.logger.info('successfully placed %s %s order of %.4f nbt at %.8f on exchange %s', side, self.unit, balance, price, repr(self.exchange))
    return response

  def effective_interest(self, orders, target, cost):
    mod = orders[:]
    for i in xrange(len(mod)):
      if mod[i]['id'] in self.orders:
        mod[i]['cost'] = cost
    mod.sort(key = lambda x: x['cost'])
    total = 0.0
    mass = 0.0
    for order in mod:
      if order['id'] in self.orders:
        mass += max(min(order['amount'], target - total), 0.0)
      total += order['amount']
    return mass * cost

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
    try:
      response = self.exchange.get_balance(exunit, self.key, self.secret)
      if not 'error' in response:
        response['balance'] = response['balance'] if exunit == 'nbt' else response['balance'] / price
        response['balance'] = int(response['balance'] * 10**3) / float(10**3)
    except KeyboardInterrupt: raise
    except: response = { 'error' : 'exception caught: %s' % sys.exc_info()[1] }
    if 'error' in response:
      self.logger.error('unable to receive balance for unit %s on exchange %s: %s', exunit, repr(self.exchange), response['error'])
      self.exchange.adjust(response['error'])
    elif response['balance'] > 0.1:
      #balance = response['balance'] if exunit == 'nbt' else response['balance'] / price
      amount = min(self.limit[side], response['balance'])
      if amount > 0.1:
        try:
          response = self.exchange.place_order(self.unit, side, self.key, self.secret, amount, price)
        except KeyboardInterrupt: raise
        except: response = { 'error' : 'exception caught: %s' % sys.exc_info()[1] }
        if 'error' in response:
          self.logger.error('unable to place %s %s order of %.4f nbt at %.8f on exchange %s: %s',
            side, exunit, amount, price, repr(self.exchange), response['error'])
          self.exchange.adjust(response['error'])
        else:
          self.logger.info('successfully placed %s %s order of %.4f nbt at %.8f on exchange %s',
            side, exunit, amount, price, repr(self.exchange))
          self.orders.append(response['id'])
          self.limit[side] -= amount
      #else:
      #  self.logger.error('not placing %s %s order of %.4f nbt at %.8f on exchange %s: target limit reached',
      #      side, exunit, response['balance'], price, repr(self.exchange))
    return response

  def run(self):
    self.logger.info("starting PyBot for unit %s on exchange %s", self.unit, repr(self.exchange))
    self.serverprice = self.conn.get('price/' + self.unit, timeout = 30)['price']
    self.cancel_orders()
    self.place('bid')
    self.place('ask')
    prevprice = self.serverprice
    curtime = time.time()
    efftime = time.time()
    while self.active:
      time.sleep(max(30 - time.time() + curtime, 0))
      curtime = time.time()
      if self.requester.errorflag:
        self.shutdown()
      else:
        response = self.conn.get('price/' + self.unit, trials = 3, timeout = 15)
        if not 'error' in response:
          self.serverprice = response['price']
          userprice = PyBot.pricefeed.price(self.unit)
          if 1.0 - min(self.serverprice, userprice) / max(self.serverprice, userprice) > 0.005: # validate server price
            self.logger.error('server price %.8f for unit %s deviates too much from price %.8f received from ticker, will delete all orders for this unit', self.serverprice, self.unit, userprice)
            self.shutdown()
          else:
            deviation = 1.0 - min(prevprice, self.serverprice) / max(prevprice, self.serverprice)
            if deviation > 0.00425:
              self.logger.info('price of unit %s moved from %.8f to %.8f, will try to reset orders', self.unit, prevprice, self.serverprice)
              prevprice = self.serverprice
              self.cancel_orders()
            elif curtime - efftime >= 30:
              efftime = curtime
              for side in [ 'bid', 'ask' ]:
                info = self.requester.interest()[side]
                weight = sum([order['amount'] for order in info['orders'] if order['id'] in self.orders])
                mass = sum([order['amount'] for order in info['orders']])
                if self.limit[side] < self.requester.interest()[side]['target'] - mass:
                  self.limit[side] = self.requester.interest()[side]['target'] - mass
                  self.logger.info('reseting %s limit to %.4f for unit %s on exchange %s', side, self.limit[side], self.unit, repr(self.exchange))
                if weight > 0:
                  # get balance and use it when calculating effective interest
                  cureff = self.effective_interest(info['orders'], info['target'], self.requester.cost[side])
                  besteff = cureff
                  bestcost = self.requester.cost[side]
                  for candidate in [ order['cost'] - 0.001 for order in info['orders'] if order['id'] not in self.orders and order['cost'] - 0.001 >= self.requester.maxcost[side]]:
                    eff = self.effective_interest(info['orders'], info['target'], candidate)
                    if eff > besteff:
                      besteff = eff
                      bestcost = candidate
                  #print "<<<<<<<<<<<<<<<", side, "cureff:", cureff, "curcost:", self.requester.cost, 'besteff:', besteff, 'bestcost:', bestcost, 'limit:', self.limit[side], 'weight:', besteff / bestcost
                  effmass = besteff / bestcost
                  if self.requester.cost[side] != bestcost:
                    self.logger.info('reducing %s interest rate to %.2f%% for unit %s on exchange %s to increase effective interest from %.4f%% to %.4f%%',
                      side, bestcost * 100.0, self.unit, repr(self.exchange), cureff / self.requester.cost[side], effmass)
                    self.requester.cost[side] = bestcost
                  elif effmass < weight: # remove balance with 0% interest
                    self.logger.info('reducing %s limit to %.4f for unit %s on exchange %s', side, effmass, self.unit, repr(self.exchange))
                    self.cancel_orders(side)
                    self.limit[side] = effmass
            self.place('bid')
            self.place('ask')
        else:
          self.logger.error('unable to retrieve server price: %s', response['message'])
    self.shutdown()