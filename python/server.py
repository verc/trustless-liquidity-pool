#! /usr/bin/env python

import SimpleHTTPServer
import BaseHTTPServer
import cgi
import urllib
import sys
import time
from math import log, exp
from thread import start_new_thread
from exchanges import *

_port = 2019
_markets = { 'poloniex' : { 'BTC' : { 'request' : None, 'bid' : [], 'ask' : [], 'nreqs' : 0 } } }
_interest = { 'poloniex' : { 'BTC' : { 'rate' : 0.02, 'target' : 100.0 } } }
_wrappers = { 'poloniex' : Poloniex() }
_tolerance = 100000
_sampling = 12

keys = {}
payout = {}
price = {'BTC' : 0.003666}

def calculate_interest(balance, amount, interest):
  return interest['rate'] * (amount - (log(exp(interest['target']) + exp(balance + amount)) - log(exp(interest['target']) + exp(balance))))

def register(params):
  if set(params.keys()) == set(['address', 'key']):
    user = params['key'][0]
    if not user in keys:
      keys[user] = _markets.copy()
      payout[user] = { 'address' : params['address'][0], 'balance' : 0.0 }
      print "new user %s: %s" % (user, payout[user])
    else:
      print >> sys.stderr, "user already exists:", user
  else:
    print >> sys.stderr, "invalid registration data received:", params

def liquidity(params):
  if set(params.keys() + ['name', 'unit', 'user', 'sign']) == set(params.keys()):
    name = params.pop('name')[0]
    if name in _markets.keys() and params['unit'][0] in _markets[name]:
      user = params.pop('user')[0]
      unit = params.pop('unit')[0]
      sign = params.pop('sign')[0]
      if user in keys:
        keys[user][name][unit]['request'] = ({ p : v[0] for p,v in params.items() }, sign)
        #print "liquidity received - user: %s exchange: %s unit: %s" % (user, name, unit)
      else:
        print >> sys.stderr, "user not found:", user
    else:
      print >> sys.stderr, "%s market not supported on %s" % (params['unit'], params['name'])
  else:
    print >> sys.stderr, "invalid liquidity data received:", params

class RequestHandler(SimpleHTTPServer.SimpleHTTPRequestHandler):
  def do_POST(self):
    if self.path in ['register', 'liquidity']:
      ctype, pdict = cgi.parse_header(self.headers.getheader('content-type'))
      if ctype == 'application/x-www-form-urlencoded':
        length = int(self.headers.getheader('content-length'))
        params = cgi.parse_qs(self.rfile.read(length), keep_blank_values = 1)
        if self.path == 'liquidity':
          liquidity(params)
        elif self.path == 'register':
          register(params)
      self.send_response(301)
      self.end_headers()

  def log_message(self, format, *args): pass

def update_price():
  price = {'BTC' : 0.003666}

def validate():
  liquidity = { 'bid' : 0.0, 'ask' : 0.0 }
  for user in keys:
    for name in keys[user]:
      for unit in keys[user][name]:
        valid = { 'bid': [], 'ask' : [] }
        if keys[user][name][unit]['request']:
          orders = _wrappers[name].validate_request(user, *keys[user][name][unit]['request'])
          if orders != None:
            for order in orders:
              if abs(order['price'] - price[unit]) < _tolerance:
                valid[order['type']].append((order['id'], order['amount']))
                print "liquidity validated - user: %s exchange: %s unit: %s" % (user, name, unit)
              else:
                print >> sys.stderr, "warning: order deviates too much from current price"
          else:
            print >> sys.stderr, "ERROR: unable to validate request:", keys[user][name][unit]['request']
        else:
            print >> sys.stderr, "WARNING: no request received from user", user
        for side in [ 'bid', 'ask' ]:
          keys[user][name][unit][side].append(valid[side])
          liquidity[side] += sum([ order[1] for order in valid[side]])
        keys[user][name][unit]['request'] = None
  return liquidity

def credit():
  for name in _interest:
    for unit in _interest[name]:
      for side in [ 'bid', 'ask' ]:
        for user in keys:
          if len(keys[user][name][unit][side]) < _sampling:
            keys[user][name][unit][side] = [ [] * (_sampling - len(keys[user][name][unit][side])) ] + keys[user][name][unit][side]
        for sample in xrange(_sampling):
          orders = []
          for user in keys:
            orders += [ (user, order) for order in keys[user][name][unit][side][sample] ]
          orders.sort(key = lambda x: x[1][0])
          balance = 0.0
          for user, order in orders:
            payout[user]['balance'] += calculate_interest(balance, order[1], _interest[name][unit]) / (_sampling * 60 * 24)
            balance += order[1]
        for user in keys:
          keys[user][name][unit][side] = []

  print "current payout:"
  for user in payout:
    print user, payout[user]['balance']

def pay():
  for user in payout:
    payout[user]['balance'] = 0

httpd = BaseHTTPServer.HTTPServer(("", _port), RequestHandler)
sa = httpd.socket.getsockname()
print "Serving HTTP on", sa[0], "port", sa[1], "..."
start_new_thread(httpd.serve_forever, ())

ts = 0
lq = []
while True:
  ts = (ts % 86400) + 5
  curtime = time.time()
  if ts % (60 / _sampling) == 0: lq.append(validate())
  if ts % 60 == 0:
    credit()
    print "submitted liquidity:", 'buy:', sum([l['bid'] for l in lq]) / len(lq), 'ask:', sum([l['ask'] for l in lq])  / len(lq)
    lq = []
  if ts % 120 == 0: update_price()
  if ts % 86400 == 0: pay()
  try: time.sleep(5.0 - (time.time() - curtime) / 1000.0)
  except KeyboardInterrupt:
    httpd.socket.close()
    break