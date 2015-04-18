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

import SimpleHTTPServer
import SocketServer
import BaseHTTPServer
import cgi
import logging
import urllib
import sys, os
import time
import thread
import threading
import sys
from math import log, exp, ceil
from thread import start_new_thread
from exchanges import *
from utils import *
import config

_wrappers = { 'bittrex' : Bittrex, 'poloniex' : Poloniex, 'ccedk' : CCEDK, 'bitcoincoid' : BitcoinCoId, 'bter' : BTER, 'testing' : Peatio }
for e in config._interest:
  _wrappers[e] = _wrappers[e]()
  for u in config._interest[e]:
    for s in ['bid', 'ask']:
      config._interest[e][u][s]['orders'] = []
      config._interest[e][u][s]['low'] = config._interest[e][u][s]['rate']
      config._interest[e][u][s]['high'] = config._interest[e][u][s]['rate']

try: os.makedirs('logs')
except: pass
try: os.makedirs('stats')
except: pass

dummylogger = logging.getLogger('null')
dummylogger.addHandler(logging.NullHandler())
dummylogger.propagate = False

logname = str(int(time.time()*100))
creditor = logging.getLogger("credits")
creditor.propagate = False
creditformat = logging.Formatter(fmt = '%(asctime)s: %(message)s', datefmt="%Y/%m/%d-%H:%M:%S")
ch = logging.FileHandler('logs/%s.credits' % logname)
ch.setFormatter(creditformat)
creditor.addHandler(ch)
logger = logging.getLogger()
logger.setLevel(logging.DEBUG)
fh = logging.FileHandler('logs/%s.log' % logname)
fh.setLevel(logging.DEBUG)
sh = logging.StreamHandler()
sh.setLevel(logging.INFO)

formatter = logging.Formatter(fmt = '%(asctime)s %(levelname)s: %(message)s', datefmt="%Y/%m/%d-%H:%M:%S")
fh.setFormatter(formatter)
sh.setFormatter(formatter)
logger.addHandler(fh)
logger.addHandler(sh)
_liquidity = []
_active_users = 0
_round = 0
_valflag = False
master = Connection(config._master, logger) if config._master != "" else None
slaves = [ CheckpointThread(host, logger) for host in config._slaves ]

keys = {}
pricefeed = PriceFeed(15, logger)
lock = threading.Lock()

class NuRPC():
  def __init__(self, config, address, logger = None):
    self.logger = logger if logger else logging.getLogger('null')
    self.address = address
    self.rpc = None
    try:
      import jsonrpc
    except ImportError:
      self.logger.warning('NuRPC: jsonrpc library could not be imported')
    else:
      # rpc connection
      self.JSONRPCException = jsonrpc.JSONRPCException
      opts = dict(tuple(line.strip().replace(' ','').split('=')) for line in open(config).readlines() if len(line.split('=')) == 2)
      if not 'rpcuser' in opts.keys() or not 'rpcpassword' in opts.keys():
        self.logger.error("NuRPC: RPC parameters could not be read")
      else:
        try:
          self.rpc = jsonrpc.ServiceProxy("http://%s:%s@127.0.0.1:%s"%(
            opts['rpcuser'],opts['rpcpassword'], 14002))
          self.txfee = self.rpc.getinfo()['paytxfee']
        except:
          self.logger.error("NuRPC: RPC connection could not be established")
          self.rpc = None

  def pay(self, txout):
    try:
      self.rpc.sendmany("", txout)
      self.logger.info("successfully sent payout: %s", txout)
      return True
    except AttributeError:
      self.logger.error('NuRPC: client not initialized')
    except self.JSONRPCException as e:
      self.logger.error('NuRPC: unable to send payout: %s', e.error['message'])
    except:
      self.logger.error("NuRPC: unable to send payout (exception caught): %s", sys.exc_info()[1])
    return False

  def liquidity(self, bid, ask):
    try:
      self.rpc.liquidityinfo('B', bid, ask, self.address)
      self.logger.info("successfully sent liquidity: buy: %.8f sell: %.8f", bid, ask)
      return True
    except AttributeError:
      self.logger.error('NuRPC: client not initialized')
    except self.JSONRPCException as e:
      self.logger.error('NuRPC: unable to send liquidity: %s', e.error['message'])
    except:
      self.logger.error("NuRPC: unable to send liquidity (exception caught): %s", sys.exc_info()[1])
    return False

class User(threading.Thread):
  def __init__(self, key, address, unit, exchange, pricefeed, sampling, tolerance, logger = None):
    threading.Thread.__init__(self)
    self.key = key
    self.active = True
    self.address = address
    self.balance = 0.0
    self.pricefeed = pricefeed
    self.unit = unit
    self.exchange = exchange
    self.tolerance = tolerance
    self.sampling = sampling
    self.last_errors = [ "" ] * sampling
    self.cost = { 'ask' : config._interest[repr(exchange)][unit]['bid']['rate'], 'bid' : config._interest[repr(exchange)][unit]['ask']['rate'] }
    self.rate = { 'ask' : config._interest[repr(exchange)][unit]['bid']['rate'], 'bid' : config._interest[repr(exchange)][unit]['ask']['rate'] }
    self.liquidity = { 'ask' : [[] for i in xrange(sampling)], 'bid' : [[] for i in xrange(sampling)] }
    self.credits = { 'ask' : [ [ {'amount' : 0.0, 'cost' : 0.0}, {'amount' : 0.0, 'cost' : 0.0}, {'amount' : 0.0, 'cost' : 0.0} ] for i in xrange(sampling) ],
                     'bid' : [ [ {'amount' : 0.0, 'cost' : 0.0}, {'amount' : 0.0, 'cost' : 0.0}, {'amount' : 0.0, 'cost' : 0.0} ] for i in xrange(sampling) ] }
    self.lock = threading.Lock()
    self.trigger = threading.Lock()
    self.trigger.acquire()
    self.response = ['m' for i in xrange(sampling)]
    self.logger = logger if logger else logging.getLogger('null')
    self.requests = []
    self.daemon = True
    self.history = []
    self.page = 1
    self.record()
    self.bundle()

  def record(self):
    missings = self.response.count('m')
    rejects = self.response.count('r')
    amount = { 'bid' : [], 'ask': [] }
    norm = max(1.0, float(len(self.response) - missings - rejects))
    for i in xrange(len(self.response)):
      for side in ['bid', 'ask']:
        stats = config._interest[repr(self.exchange)][self.unit][side]
        if not amount[side] and self.response[i] == 'a':
          amount[side] = [ self.credits[side][i][j]['amount'] for j in xrange(3) ]
        if self.credits[side][i][0]['cost'] == stats['high'] or self.credits[side][i][1]['cost'] == stats['low']:
          amount[side] = [ self.credits[side][i][j]['amount'] for j in xrange(3) ]
    self.history.append({ 'time': int(time.time()), 'balance' : self.balance, 'missings' : missings, 'rejects' : rejects, 'bid': amount['bid'], 'ask' : amount['ask'], 'rate' : self.rate})
    if len(self.history) == 60 * 24 + 1:
      with open('stats/%s.%s.%s.%d' % (logname, self.key, self.unit, self.page), 'w') as fo:
        fo.write(json.dumps(self.history))
        self.page += 1
      self.history = self.history[-1:]

  def bundle(self):
    self.checkpoint = { 'liquidity' : self.liquidity.copy(), 'response' : self.response[:], 'last_errors' : self.last_errors[:], 'balance' : self.balance }

  def set(self, request, bid, ask, sign):
    if len(self.requests) < 10: # don't accept more requests to avoid simple spamming
      self.requests.append(({ p : v[0] for p,v in request.items() }, sign, { 'bid': bid, 'ask': ask }))
    self.active = True

  def run(self):
    while True:
      self.trigger.acquire()
      self.lock.acquire()
      res = 'm'
      if self.requests:
        requests = self.requests[:]
        self.requests = []
        for rid, request in enumerate(requests):
          try:
            orders = self.exchange.validate_request(self.key, self.unit, request[0], request[1])
          except:
            orders = { 'error' : 'exception caught: %s' % sys.exc_info()[1]}
          if not 'error' in orders:
            valid = { 'bid': [], 'ask' : [] }
            price = self.pricefeed.price(self.unit)
            last_error = ''
            for order in orders:
              deviation = 1.0 - min(order['price'], price) / max(order['price'], price)
              if deviation <= self.tolerance:
                span = 60.0 / config._sampling
                et = int(time.time())
                st = et - span
                if 'closed' in order and order['closed'] < et:
                  et = order['closed']
                if 'opened' in order and order['opened'] > st:
                  st = order['opened']
                order['amount'] *= max(0.0, float(et - st) / span)
                valid[order['type']].append([order['id'], order['amount'], request[2][order['type']]])
              else:
                self.last_errors.append('unable to validate request: order of deviates too much from current price')
            for side in [ 'bid', 'ask' ]:
              del self.liquidity[side][0]
              self.liquidity[side].append(valid[side])
            if last_error != "" and len(valid['bid'] + valid['ask']) == 0:
              res = 'r'
              self.last_errors.append(last_error)
              self.logger.debug("unable to validate request %d/%d for user %s at exchange %s on unit %s: orders of deviate too much from current price",
                rid + 1, len(self.requests), self.key, repr(self.exchange), self.unit)
            else:
              res = 'a'
              self.last_errors.append("")
              break
          else:
            res = 'r'
            self.last_errors.append("unable to validate request: " + orders['error'])
            if rid + 1 == len(self.requests):
              self.logger.warning("unable to validate request %d/%d for user %s at exchange %s on unit %s: %s",
                rid + 1, len(self.requests), self.key, repr(self.exchange), self.unit, orders['error'])
            for side in [ 'bid', 'ask' ]:
              del self.liquidity[side][0]
              self.liquidity[side].append([])
      else:
        self.last_errors.append("no request received")
        for side in [ 'bid', 'ask' ]:
          del self.liquidity[side][0]
          self.liquidity[side].append([])
      self.response = self.response[1:] + [res]
      del self.last_errors[0]
      self.lock.release()

  def validate(self):
    try: self.trigger.release()
    except thread.error: pass # user did not finish last request in time

  def finish(self):
    if self.active:
      try:
        self.lock.acquire()
        self.lock.release()
      except KeyboardInterrupt:
        raise

def response(errcode = 0, message = 'success'):
  return { 'code' : errcode, 'message' : message }

def register(params):
  ret = response()
  if set(params.keys()) == set(['address', 'key', 'name']):
    user = params['key'][0]
    name = params['name'][0]
    address = params['address'][0]
    if address[0] == 'B': # this is certainly not a proper check
      if name in config._interest:
        for slave in slaves:
          slave.register(address, user, name)
        if not user in keys:
          lock.acquire()
          keys[user] = {}
          for unit in config._interest[name]:
            keys[user][unit] = User(user, address, unit, _wrappers[name], pricefeed, config._sampling, config._tolerance, logger)
            keys[user][unit].start()
          lock.release()
          logger.info("new user %s on %s: %s" % (user, name, address))
        else:
          if keys[user].values()[0].address != address:
            ret = response(9, "user already exists with different address: %s" % user)
      else:
        ret = response(8, "unknown exchange requested: %s" % name)
    else:
      ret = response(7, "invalid payout address: %s" % address)
  else:
    ret = response(6, "invalid registration data received: %s" % str(params))
  return ret

def liquidity(params):
  ret = response()
  if set(params.keys() + ['user', 'sign', 'unit', 'ask', 'bid']) == set(params.keys()):
    user = params.pop('user')[0]
    sign = params.pop('sign')[0]
    unit = params.pop('unit')[0]
    try:
      bid = float(params.pop('bid')[0])
      ask = float(params.pop('ask')[0])
      if user in keys:
        if unit in keys[user]:
          start_new_thread(keys[user][unit].set, (params, bid, ask, sign))
        else:
          ret = response(12, "unit for user %s not found: %s" % (user, unit))
      else:
        ret = response(11, "user not found: %s" % user)
    except ValueError:
      ret = response(10, "invalid cost information received: %s" % str(params))
  return ret

def poolstats():
  return { 'liquidity' : ([ (0,0) ] + _liquidity)[-1], 'sampling' : config._sampling, 'users' : _active_users, 'credits' : _round / config._sampling, 'validations' : _round }

critical_message = ""
def userstats(user):
  res = { 'balance' : 0.0, 'efficiency' : 0.0, 'rejects': 0, 'missing' : 0, 'message' : critical_message }
  res['units'] = {}
  for unit in keys[user]:
    checkpoint = keys[user][unit].checkpoint
    if checkpoint['liquidity']['bid'].count([]) + checkpoint['liquidity']['ask'].count([]) < 2 * config._sampling:
      credits = { 'bid' : [ { 'amount': 0.0, 'cost': -1.0 }, { 'amount': 0.0, 'cost': -1.0 }, { 'amount': 0.0, 'cost': -1.0 } ],
                  'ask' : [ { 'amount': 0.0, 'cost': -1.0 }, { 'amount': 0.0, 'cost': -1.0 }, { 'amount': 0.0, 'cost': -1.0 } ] }
      last_error = ""
      missing = checkpoint['response'].count('m')
      rejects = checkpoint['response'].count('r')
      res['balance'] += checkpoint['balance']
      res['missing'] += missing
      res['rejects'] += rejects
      norm = max(1.0, float(keys[user][unit].sampling - missing - rejects))
      for i in xrange(keys[user][unit].sampling):
        if checkpoint['last_errors'][i] != "":
          last_error = checkpoint['last_errors'][i]
        for side in ['bid', 'ask']:
          stats = config._interest[repr(keys[user][unit].exchange)][unit][side]
          if credits[side][0]['cost'] < 0.0 and checkpoint['response'][i] == 'a':
            for j in xrange(3):
              credits[side][j]['amount'] = keys[user][unit].credits[side][i][j]['amount']
              credits[side][j]['cost'] = 0.0
          if keys[user][unit].credits[side][i][0]['cost'] == stats['high'] or keys[user][unit].credits[side][i][1]['cost'] == stats['low']:
            for j in xrange(3):
              credits[side][j]['amount'] = keys[user][unit].credits[side][i][j]['amount']
      for side in ['bid', 'ask']:
        stats = config._interest[repr(keys[user][unit].exchange)][unit][side]
        credits[side][0]['cost'] = stats['high']
        credits[side][1]['cost'] = stats['low']
      res['units'][unit] = { 'bid' : credits['bid'],
                             'ask' : credits['ask'],
                             'rate' : keys[user][unit].rate,
                             'rejects' : rejects,
                             'missing' : missing,
                             'active' : keys[user][unit].active,
                             'last_error' :  last_error }
  if len(res['units']) > 0:
    res['efficiency'] = 1.0 - (res['rejects'] + res['missing']) / float(config._sampling * len(res['units']))
  return res

def collect(timeout):
  for slave in slaves:
    slave.collect(timeout)
  for slave in slaves:
    checkpoint = slave.finish()
    if not 'error' in checkpoint:
      for user in checkpoint:
        for unit in checkpoint[user]:
          for i in xrange(config._sampling):
            if keys[user][unit].response[i] == 'm':
              keys[user][unit].last_errors[i] = checkpoint[user][unit]['last_errors'][i]
              if checkpoint[user][unit]['response'][i] != 'm':
                keys[user][unit].response[i] = checkpoint[user][unit]['response'][i]
                if checkpoint[user][unit]['response'][i] == 'a':
                  for side in [ 'bid', 'ask' ]:
                    keys[user][unit].liquidity[side][i] = checkpoint[user][unit]['liquidity'][side][i]
  for user in keys:
    for unit in keys[user]:
      keys[user][unit].bundle()
      keys[user][unit].active = keys[user][unit].liquidity['bid'].count([]) + keys[user][unit].liquidity['ask'].count([]) < 2 * keys[user][unit].sampling

def checkpoints(params):
  ret = {}
  for user in params:
    if user in keys:
      for unit in keys[user]:
        if keys[user][unit].active:
          if not user in ret: ret[user] = {}
          ret[user][unit] = keys[user][unit].checkpoint
  return ret

def credit():
  for name in config._interest:
    for unit in config._interest[name]:
      users = [ k for k in keys if unit in keys[k] and repr(keys[k][unit].exchange) == name ]
      for user in users:
        keys[user][unit].record()
        keys[user][unit].rate['bid'] = 0.0
        keys[user][unit].rate['ask'] = 0.0
      for side in [ 'bid', 'ask' ]:
        config._interest[name][unit][side]['low'] = 0.0
        config._interest[name][unit][side]['high'] = 0.0
        config._interest[name][unit][side]['orders'] = []
        for sample in xrange(config._sampling):
          config._interest[name][unit][side]['orders'].append([])
          # payout variables
          maxrate = config._interest[name][unit][side]['rate'] 
          submitted = []
          for user in users:
            keys[user][unit].credits[side][sample] = [ { 'amount' : 0.0, 'cost' : 0.0 }, { 'amount' : 0.0, 'cost' : 0.0 }, { 'amount' : 0.0, 'cost' : 0.0 } ]
            submitted.extend([ (user, order) for order in keys[user][unit].liquidity[side][sample] ])
          submitted.sort(key = lambda x: (x[1][2], x[1][0]))
          orders = [ submitted[i] for i in xrange(len(submitted)) if i == 0 or submitted[i][1][0] != submitted[i - 1][1][0] ]
          mass = sum([order[1] for _,order in submitted])
          if mass > 0:
            target = min(mass, config._interest[name][unit][side]['target'])
            maxlevel = int(ceil(mass / target))
            pricelevels = sorted(list(set( [ order[2] for _,order in orders if order[2] < maxrate ])) + [maxrate, maxrate])
            if len(pricelevels) < maxlevel + 2:
              pricelevels += [maxrate] * (2 + maxlevel - len(pricelevels))
            # calculate level
            levelvolume = [ 0.0 for p in pricelevels ]
            for user,order in orders:
              if order[2] <= maxrate:
                for i,p in enumerate(pricelevels):
                  if order[2] < p or p == maxlevel:
                    levelvolume[i] += order[1]
            lower = mass - int(mass / target) * target
            higher = int((mass / target) + 1) * target - mass
            lvl = len(pricelevels) - 3
            for i in xrange(1, len(levelvolume) - 1):
              if levelvolume[i - 1] >= lower and levelvolume[i] >= config._interest[name][unit][side]['target']:
                lvl = i - 2
                break
            config._interest[name][unit][side]['low'] = pricelevels[lvl+1]
            config._interest[name][unit][side]['high'] = pricelevels[lvl+2]
            # collect user contribution
            volume = [ { user : 0.0 for user in users }, { user : 0.0 for user in users }, { user : 0.0 for user in users } ]
            for user,order in orders:
              volume[2][user] += order[1]
              if order[2] <= maxrate:
                ulvl = pricelevels.index(order[2])
                if ulvl < lvl + 1:
                  volume[0][user] += order[1]
                if ulvl < lvl + 2 or pricelevels[lvl + 2] == maxlevel:
                  volume[1][user] += order[1]
            if sample == config._sampling - 1:
              logger.debug('%s pricelevel %d [%.4f,%.4f]: %s', side, lvl, float(sum(volume[0].values())), float(sum(volume[1].values())), " ".join([str(s) for s in pricelevels]))
              logger.debug('%s pricevolumes [%.4f,%.4f]: %s', side, lower, higher, " ".join([str(s) for s in levelvolume]))
            # credit higher payout level
            norm = float(sum(volume[1].values()))
            for user in volume[1]:
              if norm > 0 and volume[1][user] > 0:
                price = pricelevels[lvl+2]
                contrib = min(volume[1][user], higher * volume[1][user] / norm)
                payout = contrib * price
                volume[0][user] -= contrib
                volume[2][user] -= contrib
                keys[user][unit].balance += payout / float(24 * 60  * config._sampling)
                keys[user][unit].credits[side][sample][0] = { 'amount' : contrib, 'cost' : price }
                keys[user][unit].rate[side] += price * contrib / (volume[1][user] * config._sampling)
                config._interest[name][unit][side]['orders'][sample].append({ 'amount' : contrib, 'cost' : price })
                creditor.info("[%d/%d] %.8f %s %.8f %s %s %s %.2f high %s",
                  sample + 1, config._sampling, payout / float(24 * 60  * config._sampling), user, contrib, side, name, unit, price * 100, keys[user][unit].address)
            # credit lower payout level
            norm = float(sum([ max(0,v) for v in volume[0].values()]))
            for user in volume[0]:
              if norm > 0 and volume[0][user] > 0:
                price = pricelevels[lvl+1]
                contrib = min(volume[0][user], lower * volume[0][user] / norm)
                payout = contrib * price
                volume[2][user] -= contrib
                keys[user][unit].balance += payout / float(24 * 60  * config._sampling)
                keys[user][unit].credits[side][sample][1] = { 'amount' : contrib, 'cost' : price }
                keys[user][unit].rate[side] += price * contrib / (volume[0][user] * config._sampling)
                config._interest[name][unit][side]['orders'][sample].append({ 'amount' : contrib, 'cost' : price })
                creditor.info("[%d/%d] %.8f %s %.8f %s %s %s %.2f low %s", 
                  sample + 1, config._sampling, payout / float(24 * 60  * config._sampling), user, contrib, side, name, unit, price * 100, keys[user][unit].address)
            # mark zero payout level
            for user in volume[2]:
              if volume[2][user] > float(10**(-8)):
                keys[user][unit].credits[side][sample][2] = { 'amount' : volume[2][user], 'cost' : 0.0 }
                config._interest[name][unit][side]['orders'][sample].append({ 'amount' : volume[2][user], 'cost' : 0.0 })

def pay(nud):
  txout = {}
  lock.acquire()
  for user in keys:
    for unit in keys[user]:
      if not keys[user][unit].address in txout:
        txout[keys[user][unit].address] = 0.0
      txout[keys[user][unit].address] += keys[user][unit].balance
  lock.release()
  txfee = 0.01 if not nud.rpc else nud.txfee
  txout = {k : v - nud.txfee for k,v in txout.items() if v - txfee > config._minpayout}
  if txout:
    sent = False
    if config._autopayout:
      sent = nud.pay(txout)
    try:
      filename = 'logs/%d.credit' % time.time()
      out = open(filename, 'w')
      out.write(json.dumps(txout))
      out.close()
      if not sent:
        logger.info("successfully stored payout to %s: %s", filename, txout)
      lock.acquire()
      for user in keys:
        for unit in keys[user]:
          if keys[user][unit].address in txout and keys[user][unit].balance > 0.0:
            creditor.info("[-] %.8f %s %s", keys[user][unit].balance, user, unit)
            keys[user][unit].balance = 0.0
      lock.release()
    except: logger.error("failed to store payout to %s: %s", filename, txout)
  else:
    logger.warning("not processing payouts because no valid balances were detected.")

def submit(nud):
  curliquidity = [0,0]
  lock.acquire()
  for user in keys:
    for unit in keys[user]:
      for s in xrange(config._sampling):
        curliquidity[0] += sum([ order[1] for order in keys[user][unit].liquidity['bid'][-(s+1)] ])
        curliquidity[1] += sum([ order[1] for order in keys[user][unit].liquidity['ask'][-(s+1)] ])
  lock.release()
  curliquidity = [ curliquidity[0] / float(config._sampling), curliquidity[1] / float(config._sampling) ]
  _liquidity.append(curliquidity)
  nud.liquidity(curliquidity[0], curliquidity[1])

def sync():
  ts = int(time.time() * 1000.0)
  return { 'time' : ts, 'sync' : 15000, 'round' : _round }

class RequestHandler(SimpleHTTPServer.SimpleHTTPRequestHandler):
  def do_POST(self):
    if len(self.path) == 0:
      self.send_response(404)
      return
    self.path = self.path[1:]
    if self.path in ['register', 'liquidity', 'checkpoints']:
      ctype, pdict = cgi.parse_header(self.headers.getheader('content-type'))
      if ctype == 'application/x-www-form-urlencoded':
        length = int(self.headers.getheader('content-length'))
        params = cgi.parse_qs(self.rfile.read(length), keep_blank_values = 1)
        if self.path == 'register':
          ret = register(params)
        elif self.path == 'liquidity':
          ret = liquidity(params)
        elif self.path == 'checkpoints':
          if _valflag: ret = { 'error' : "validation in progress"}
          else: ret = checkpoints(params)
      self.send_response(200)
      self.send_header('Content-Type', 'application/json')
      self.wfile.write("\n")
      self.wfile.write(json.dumps(ret))
      self.end_headers()

  def do_GET(self):
    if len(self.path.replace('/', '')) == 0:
      self.send_response(200)
      return
    method = self.path[1:]
    if master:
      try:
        content = json.dumps(master.get(method, trials = 1, timeout = 5))
        self.send_response(200)
        self.send_header('Content-Type', 'text/plain')
        self.wfile.write("\n")
        self.wfile.write(content)
        self.end_headers()
      except:
        self.send_response(404)
    elif 'loaderio' in method: # evil hack to support load tester (TODO)
      self.send_response(200)
      self.send_header('Content-Type', 'text/plain')
      self.wfile.write("\n")
      self.wfile.write(method.replace('/',''))
      self.end_headers()
    elif method in [ 'status', 'exchanges', 'sync' ]:
      self.send_response(200)
      self.send_header('Content-Type', 'application/json')
      self.wfile.write("\n")
      if method == 'status':
        self.wfile.write(json.dumps(poolstats()))
      elif method == 'exchanges':
        self.wfile.write(json.dumps(config._interest))
      elif method == 'sync':
        self.wfile.write(json.dumps(sync()))
      self.end_headers()
    elif method in keys:
      self.send_response(200)
      self.send_header('Content-Type', 'application/json')
      self.wfile.write("\n")
      self.wfile.write(json.dumps(userstats(method)))
      self.end_headers()
    elif '/' in method:
      root = method.split('/')[0]
      method = method.split('/')[1:]
      if root == 'price':
        price = { 'price' : pricefeed.price(method[0]) }
        if price['price']:
          self.send_response(200)
          self.send_header('Content-Type', 'application/json')
          self.wfile.write("\n")
          self.wfile.write(json.dumps(price))
          self.end_headers()
        else:
          self.send_response(404)
      elif root == 'info':
        if len(method) == 2 and method[0] in config._interest and method[1] in config._interest[method[0]]:
          self.send_response(200)
          self.send_header('Content-Type', 'application/json')
          self.wfile.write("\n")
          self.wfile.write(json.dumps(config._interest[method[0]][method[1]]))
          self.end_headers()
        else:
          self.send_response(404)
      elif root == 'history':
        try:
          page = int(method[2])
          if page == 0:
            content = json.dumps(keys[method[0]][method[1]].history)
          else:
            content = open('stats/%s.%s.%s.%d' % (logname, method[0], method[1], page)).read()
          content = fin.read()
          self.send_response(200)
          self.send_header('Content-Type', 'application/json')
          self.wfile.write("\n")
          self.wfile.write(content)
          self.end_headers()
        except:
          self.send_response(404)
      else:
        self.send_response(404)
    else:
      self.send_response(404)

  def log_message(self, format, *args): pass

class ThreadingServer(SocketServer.ThreadingMixIn, BaseHTTPServer.HTTPServer):
  pass

nud = NuRPC(config._nuconfig, config._grantaddress, logger)
if not nud.rpc:
  logger.critical('Connection to Nu daemon could not be established, liquidity will NOT be sent!')
  config._autopayout = False
httpd = ThreadingServer(("", config._port), RequestHandler)
sa = httpd.socket.getsockname()
logger.debug("serving on %s port %d", sa[0], sa[1])
start_new_thread(httpd.serve_forever, ())

if master:
  _round = -1
  ts = int(time.time() * 1000.0)
  ret = master.get('sync', trials = 3, timeout = 15)
  if not 'error' in ret:
    _round = ret['round']
    delay = (60000 - (ret['time'] % 60000)) - (int(time.time() * 1000.0) - ts) / 2
    if delay <= 0:
      logger.error("unable to synchronize time with master server: time difference to small")
    logger.info("waiting %.2f seconds to synchronize with master server", delay / 1000.0)
    time.sleep(delay / 1000.0)
  else:
    logger.error("unable to synchronize time with master server: %s", ret['message'])
elif slaves:
  delay = max(float(60000 - (int(time.time()*1000) % 60000)), 0.0)
  if delay > 0.0:
    logger.info("waiting %.2f seconds to synchronize with slave servers", delay / 1000.0)
    time.sleep(delay / 1000.0)

lastcheckp = time.time()
lastcredit = time.time()
lastpayout = time.time()
lastsubmit = time.time()

while True:
  try:
    curtime = time.time()

    # wait for validation round to end:
    lock.acquire()
    _active_users = 0
    for user in keys:
      active = False
      for unit in keys[user]:
        keys[user][unit].finish()
        active = active or keys[user][unit].active
      if active: _active_users += 1
    lock.release()

    # create checkpoints
    if not slaves or curtime - lastcheckp >= 60:
      collect(max(float(60 / config._sampling) - time.time() + curtime, 0.01) / 2.0)
      lastcheckp = curtime
    _valflag = False

    if not master:
      _round += 1
      # send liquidity
      if curtime - lastsubmit >= 60:
        submit(nud)
        lastsubmit = curtime
      # credit requests
      if curtime - lastcredit >= 60:
        credit()
        lastcredit = curtime
      # make payout
      if curtime - lastpayout >= 86400:
        pay(nud)
        lastpayout = curtime
    else:
      while True:
        ret = master.get('sync', trials = 1, timeout = 1)
        if 'error' in ret or ret['round'] == _round:
          time.sleep(0.1)
          continue
        _round = ret['round']
        break

    # start new validation round
    _valflag = True
    lock.acquire()
    for user in keys:
      for unit in keys[user]:
        keys[user][unit].validate()
    lock.release()

    if not master:
      time.sleep(max(float(60 / config._sampling) - time.time() + curtime, 0))
  except Exception as e:
    logger.error('exception caught in main loop: %s', sys.exc_info()[1])
    httpd.socket.close()
    raise
