#! /usr/bin/env python

import os
import sys
import time
import json
import tempfile
import signal
import subprocess
import logging
from exchanges import *

if len(sys.argv) < 2:
  print "usage:", sys.argv[0], "server[:port] [users.dat]"
  sys.exit(1)

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
_wrappers = { 'poloniex' : Poloniex() }
_feeds = { 'btc' : {  'main-feed' : 'bitfinex',
                      'backup-feeds' : {  
                        'backup1' : { 'name' : 'blockchain' },
                        'backup2' : { 'name' : 'coinbase' },
                        'backup3' : { 'name' : 'bitstamp' }
                      } }, 'usd' : None }

def get(method):
  connection = httplib.HTTPConnection('127.0.0.1:2019', timeout=60)
  connection.request('GET', '/' + method)
  return json.loads(connection.getresponse().read())

def post(method, params):
  connection = httplib.HTTPConnection('127.0.0.1:2019', timeout=60)
  headers = { "Content-type": "application/x-www-form-urlencoded" }
  connection.request('POST', method, urllib.urlencode(params), headers = headers)
  return json.loads(connection.getresponse().read())

def register(key, name, address):
  return post('register', {'address' : address, 'key' : key, 'name' : name})

def submit(key, name, unit, secret):
  data, sign = _wrappers[name].create_request(unit, secret)
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
ts = 0
while True:
  ts = (ts % 60) + 3
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
  if ts == 60: # print some info
    for user in users:
      stats = get(user['key'])
      orders = {}
      for unit in stats['orders']:
        orders[unit] = { 'bid' : sum([x[1] for x in stats['orders'][unit]['bid']]),
                         'ask' : sum([x[1] for x in stats['orders'][unit]['ask']]) }
      logger.info("Balance: %.8f Exchange: %s Orders: %s" % (stats['balance'], stats['name'], orders))
  try: time.sleep(3) # send every 3 seconds
  except KeyboardInterrupt:
    for user in users:
      for unit in user['units']:
        os.killpg(user['nubot'][unit].pid, signal.SIGTERM) 
    break