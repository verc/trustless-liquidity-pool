import os
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
  def __init__(self, conn, key, secret, exchange, unit, logger = None):
    super(NuBot, self).__init__(conn, logger)
    self.process = None
    self.unit = unit
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
    self.logger.info("starting NuBot for unit %s on exchange %s", self.unit, repr(self.exchange))
    out = tempfile.NamedTemporaryFile(delete = False)
    out.write(json.dumps({ 'options' : self.options }))
    out.close()
    with open(os.devnull, 'w') as fp:
      self.process = subprocess.Popen("java -jar NuBot.jar %s" % out.name,
        stdout=fp, stderr=fp, shell=True, preexec_fn=os.setsid, cwd = 'nubot')

  def shutdown(self):
    if self.process:
      self.logger.info("stopping NuBot for unit %s on exchange %s", self.unit, repr(self.exchange))
      os.killpg(self.process.pid, signal.SIGTERM)


class PyBot(ConnectionThread):
  def __init__(self, conn, key, secret, exchange, unit, logger = None):
    super(PyBot, self).__init__(conn, logger)
    self.key = key
    self.secret = secret
    self.exchange = exchange
    self.unit = unit
    self.spread = 0.002
    self.interest = [0, {}]
    if not hasattr(PyBot, 'lock'):
      PyBot.lock = {}
    if not repr(exchange) in PyBot.lock:
      PyBot.lock[repr(exchange)] = threading.Lock()
    if not hasattr(PyBot, 'price'):
      PyBot.price = [0, {}, {}]
    if not hasattr(PyBot, 'interest'):
      PyBot.interest = [0, {}]

  def shutdown(self):
    self.logger.info("stopping PyBot for unit %s on exchange %s", self.unit, repr(self.exchange))
    trials = 0
    while trials < 10:
      try:
        response = self.exchange.cancel_orders(self.unit, self.key, self.secret)
      except:
        response = {'error' : 'exception caught'}
      if 'error' in response:
        self.logger.error('unable to cancel orders for unit %s on exchange %s (trial %d): %s', self.unit, repr(self.exchange), trials + 1, response['error'])
      else:
        self.logger.info('successfully deleted all orders for unit %s on exchange %s', self.unit, repr(self.exchange))
        break
      trials = trials + 1

  def acquire_lock(self):
    PyBot.lock[repr(self.exchange)].acquire()

  def release_lock(self):
    PyBot.lock[repr(self.exchange)].release()

  def update_price(self):
    curtime = time.time()
    if curtime - PyBot.price[0] > 30:
      try: # bitfinex
        ret = json.loads(urllib2.urlopen(urllib2.Request('https://api.bitfinex.com/v1//pubticker/btcusd')).read())
        PyBot.price[1]['btc'] = 1.0 / float(ret['mid'])
      except:
        try: # coinbase
          ret = json.loads(urllib2.urlopen(urllib2.Request('https://coinbase.com/api/v1/prices/spot_rate?currency=USD')).read())
          PyBot.price[1]['btc'] = 1.0 / float(ret['amount'])
        except:
          try: # bitstamp
            ret = json.loads(urllib2.urlopen(urllib2.Request('https://www.bitstamp.net/api/ticker/')).read())
            PyBot.price[1]['btc'] = 2.0 / (float(ret['ask']) + float(ret['bid']))
          except:
            self.logger.error("unable to update price for BTC")
      PyBot.price[2] = self.conn.get('price')
      PyBot.price[0] = curtime

  def update_interest(self):
    curtime = time.time()
    if curtime - PyBot.interest[0] > 120:
      PyBot.interest[1] = self.conn.get('exchanges')
      PyBot.interest[0] = curtime

  def place(self, side):
    price = PyBot.price[2][self.unit]
    if side == 'ask':
      exunit = 'nbt'
      price *= (1.0 + self.spread)
    else:
      exunit = self.unit
      price *= (1.0 - self.spread)
    price = ceil(price * 10**8) / float(10**8) # truncate floating point precision after 8th position
    try:
      response = self.exchange.get_balance(exunit, self.key, self.secret)
    except KeyboardInterrupt: raise
    except: response = { 'error' : 'exception caught' }
    if 'error' in response:
      self.logger.error('unable to receive balance for unit %s on exchange %s: %s', exunit, repr(self.exchange), response['error'])
      self.exchange.adjust(response['error'])
    elif response['balance'] > 0.0001:
      balance = response['balance'] if exunit == 'nbt' else response['balance'] / price
      self.update_interest()
      try:
        response = self.exchange.place_order(self.unit, side, self.key, self.secret, balance, price)
      except KeyboardInterrupt: raise
      except: response = { 'error' : 'exception caught' }
      if 'error' in response:
        self.logger.error('unable to place %s %s order iof %.4f nbt at %.8f on exchange %s: %s', side, exunit, balance, price, repr(self.exchange), response['error'])
        self.exchange.adjust(response['error'])
      else:
        self.logger.info('successfully placed %s %s order of %.4f nbt at %.8f on exchange %s', side, exunit, balance, price, repr(self.exchange))
    return response

  def reset(self, cancel = True):
    self.acquire_lock()
    response = { 'error' : True }
    while 'error' in response:
      response = {}
      if cancel:
        try: response = self.exchange.cancel_orders(self.unit, self.key, self.secret)
        except KeyboardInterrupt: raise
        except: response = { 'error' : 'exception caught' }
        if 'error' in response:
          self.logger.error('unable to cancel orders for unit %s on exchange %s: %s', self.unit, repr(self.exchange), response['error'])
        else:
          self.logger.info('successfully deleted all orders for unit %s on exchange %s', self.unit, repr(self.exchange))
      if not 'error' in response:
        response = self.place('bid')
        if not 'error' in response:
          response = self.place('ask')
      if 'error' in response:
        self.exchange.adjust(response['error'])
        self.logger.info('trying to adjust nonce of exchange %s to %d', repr(self.exchange), self.exchange._shift)
    self.release_lock()

  def run(self):
    self.logger.info("starting PyBot for unit %s on exchange %s", self.unit, repr(self.exchange))
    self.update_price()
    self.update_interest()
    self.reset() # initialize walls
    while self.active:
      curtime = time.time()
      prevprice = PyBot.price[2][self.unit]
      self.update_interest()
      self.update_price()
      userprice = PyBot.price[1][self.unit]
      newprice = PyBot.price[2][self.unit]
      if 1.0 - min(newprice, userprice) / max(newprice, userprice) > 0.02: # validate server price
        self.logger.error('server price %.8f for unit %s deviates too much from price %.8f received from ticker, will delete all orders for this unit', newprice, self.unit, userprice)
        try: response = self.exchange.cancel_orders(self.unit, self.key, self.secret)
        except KeyboardInterrupt: raise
        except: response = { 'error' : 'exception caught' }
      else:
        deviation = 1.0 - min(prevprice, newprice) / max(prevprice, newprice)
        if deviation > 0.02:
          self.logger.info('price of unit %s moved from %.8f to %.8f, will try to reset orders', unit, prevprice, newprice)
        self.reset(deviation > 0.02)
      time.sleep(max(30 - time.time() + curtime, 0))
    self.shutdown()