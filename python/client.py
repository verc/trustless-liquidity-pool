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
_wrappers = { 'poloniex' : Poloniex(), 'ccedk' : CCEDK() }
_feeds = { 'btc' : {  'main-feed' : 'bitfinex',
                      'backup-feeds' : {  
                        'backup1' : { 'name' : 'blockchain' },
                        'backup2' : { 'name' : 'coinbase' },
                        'backup3' : { 'name' : 'bitstamp' }
                      } }, 'usd' : None }

def json_request(request, method, params, headers, callback):
  connection = httplib.HTTPConnection(_server, timeout=60)
  try:
    connection.request(request, method, urllib.urlencode(params), headers = headers)
    response = connection.getresponse()
    content = response.read()
    return json.loads(content)
  except httplib.BadStatusLine:
    logging.error("server could not be reached, retrying in 15 seconds ...")
  except ValueError:
    logging.error("server response invalid, retrying in 15 seconds ...")
    print content
  except socket.error:
    logging.error("socket error, retrying in 15 seconds ...")
  time.sleep(15)
  return callback(method, params)

def get(method, params = None):
  if not params: params = {}
  return json_request('GET', '/' + method, params, {}, get)

def post(method, params = None):
  if not params: params = {}
  headers = { "Content-type": "application/x-www-form-urlencoded" }
  return json_request('POST', method, params, headers, post)

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

# register users
users = []
for user in userdata:
  ret = register(user[3], user[2], user[0])
  if ret['code'] != 0:
    logger.error("register: %s" % ret['message'])
  else:
    units = [ unit.lower() for unit in user[1].split(',') ]
    if len([unit for unit in units if not unit in _feeds]) > 0:
      logger.error("register: no feed available for unit %s" % unit)
    else:
      users.append({'address' : user[0], 'units' : units, 'name' : user[2], 'key' : user[3], 'secret' : user[4], 'nubot' : {}})

# submit liquidity
try:
  ts = 0
  basestatus = get('status')
  validations = basestatus['validations']
  sampling = max(1, basestatus['sampling'] - 1)
  efficiency = { user['key'] : [0,0] for user in users }
  logger.debug('starting liquidity propagation with sampling %d' % sampling)
  while True:
    ts = (ts % 30) + 60 / sampling
    curtime = time.time()
    for user in users:
      for unit in user['units']:
        # submit requests
        ret = submit(user['key'], user['name'], unit, user['secret'])
        if ret['code'] != 0:
          logger.error("submit: %s" % ret['message'])
        # check if NuBot is alive
        if not unit in user['nubot'] or user['nubot'][unit].poll():
          logger.info("starting NuBot on exchange %s" % user['name'])
          options = {
            'exchangename' : user['name'],
            'apikey' : user['key'],
            'apisecret' : user['secret'],
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
            options['secondary-peg-options'] = {
              'wallshift-threshold' : 0.3,
              'spread' : 0
            }
            options['secondary-peg-options'].update(_feeds[unit])
          out = tempfile.NamedTemporaryFile(delete = False)
          out.write(json.dumps({ 'options' : options }))
          out.close()
          with open(os.devnull, 'w') as fp:
            user['nubot'][unit] = subprocess.Popen("java -jar NuBot.jar %s" % out.name,
              stdout=fp, stderr=fp, shell=True, preexec_fn=os.setsid, cwd = 'nubot')
    if ts >= 30: # print some info
      status = get('status')
      passed = status['validations'] - validations
      validations = status['validations']
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
                          'rejects' : stats['units'][unit]['rejects'],
                          'missing' : stats['units'][unit]['missing'],
                          'last_error' : stats['units'][unit]['last_error'] }
        if validations - basestatus['validations'] > status['sampling']: # do not adjust in initial phase
          if missing - efficiency[user['key']][0] > passed / 5:
            if sampling > 2: # just send more requests
              sampling -= 1
              logger.warning('too many missing requests, adjusting sampling to %d', sampling)
            else: # just wait a little bit
              time.sleep(0.7)
              logger.warning('too many missing requests, sleeping a short while')
          if rejects - efficiency[user['key']][1] > passed / 5:
            _wrappers[stats['name']]._shift = ((_wrappers[stats['name']]._shift + 3) % 20) - 10 # -6 7 0 -7 6 -1 -8 5 -2 -9 4 -3 -10 3 -4 9 2 -5 8 1
            logger.warning('too many rejected requests on exchange %s, trying to adjust nonce of exchange to %d', stats['name'], _wrappers[stats['name']]._shift)
        logger.info("%s: balance: %.8f exchange: %s efficiency: %.2f%% units: %s" % (user['key'],
          stats['balance'], stats['name'], 100 * (1.0 - ((missing - efficiency[user['key']][0]) + (rejects - efficiency[user['key']][1])) / float(len(stats['units']) * passed)), units))
        efficiency[user['key']][0] = missing
        efficiency[user['key']][1] = rejects
    time.sleep(60 / sampling - (time.time() - curtime) / 1000)
except KeyboardInterrupt:
  pass
for user in users:
  for unit in user['units']:
    if user['nubot'][unit]:
      logger.info("stopping NuBot on exchange %s" % user['name'])
      os.killpg(user['nubot'][unit].pid, signal.SIGTERM)