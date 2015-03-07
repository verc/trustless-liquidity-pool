#! /usr/bin/env python

import SimpleHTTPServer
import BaseHTTPServer
import cgi
import urllib
import sys
import time
from thread import start_new_thread
from exchanges import *

_port = 2019
_markets = { 'poloniex' : { 'BTC' : { 'request' : None, 'bid' : [], 'ask' : [] } } }
_wrappers = { 'poloniex' : Poloniex() }
_tolerance = 100000

keys = {}
payout = {}
price = {'BTC' : 0.003666}

class RequestHandler(SimpleHTTPServer.SimpleHTTPRequestHandler):
  def do_POST(self):
    ctype, pdict = cgi.parse_header(self.headers.getheader('content-type'))
    if ctype == 'application/x-www-form-urlencoded':
      length = int(self.headers.getheader('content-length'))
      params = cgi.parse_qs(self.rfile.read(length), keep_blank_values = 1)
      if self.path == 'liquidity':
        if set(params.keys() + ['name', 'unit', 'user', 'sign']) == set(params.keys()):
          name = params.pop('name')[0]
          if name in _markets.keys() and params['unit'][0] in _markets[name]:
            user = params.pop('user')[0]
            unit = params.pop('unit')[0]
            sign = params.pop('sign')[0]
            if user in keys:
              keys[user][name][unit]['request'] = ({ p : v[0] for p,v in params.items() }, sign)
              print "liquidity received - user: %s exchange: %s unit: %s" % (user, name, unit)
            else:
              print >> sys.stderr, "user not found:", user
          else:
            print >> sys.stderr, "%s market not supported on %s" % (params['unit'], params['name'])
        else:
          print >> sys.stderr, "invalid liquidity data received:", params
      elif self.path == 'register':
        if set(params.keys()) == set(['address', 'key']):
          user = params['key'][0]
          if not user in keys:
            keys[user] = _markets.copy()
            payout[user] = params['address'][0]
            print "new user %s: %s" % (user, payout[user])
          else:
            print >> sys.stderr, "user already exists:", user
        else:
          print >> sys.stderr, "invalid registration data received:", params
      else:
        print >> sys.cerr, "invalid path:", path
    else:
      print >> sys.stderr, "invalid ctype received:", ctype
    self.send_response(301)
    self.end_headers()

  def log_message( self, format, *args ):
    pass

def update_price():
  price = {'BTC' : 0.003666}

def validate():
  for user in keys:
    for name in keys[user]:
      for unit in keys[user][name]:
        if keys[user][name][unit]['request']:
          orders = _wrappers[name].validate_request(user, *keys[user][name][unit]['request'])
          if orders != None:
            for order in orders:
              if abs(order['price'] - price[unit]) < _tolerance:
                keys[user][name][unit][order['type']].append((order['id'], order['amount']))
                print "liquidity validated - user: %s exchange: %s unit: %s" % (user, name, unit)
              else:
                print >> sys.stderr, "warning: order deviates too much from current price"
          else:
            print >> sys.stderr, "ERROR: unable to validate request:", keys[user][name][unit]['request']
        else:
            print >> sys.stderr, "WARNING: no request received from user", user
        keys[user][name][unit]['request'] = None

def credit():
  for user in keys:
    for name in keys[user]:
      for unit in keys[user][name]:
        nreqs = float(len(keys[user][name][unit]['bid']))
        if nreqs > 0:
          bid = sum([x[1] for x in keys[user][name][unit]['bid']]) / nreqs
          ask = sum([x[1] for x in keys[user][name][unit]['ask']]) / nreqs
        else:
          bid = 0
          ask = 0
        print "user: %s exchange: %s unit: %s requests: %d/12 bid: %.2f ask: %.2f" % (user , name, unit, nreqs, bid, ask)
        keys[user][name][unit]['bid'] = []
        keys[user][name][unit]['ask'] = []

httpd = BaseHTTPServer.HTTPServer(("", _port), RequestHandler)
sa = httpd.socket.getsockname()
print "Serving HTTP on", sa[0], "port", sa[1], "..."
start_new_thread(httpd.serve_forever, ())

ts = 0
while True:
  ts = (ts % 120) + 5
  curtime = time.time()
  if ts % 5 == 0: validate()
  if ts % 60 == 0: credit()
  if ts % 120 == 0: update_price()
  try: time.sleep(5.0 - (time.time() - curtime) / 1000.0)
  except KeyboardInterrupt:
    httpd.socket.close()
    break