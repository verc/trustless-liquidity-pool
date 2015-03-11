#! /usr/bin/env python

import os
import sys
import time
import json
import tempfile
import signal
import subprocess
import logging
import socket
from math import ceil
from exchanges import *

if len(sys.argv) < 2:
  print "usage:", sys.argv[0], "server[:port] [users.dat]"
  sys.exit(1)

if not os.path.isdir('logs'):
  os.makedirs('logs')

userfile = 'users.dat'
if len(sys.argv) == 3:
  userfile = sys.argv[2]
try:
  userdata = [ line.strip().split() for line in open(userfile).readlines() ] # address units exchange key secret
except:
  print "%s could not be read" % userfile
  sys.exit(1)

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

_server = sys.argv[1]
_wrappers = { 'poloniex' : Poloniex(), 'ccedk' : CCEDK(), 'bitcoincoid' : BitcoinCoId() }
_feeds = { 'btc' : {  'main-feed' : 'bitfinex',
                      'backup-feeds' : {  
                        'backup1' : { 'name' : 'blockchain' },
                        'backup2' : { 'name' : 'coinbase' },
                        'backup3' : { 'name' : 'bitstamp' }
                      } }, 'usd' : None }
_spread = 0.002

def json_request(request, method, params, headers):
  connection = httplib.HTTPConnection(_server, timeout=60)
  try:
    connection.request(request, method, urllib.urlencode(params), headers = headers)
    response = connection.getresponse()
    content = response.read()
    return json.loads(content)
  except httplib.BadStatusLine:
    logging.error("%s: server could not be reached, retrying in 15 seconds ...", method)
  except ValueError:
    logging.error("%s: server response invalid, retrying in 15 seconds ... %s", method, content)
  except socket.error:
    logging.error("%s: socket error, retrying in 15 seconds ...", method)
  time.sleep(15)
  return json_request(request, method, params, headers)

def get(method, params = None):
  if not params: params = {}
  return json_request('GET', '/' + method, params, {})

def post(method, params = None):
  if not params: params = {}
  headers = { "Content-type": "application/x-www-form-urlencoded" }
  return json_request('POST', method, params, headers)

def register(key, name, address):
  return post('register', {'address' : address, 'key' : key, 'name' : name})

def submit(key, name, unit, secret):
  data, sign = _wrappers[name].create_request(unit, key, secret)
  params = {
    'unit' : unit,
    'user' : key,
    'sign' : sign
  }
  params.update(data)
  return post('liquidity', params)

_exchanges = { 'time' : 0 }
def place(unit, side, name, key, secret, price):
  global _exchanges
  if side == 'ask':
    exunit = 'nbt'
    price *= (1.0 + _spread)
  else:
    exunit = unit
    price *= (1.0 - _spread)
  price = ceil(price * 10**8) / float(10**8) # truncate floating point precision after 8th position
  response = _wrappers[name].get_balance(exunit, key, secret)
  if 'error' in response:
    logger.error('unable to receive balance for unit %s on exchange %s: %s', exunit, name, response['error'])
    _wrappers[name].adjust(response['error'])
  elif response['balance'] >  0.0001:
    balance = response['balance'] if exunit == 'nbt' else response['balance'] / price
    if time.time() - _exchanges['time'] > 30: # this will be used to rebalance nbts
      _exchanges = get('exchanges')
      _exchanges['time'] = time.time()
    response = _wrappers[name].place_order(unit, side, key, secret, balance, price)
    if 'error' in response:
      logger.error('unable to place %s %s order iof %.4f NBT at %.8f on exchange %s: %s', side, exunit, balance, price, name, response['error'])
      _wrappers[name].adjust(response['error'])
    else:
      logger.info('successfully placed %s %s order of %.4f NBT at %.8f on exchange %s', side, exunit, balance, price, name)
  return response

def reset(user, unit, price, cancel = True):
  response = { 'error' : True }
  while 'error' in response:
    response = {}
    if cancel:
      response = _wrappers[user['name']].cancel_orders(unit, user['key'], user['secret'])
      if 'error' in response:
        logger.error('unable to cancel orders for unit %s on exchange %s: %s', unit, user['name'], response['error'])
      else:
        logger.info('successfully deleted all orders for unit %s on exchange %s', unit, user['name'])
    if not 'error' in response:
      response = place(unit, 'bid', user['name'], user['key'], user['secret'], price)
      if not 'error' in response:
        response = place(unit, 'ask', user['name'], user['key'], user['secret'], price)
    if 'error' in response:
      _wrappers[user['name']].adjust(response['error'])
      logger.info('trying to adjust nonce of exchange %s to %d', user['name'], _wrappers[user['name']]._shift)

# register users
users = []
for user in userdata:
  ret = register(user[3], user[2].lower(), user[0])
  if ret['code'] != 0:
    logger.error("register: %s" % ret['message'])
  else:
    units = [ unit.lower() for unit in user[1].split(',') ]
    if len([unit for unit in units if not unit in _feeds]) > 0:
      logger.error("register: no feed available for unit %s" % unit)
    else:
      users.append({'address' : user[0], 'units' : units, 'name' : user[2].lower(), 'key' : user[3], 'secret' : user[4], 'nubot' : {}})

# submit liquiditys
try:
  ts = 0
  basestatus = get('status')
  price = get('price')
  newprice = None
  validations = basestatus['validations']
  sampling = min(45, basestatus['sampling'] + 1)
  efficiency = { user['key'] : [0,0] for user in users }
  logger.debug('starting liquidity propagation with sampling %d' % sampling)
  exchanges = get('exchanges')
  exchanges['time'] = time.time()

  # initialize walls
  for user in users:
    for unit in user['units']:
      reset(user, unit, price[unit])

  while True:
    ts = (ts % 30) + 60 / sampling
    curtime = time.time()
    for user in users:
      for unit in user['units']:
        # submit requests
        ret = submit(user['key'], user['name'], unit, user['secret'])
        if ret['code'] != 0:
          if ret['code'] == 11: # user not found, just register again
            register(user['key'], user['name'], user['address'])
          logger.error("submit: %s" % ret['message'])

    if ts >= 30:
      # check orders
      newprice = get('price')
      for unit in price:
        deviation = 1.0 - min(price[unit], newprice[unit]) / max(price[unit], newprice[unit])
        if deviation > 0.02:
          logger.info('Price of unit %s moved from %.8f to %.8f, will try to reset orders', unit, price[unit], newprice[unit])
          price[unit] = newprice[unit]
        for user in users:
          if unit in user['units']:
            reset(user, unit, price[unit], deviation > 0.02)
      # print some info
      status = get('status')
      passed = status['validations'] - validations
      validations = status['validations']
      if passed > 0:
        for user in users:
          stats = get(user['key'])
          units = {}
          rejects = 0
          missing = 0
          for unit in stats['units']:
            rejects += stats['units'][unit]['rejects']
            missing += stats['units'][unit]['missing']
            units[unit] = { 'bid' : sum([x[1] for x in stats['units'][unit]['bid']]),
                            'ask' : sum([x[1] for x in stats['units'][unit]['ask']]),
                            'last_error' : stats['units'][unit]['last_error'] }
          if validations - basestatus['validations'] > status['sampling']: # do not adjust in initial phase
            if missing - efficiency[user['key']][0] > passed / 5:
              if sampling < 45: # just send more requests
                sampling += 1
                logger.warning('too many missing requests, adjusting sampling to %d', sampling)
              else: # just wait a little bit
                time.sleep(0.7)
                logger.warning('too many missing requests, sleeping a short while')
            if rejects - efficiency[user['key']][1] > passed / 5:
              _wrappers[stats['name']].adjust(stats['units'][unit]['last_error'])
              logger.warning('too many rejected requests on exchange %s, trying to adjust nonce of exchange to %d', stats['name'], _wrappers[stats['name']]._shift)
          newmissing = missing - efficiency[user['key']][0]
          newrejects = rejects - efficiency[user['key']][1]
          logger.info("%s: balance: %.8f exchange: %s rejects: %d missing: %d efficiency: %.2f%% units: %s" % (user['key'],
            stats['balance'], stats['name'], newrejects, newmissing, 100 * (1.0 - (newmissing + newrejects) / float(len(stats['units']) * passed)), units))
          efficiency[user['key']][0] = missing
          efficiency[user['key']][1] = rejects
    time.sleep(60 / sampling - (time.time() - curtime) / 1000)
except KeyboardInterrupt:
  pass

for user in users:
  for unit in user['units']:
    response = _wrappers[user['name']].cancel_orders(unit, user['key'], user['secret'])
    if 'error' in response:
      logger.error('unable to cancel orders for unit %s on exchange %s: %s', unit, user['name'], response['error'])
    else:
      logger.info('successfully deleted all orders for unit %s on exchange %s', unit, user['name'])