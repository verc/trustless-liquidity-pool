#! /usr/bin/env python

import SimpleHTTPServer
import BaseHTTPServer
import cgi
import logging
import urllib
import sys, os
import errno
import time
from math import log, exp
from thread import start_new_thread
from exchanges import *

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

if not os.path.isdir('logs'):
  os.makedirs('logs')

_port = 2019
_interest = { 'poloniex' : { 'btc' : { 'rate' : 0.002, 'target' : 100.0 } }, 'ccedk' : { 'btc' : { 'rate' : 0.002, 'target' : 100.0 } } }
_nuconfig = '%s/.nu/nu.conf'%os.getenv("HOME") # path to nu.conf
_wrappers = { 'poloniex' : Poloniex(), 'ccedk' : CCEDK() }
_tolerance = 0.03
_sampling = 12
_minpayout = 0.1

keys = {}
price = {'btc' : 0.003666}

_lock = False
def acquire_lock():
  global _lock
  while _lock: pass
  _lock = True
def release_lock():
  global _lock
  _lock = False

def response(errcode = 0, message = 'success'):
  return { 'code' : errcode, 'message' : message }

def register(params):
  ret = response()
  if set(params.keys()) == set(['address', 'key', 'name']):
    user = params['key'][0]
    name = params['name'][0]
    if name in _wrappers:
      if not user in keys:
        acquire_lock()
        keys[user] = { 'name' : params['name'][0], 'address' : params['address'][0], 'balance' : 0.0, 'accepts' : 0, 'units' : {} }
        for unit in _interest[name]:
          keys[user]['units'][unit] = { 'request' : None, 'bid' : [], 'ask' : [] }
        release_lock()
        logger.info("new user %s: %s" % (user, keys[user]['address']))
      elif keys[user]['address'] != params['address'][0]:
        ret = response(9, "user already exists with different address: %s" % user)
    else:
      ret = response(8, "unknown exchange requested: %s" % name)
  else:
    ret = response(7, "invalid registration data received: %s" % str(params))
  return ret

def liquidity(params):
  ret = response()
  if set(params.keys() + ['user', 'sign', 'unit']) == set(params.keys()):
    user = params.pop('user')[0]
    sign = params.pop('sign')[0]
    unit = params.pop('unit')[0]
    if user in keys:
      if unit in _interest[keys[user]['name']]:
        acquire_lock()
        keys[user]['units'][unit]['request'] = ({ p : v[0] for p,v in params.items() }, sign)
        release_lock()
      else:
        ret = response(12, "%s market not supported on %s" % (unit, keys[user]['name']))
    else:
        ret = response(11, "user not found: %s" % user)
  else:
    ret = response(10, "invalid liquidity data received: %s" % str(params))
  return ret

def userstats(user):
  res = { 'name' : keys[user]['name'], 'address' : keys[user]['address'], 'balance' : keys[user]['balance'], 'accepts' : keys[user]['accepts'] }
  res['orders'] = {}
  for unit in keys[user]['units']:
    bid = [ x for x in keys[user]['units'][unit]['bid'] if x ]
    ask = [ x for x in keys[user]['units'][unit]['ask'] if x ]
    if len(bid) > 0 or len(ask) > 0:
      bid, ask = [[]] + bid, [[]] + ask
      res['orders'][unit] = { 'bid' : bid[-1], 'ask' : ask[-1] }
  return res

class RequestHandler(SimpleHTTPServer.SimpleHTTPRequestHandler):
  def do_POST(self):
    if self.path in ['register', 'liquidity']:
      ctype, pdict = cgi.parse_header(self.headers.getheader('content-type'))
      if ctype == 'application/x-www-form-urlencoded':
        length = int(self.headers.getheader('content-length'))
        params = cgi.parse_qs(self.rfile.read(length), keep_blank_values = 1)
        if self.path == 'liquidity':
          ret = liquidity(params)
        elif self.path == 'register':
          ret = register(params)
      self.send_response(200)
      self.send_header('Content-Type', 'application/json')
      self.wfile.write("\n")
      self.wfile.write(json.dumps(ret))
      self.end_headers()

  def do_GET(self):
    method = self.path[1:]
    if method in keys:
      self.send_response(200)
      self.send_header('Content-Type', 'application/json')
      self.wfile.write("\n")
      self.wfile.write(json.dumps(userstats(method)))
      self.end_headers()
    else:
      self.send_response(404)

  def log_message(self, format, *args): pass

def update_price():
  try:
    ret = json.loads(urllib2.urlopen(urllib2.Request('https://api.bitfinex.com/v1//pubticker/btcusd')).read())
    price['btc'] = 1.0 / float(ret['mid'])
  except:
    logging.error("unable to update price for BTC")

def validate():
  liquidity = { 'bid' : 0.0, 'ask' : 0.0 }
  for user in keys:
    for unit in keys[user]['units']:
      if keys[user]['units'][unit]['request']:
        try:
          orders = _wrappers[keys[user]['name']].validate_request(user, unit, *keys[user]['units'][unit]['request'])
          keys[user]['units'][unit]['request'] = None
        except:
          orders = { 'error' : 'exception caught: %s' % str(e)}
        if not 'error' in orders:
          valid = { 'bid': [], 'ask' : [] }
          for order in orders:
            if 1.0 - min(order['price'], price[unit]) / max(order['price'], price[unit]) < _tolerance:
              valid[order['type']].append((order['id'], order['amount']))
            else:
              logger.warning("order of deviates too much from current price for user %s at exchange %s on market %s" % (user, keys[user]['name'], unit))
          for side in [ 'bid', 'ask' ]:
            keys[user]['units'][unit][side].append(valid[side])
            liquidity[side] += sum([ order[1] for order in valid[side]])
        else:
          logger.error("unable to validate request for user %s at exchange %s on market %s: %s" % (user, keys[user]['name'], unit, orders['error']))
      else:
        logger.warning("no request received for user %s at exchange %s on market %s" % (user, keys[user]['name'], unit))
  return liquidity

def calculate_interest(balance, amount, interest):
  return interest['rate'] * (amount - (log(exp(interest['target']) + exp(balance + amount)) - log(exp(interest['target']) + exp(balance))))

def credit():
  for name in _interest:
    for unit in _interest[name]:
      users = [ u for u in keys if keys[u]['name'] == name and unit in keys[u]['units'] ]
      for side in [ 'bid', 'ask' ]:
        for user in users:
          if len(keys[user]['units'][unit][side]) < _sampling:
            keys[user]['units'][unit][side] = [ [] ] * (_sampling - len(keys[user]['units'][unit][side])) + keys[user]['units'][unit][side]
          keys[user]['accepts'] += len(keys[user]['units'][unit][side]) - keys[user]['units'][unit][side].count([])
        for sample in xrange(_sampling):
          orders = []
          for user in users:
            orders += [ (user, order) for order in keys[user]['units'][unit][side][sample] ]
          orders.sort(key = lambda x: x[1][0])
          balance = 0.0
          for user, order in orders:
            payout = calculate_interest(balance, order[1], _interest[name][unit]) / (_sampling * 60 * 24)
            keys[user]['balance'] += payout
            balance += order[1]
            logger.info("credit %.8f NBT to %s for providing %.8f %s liquidity on the %s market of %s", payout, user, order[1], side, unit, keys[user]['name'])
        for user in users:
          keys[user]['units'][unit][side] = []

def pay():
  txout = {}
  for user in keys:
    if keys[user]['balance'] > _minpayout:
      txout[keys[user]['address']] = keys[user]['balance']
  if txout:
    # rpc connection
    opts = dict(tuple(line.strip().replace(' ','').split('=')) for line in open(_nuconfig).readlines())
    assert 'rpcuser' in opts.keys() and 'rpcpassword' in opts.keys(), "RPC parameters could not be read"
    rpc = jsonrpc.ServiceProxy("http://%s:%s@127.0.0.1:%s"%(
    opts['rpcuser'],opts['rpcpassword'],opts.pop('rpcport', 14002)))
    if _passphrase: # unlock wallet if required
      try: rpc.walletpassphrase(_passphrase, 30, False)
      except:pass
    # send the transactions
    try: rpc.sendmany("", txout)
    except: print "failed to send transactions:", txout
    else:
      for user in keys:
        if keys[user]['balance'] > _minpayout:
          keys[user]['balance'] = 0

httpd = BaseHTTPServer.HTTPServer(("", _port), RequestHandler)
sa = httpd.socket.getsockname()
logger.debug("Serving on %s port %d", sa[0], sa[1])
start_new_thread(httpd.serve_forever, ())
update_price()

ts = 0
lq = []
while True:
  ts = (ts % 86400) + 5
  curtime = time.time()
  if ts % (60 / _sampling) == 0:
    acquire_lock()
    lq.append(validate())
    release_lock()
  if ts % 60 == 0:
    acquire_lock()
    bid = sum([l['bid'] for l in lq]) / len(lq)
    ask = sum([l['ask'] for l in lq])  / len(lq)
    logger.info("liquidity buy: %.8f sell: %.08f", bid, ask)
    credit()
    lq = []
    release_lock()
  if ts % 120 == 0: update_price()
  if ts % 86400 == 0: pay()
  try: time.sleep(5.0 - (time.time() - curtime) / 1000.0)
  except KeyboardInterrupt:
    httpd.socket.close()
    break