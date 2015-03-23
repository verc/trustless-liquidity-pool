import threading
import urllib2
import urllib
import json
import logging
import httplib
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
          self.logger.error("%s: %s", method, msg)
          return { 'message' : msg, 'code' : -1, 'error' : True }
        trials = trials - 1
      self.logger.error("%s: %s, retrying in 5 seconds with timeout %d...", method, msg, timeout)
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
    self.runlock = threading.Lock()
    self.runlock.acquire()
    self.daemon = True
    self.active = True
    self.pause = False
    self.logger = logger
    self.logger = logger if logger else logging.getLogger('null')
    self.conn = conn

  def release(self):
    self.runlock.release()

  def terminate(self):
    self.stop()
    self.runlock.acquire()

  def stop(self):
    self.active = False

  def acquire_lock(self): pass
  def release_lock(self): pass


class PriceFeed():
  def __init__(self, update_interval, logger):
    self.update_interval = update_interval
    self.feed = { 'btc' : [0, threading.Lock(), 0.0] }
    self.logger = logger if logger else logging.getLogger('null')

  def price(self, unit, force = False):
    if not unit in self.feed: return None
    self.feed[unit][1].acquire()
    curtime = time.time()
    if force or curtime - self.feed[unit][0] > self.update_interval:
      self.feed[unit][0] = curtime
      self.feed[unit][2] = None
      if unit == 'btc': 
        try: # bitfinex
          ret = json.loads(urllib2.urlopen(urllib2.Request('https://api.bitfinex.com/v1//pubticker/btcusd'), timeout = 1).read())
          self.feed['btc'][2] = 1.0 / float(ret['mid'])
        except:
          self.logger.warning("unable to update BTC price from bitfinex")
          try: # coinbase
            ret = json.loads(urllib2.urlopen(urllib2.Request('https://coinbase.com/api/v1/prices/spot_rate?currency=USD'), timeout = 1).read())
            self.feed['btc'][2] = 1.0 / float(ret['amount'])
          except:
            self.logger.warning("unable to update BTC price from coinbase")
            try: # bitstamp
              ret = json.loads(urllib2.urlopen(urllib2.Request('https://www.bitstamp.net/api/ticker/'), timeout = 1).read())
              self.feed['btc'][2] = 2.0 / (float(ret['ask']) + float(ret['bid']))
            except:
              self.logger.error("unable to update price for BTC")
    self.feed[unit][1].release()
    return self.feed[unit][2]