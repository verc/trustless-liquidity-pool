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

import threading
import urllib2
import urllib
import json
import logging
import httplib
import thread
import socket
import time

nulllogger = logging.getLogger('null')
nulllogger.addHandler(logging.NullHandler())
nulllogger.propagate = False

class Connection():
  def __init__(self, server, logger = None):
    self.server = server
    self.logger = logger
    if not logger:
      self.logger = logging.getLogger('null')

  def json_request(self, request, method, params, headers, trials = None, timeout = 5):
    while True:
      curtime = time.time()
      connection = httplib.HTTPConnection(self.server, timeout = timeout)
      try:
        connection.request(request, method, urllib.urlencode(params), headers = headers)
        response = connection.getresponse()
        content = response.read()
        return json.loads(content)
      except httplib.BadStatusLine:
        msg = 'server could not be reached'
      except ValueError:
        msg = 'server response invalid'
      except socket.error, v:
        msg = 'socket error (%s)' % str(v[0])
        if str(v[0]) == 'timed out':
          timeout = min(timeout + 5, 30)
      except:
        msg = 'unknown connection error'
      if trials:
        if trials <= 1:
          self.logger.debug("%s: %s", method, msg)
          return { 'message' : msg, 'code' : -1, 'error' : True }
        trials = trials - 1
      self.logger.debug("%s: %s, retrying in 5 seconds with timeout %d...", method, msg, timeout)
      time.sleep(max(5.0 - time.time() + curtime, 0))

  def get(self, method, params = None, trials = None, timeout = 5):
    if not params: params = {}
    return self.json_request('GET', '/' + method, params, {}, trials, timeout)

  def post(self, method, params = None, trials = None, timeout = 5):
    if not params: params = {}
    headers = { "Content-type": "application/x-www-form-urlencoded" }
    return self.json_request('POST', '/' + method, params, headers, trials, timeout)


class ConnectionThread(threading.Thread):
  def __init__(self, conn, logger = None):
    threading.Thread.__init__(self)
    self.daemon = True
    self.active = True
    self.pause = False
    self.logger = logger
    self.logger = logger if logger else logging.getLogger('null')
    self.conn = conn

  def stop(self):
    self.active = False

  def acquire_lock(self): pass
  def release_lock(self): pass


class CheckpointThread(ConnectionThread):
  def __init__(self, host, logger = None):
    super(CheckpointThread, self).__init__(Connection(host, logger), logger)
    self.users = []
    self.lock = threading.Lock()
    self.trigger = threading.Lock()
    self.trigger.acquire()
    self.checkpoint = { 'error' : 'no checkpoint received' }
    self.start()

  def collect(self, timeout):
    self.timeout = timeout
    try: self.trigger.release()
    except thread.error: pass

  def finish(self):
    try:
      self.lock.acquire()
      self.lock.release()
    except KeyboardInterrupt:
      raise
    return self.checkpoint

  def register(self, address, key, name):
    self.users.append(key)
    self.conn.post('register', { 'address' : address, 'key' : key, 'name' : name }, trials = 3, timeout = 10)

  def run(self):
    while self.active:
      self.trigger.acquire()
      self.lock.acquire()
      starttime = time.time()
      while time.time() < starttime + self.timeout > 0:
        self.checkpoint = self.conn.post('checkpoints', { u : 1 for u in self.users }, trials = 1, timeout = 1)
        if 'error' in self.checkpoint:
          time.sleep(0.1)
          self.logger.error('unable to retrieve checkpoint from %s: %s', self.conn.server, self.checkpoint['error'])
        else:
          break
      self.lock.release()


class PriceFeed():
  def __init__(self, update_interval, logger):
    self.update_interval = update_interval
    self.feed = { x : [0, threading.Lock(), 0.0] for x in [ 'btc', 'eur' ] }
    self.logger = logger if logger else logging.getLogger('null')

  def price(self, unit, force = False):
    if unit == 'usd' or unit == 'nbt': return 1.0 #AlwaysADollar
    if not unit in self.feed: return None
    self.feed[unit][1].acquire()
    curtime = time.time()
    if force or curtime - self.feed[unit][0] > self.update_interval:
      self.feed[unit][0] = curtime
      #self.feed[unit][2] = None
      if unit == 'btc': 
        try: # bitfinex
          ret = json.loads(urllib2.urlopen(urllib2.Request('https://api.bitfinex.com/v1//pubticker/btcusd'), timeout = 3).read())
          self.feed['btc'][2] = 1.0 / float(ret['mid'])
        except:
          self.logger.warning("unable to update BTC price from bitfinex")
          try: # coinbase
            ret = json.loads(urllib2.urlopen(urllib2.Request('https://coinbase.com/api/v1/prices/spot_rate?currency=USD'), timeout = 3).read())
            self.feed['btc'][2] = 1.0 / float(ret['amount'])
          except:
            self.logger.warning("unable to update BTC price from coinbase")
            try: # bitstamp
              ret = json.loads(urllib2.urlopen(urllib2.Request('https://www.bitstamp.net/api/ticker/'), timeout = 3).read())
              self.feed['btc'][2] = 2.0 / (float(ret['ask']) + float(ret['bid']))
            except:
              self.logger.error("unable to update price for BTC")
      elif unit == 'eur':
        try: # yahoo
          ret = json.loads(urllib2.urlopen(urllib2.Request('http://finance.yahoo.com/webservice/v1/symbols/allcurrencies/quote?format=json'), timeout = 3).read())
          for res in ret['list']['resources']:
            if res['resource']['fields']['name'] == 'USD/EUR':
              self.feed['eur'][2] = float(res['resource']['fields']['price'])
        except:
          self.logger.warning("unable to update EUR price from yahoo")
          try: # bitstamp
            ret = json.loads(urllib2.urlopen(urllib2.Request('https://www.bitstamp.net/api/eur_usd/'), timeout = 3).read())
            self.feed['eur'][2] = 2.0 / (float(ret['sell']) + float(ret['buy']))
          except:
            self.logger.error("unable to update price for EUR")
    self.feed[unit][1].release()
    return self.feed[unit][2]