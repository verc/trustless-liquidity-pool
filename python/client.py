#! /usr/bin/env python

import sys
import time
import json
from exchanges import *

try:
  _login = json.loads(open('secrets.json').read())
except:
  print "secrets.json could not be read"
  sys.exit(1)

_wrappers = { 'poloniex' : Poloniex() }

def register(key, address):
  connection = httplib.HTTPConnection('127.0.0.1:2019', timeout=60)
  headers = { "Content-type": "application/x-www-form-urlencoded" }
  connection.request('POST', 'register', urllib.urlencode({'address' : address, 'key' : key}), headers = headers)
  connection.getresponse()

def submit(key, secret, name, unit):
  data, sign = _wrappers[name].create_request(unit, secret)
  params = {
    'name' : name,
    'unit' : unit,
    'user' : key,
    'sign' : sign
  }
  params.update(data)
  connection = httplib.HTTPConnection('127.0.0.1:2019', timeout=60)
  headers = { "Content-type": "application/x-www-form-urlencoded" }
  connection.request('POST', 'liquidity', urllib.urlencode(params), headers = headers)
  connection.getresponse()

address = 'BQRKF4X8gFLyHGVeym8tuff57PcJczYPVg'
exchange = 'poloniex'
unit = 'BTC'

key = _login[exchange]['key'].encode('utf-8')
secret = _login[exchange]['secret'].encode('utf-8')
register(key, address)
while True:
  submit(key, secret, exchange, unit)
  try:
    time.sleep(3)
  except KeyboardInterrupt:
    break