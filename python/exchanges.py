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

import sys
import json
import hmac
import time
import urllib
import urllib2
import random
import hashlib
import httplib
import threading
import datetime

class Exchange(object):
  def __init__(self, fee):
    self.fee = fee
    self._shift = 1
    self._nonce = 0

  def adjust(self, error):
    if not 'exception caught:' in error:
      self._shift = ((self._shift + 7) % 200) - 100 # -92 15 -78 29 -64 43 -50 57 ...

  def nonce(self, factor = 1000.0):
    n = int((time.time() + self._shift) * float(factor))
    if self._nonce >= n:
      n = self._nonce + 10
    self._nonce = n
    return n


class Bittrex(Exchange):
  def __init__(self):
    super(Bittrex, self).__init__(0.0025)
    self.placed = {}
    self.closed = []

  def __repr__(self): return "bittrex"

  def adjust(self, error):
    pass

  def post(self, method, params, key, secret, throttle = 5):
    data = 'https://bittrex.com/api/v1.1' + method + '?apikey=%s&nonce=%d&' % (key, self.nonce()) + urllib.urlencode(params)
    sign = hmac.new(secret, data, hashlib.sha512).hexdigest()
    headers = { 'apisign' : sign }
    connection = httplib.HTTPSConnection('bittrex.com', timeout = 10)
    connection.request('GET', data, headers = headers)
    response = json.loads(connection.getresponse().read())
    if throttle > 0 and not response['success'] and 'THROTTLED' in response['message']:
      time.sleep(2)
      return self.post(method, params, key, secret, throttle - 1)
    return response

  def get(self, method, params):
    data = 'https://bittrex.com/api/v1.1' + method + '?' + urllib.urlencode(params)
    connection = httplib.HTTPSConnection('bittrex.com', timeout = 10)
    connection.request('GET', data, headers = {})
    return json.loads(connection.getresponse().read())

  def cancel_orders(self, unit, side, key, secret):
    response = self.post('/market/getopenorders', { 'market' : "%s-NBT"%unit.upper() }, key, secret)
    if not response['success']:
      response['error'] = response['message']
      return response
    if not response['result']:
      response['result'] = []
    response['removed'] = []
    response['amount'] = 0.0
    for order in response['result']:
      if side == 'all' or (side == 'bid' and 'BUY' in order['OrderType']) or (side == 'ask' and 'SELL' in order['OrderType']):
        ret = self.post('/market/cancel', { 'uuid' : order['OrderUuid'] }, key, secret)
        if not ret['success'] and ret['message'] != "ORDER_NOT_OPEN":
          if not 'error' in response: response = { 'error': "" }
          response['error'] += "," + ret['message']
        else:
          response['removed'].append(order['OrderUuid'])
          response['amount'] += order['Quantity']
    if not 'error' in response and key in self.placed and unit in self.placed[key]:
      if side == 'all':
        self.placed[key][unit]['bid'] = False
        self.placed[key][unit]['ask'] = False
      else:
        self.placed[key][unit][side] = False
    return response

  def place_order(self, unit, side, key, secret, amount, price):
    ret = self.cancel_orders(unit, side, key, secret)
    if 'error' in ret: return ret
    amount += ret['amount']
    if side == 'bid':
      amount *= (1.0 - self.fee)
    params = { 'market' : "%s-NBT"%unit.upper(), "rate" : price, "quantity" : amount }
    response = self.post('/market/buylimit' if side == 'bid' else '/market/selllimit', params, key, secret)
    if response['success']:
      response['id'] = response['result']['uuid']
      if not key in self.placed:
        self.placed[key] = {}
      if not unit in self.placed[key]:
        self.placed[key][unit] = { 'bid' : False, 'ask' : False }
      self.placed[key][unit][side] = response['id']
    else:
      response['error'] = response['message']
      response['residual'] = ret['amount']
    return response

  def get_balance(self, unit, key, secret):
    response = self.post('/account/getbalance', {'currency' : unit.upper()}, key, secret)
    if response['success']:
      try:
        response['balance'] = float(response['result']['Available'])
      except:
        response['balance'] = 0.0
    else:
      response['error'] = response['message']
    return response

  def get_price(self, unit):
    response = self.get('/public/getticker', {'market' : '%s-NBT' % unit})
    if response['success']:
      response.update({'bid': response['result']['Bid'], 'ask': response['result']['Ask']})
    else:
      response['error'] = response['message']
    return response

  def create_request(self, unit, key = None, secret = None):
    if not secret or not key:
      return None, None
    uuids = []
    if key in self.placed and unit in self.placed[key]:
      if self.placed[key][unit]['bid']:
        uuids.append(self.placed[key][unit]['bid'])
      if self.placed[key][unit]['ask']:
        uuids.append(self.placed[key][unit]['ask'])
    requests = []
    signatures = []
    for uuid in uuids:
      data = 'https://bittrex.com/api/v1.1/account/getorder?apikey=%s&nonce=%d&uuid=%s' % (key, self.nonce(), uuid)
      requests.append(data)
      signatures.append(hmac.new(secret, data, hashlib.sha512).hexdigest())
    return { 'requests' : json.dumps(requests), 'signs' : json.dumps(signatures) }, None

  def validate_request(self, key, unit, data, signs):
    orders = []
    last_error = ""
    requests = json.loads(data['requests'])
    signs = json.loads(data['signs'])
    if len(requests) != len(signs):
      return { 'error' : 'missmatch between requests and signatures (%d vs %d)' % (len(data['requests']), len(signs)) }
    if len(requests) > 2:
      return { 'error' : 'too many requests received: %d' % len(requests) }
    connection = httplib.HTTPSConnection('bittrex.com', timeout = 5)
    for data, sign in zip(requests, signs):
      uuid = data.split('=')[-1]
      if not uuid in self.closed:
        headers = { 'apisign' : sign }
        connection.request('GET', data, headers = headers)
        response = json.loads(connection.getresponse().read())
        if response['success']:
          try: opened = int(datetime.datetime.strptime(response['result']['Opened'], '%Y-%m-%dT%H:%M:%S.%f').strftime("%s"))
          except: opened = 0
          try: closed = int(datetime.datetime.strptime(response['result']['Closed'], '%Y-%m-%dT%H:%M:%S.%f').strftime("%s"))
          except: closed = sys.maxint
          if closed < time.time() - 60:
            self.closed.append(uuid)
          orders.append({
            'id' : response['result']['OrderUuid'],
            'price' : response['result']['Limit'],
            'type' : 'ask' if 'SELL' in response['result']['Type'] else 'bid',
            'amount' : response['result']['QuantityRemaining'], # if not closed == sys.maxint else response['result']['Quantity'],
            'opened' : opened,
            'closed' : closed,
            })
        else:
          last_error = response['message']
    if not orders and last_error != "":
      return { 'error' : last_error }
    return orders


class Poloniex(Exchange):
  def __init__(self):
    super(Poloniex, self).__init__(0.002)

  def __repr__(self): return "poloniex"

  def adjust(self, error):
    if "Nonce must be greater than" in error: # (TODO: regex)
      if ':' in error: error = error.split(':')[1].strip()
      error = error.replace('.', '').split()
      self._shift += 100.0 + (int(error[5]) - int(error[8])) / 1000.0
    else:
      self._shift = self._shift + 100.0

  def post(self, method, params, key, secret):
    request = { 'nonce' : self.nonce(), 'command' : method }
    request.update(params)
    data = urllib.urlencode(request)
    sign = hmac.new(secret, data, hashlib.sha512).hexdigest()
    headers = { 'Sign' : sign, 'Key' : key }
    return json.loads(urllib2.urlopen(urllib2.Request('https://poloniex.com/tradingApi', data, headers)).read())

  def cancel_orders(self, unit, side, key, secret):
    response = self.post('returnOpenOrders', { 'currencyPair' : "%s_NBT"%unit.upper() }, key, secret)
    if 'error' in response: return response
    for order in response:
      if side == 'all' or (side == 'bid' and order['type'] == 'buy') or (side == 'ask' and order['type'] == 'sell'):
        ret = self.post('cancelOrder', { 'currencyPair' : "%s_NBT"%unit.upper(), 'orderNumber' : order['orderNumber'] }, key, secret)
        if 'error' in ret:
          if isinstance(response,list): response = { 'error': "" }
          response['error'] += "," + ret['error']
    return response

  def place_order(self, unit, side, key, secret, amount, price):
    params = { 'currencyPair' : "%s_NBT"%unit.upper(), "rate" : price, "amount" : amount }
    response = self.post('buy' if side == 'bid' else 'sell', params, key, secret)
    if not 'error' in response:
      response['id'] = int(response['orderNumber'])
    return response

  def get_balance(self, unit, key, secret):
    response = self.post('returnBalances', {}, key, secret)
    if not 'error' in response:
      response['balance'] = float(response[unit.upper()])
    return response

  def get_price(self, unit):
    response = json.loads(urllib2.urlopen('https://poloniex.com/public?' + 
      urllib.urlencode({'command' : 'returnOrderBook', 'currencyPair' : "%s_NBT"%unit.upper(), 'depth' : 1}), timeout = 5).read())
    if not 'error' in response:
      response.update({'bid': None, 'ask': None})
      if response['bid']: response['bid'] = float(response['bid'][0])
      if response['ask']: response['ask'] = float(response['ask'][0])
    return response

  def create_request(self, unit, key = None, secret = None):
    if not secret: return None, None
    request = { 'command' : 'returnOpenOrders', 'nonce' : self.nonce(),  'currencyPair' : "%s_NBT"%unit.upper() }
    data = urllib.urlencode(request)
    sign = hmac.new(secret, data, hashlib.sha512).hexdigest()
    return request, sign

  def validate_request(self, key, unit, data, sign):
    headers = { 'Sign' : sign, 'Key' : key }
    ret = urllib2.urlopen(urllib2.Request('https://poloniex.com/tradingApi', urllib.urlencode(data), headers), timeout = 5)
    response = json.loads(ret.read())
    if 'error' in response: return response
    return [ {
      'id' : int(order['orderNumber']),
      'price' : float(order['rate']),
      'type' : 'ask' if order['type'] == 'sell' else 'bid',
      'amount' : float(order['amount']),
      } for order in response ]


class CCEDK(Exchange):
  def __init__(self):
    super(CCEDK, self).__init__(0.002)
    self.pair_id = {}
    self.currency_id = {}
    failed = False
    while not self.pair_id or not self.currency_id:
      try:
        response = None
        if not self.pair_id:
          response = json.loads(urllib2.urlopen(urllib2.Request(
            'https://www.ccedk.com/api/v1/stats/marketdepthfull?' + urllib.urlencode({ 'nonce' : self.nonce() }))).read(), timeout = 15)
          for unit in response['response']['entities']:
            if unit['pair_name'][:4] == 'NBT/':
              self.pair_id[unit['pair_name'][4:]] = unit['pair_id']
        if not self.currency_id:
          response = json.loads(urllib2.urlopen(urllib2.Request(
            'https://www.ccedk.com/api/v1/currency/list?' + urllib.urlencode({ 'nonce' : self.nonce() }))).read(), timeout = 15)
          for unit in response['response']['entities']:
            self.currency_id[unit['iso'].lower()] = unit['currency_id']
      except:
        if response and not response['response']:
          self.adjust(",".join(response['errors'].values()))
          if failed: print >> sys.stderr, "could not retrieve ccedk ids, will adjust shift to", self._shift, "reason:", ",".join(response['errors'].values())
        else:
          print >> sys.stderr, "could not retrieve ccedk ids, server is unreachable"
        failed = True
        time.sleep(1)

  def __repr__(self): return "ccedk"

  def nonce(self, factor = 1.0):
    n = int(time.time() + self._shift)
    if n == self._nonce:
      n = self._nonce + 1
    self._nonce = n
    return n

  def adjust(self, error):
    if "incorrect range" in error: #(TODO: regex)
      if ':' in error: error = error.split(':')[1].strip()
      try:
        minimum = int(error.strip().split()[-3].replace('`', ''))
        maximum = int(error.strip().split()[-1].replace('`', ''))
        current = int(error.strip().split()[-7].split('`')[3])
      except:
        self._shift += random.randrange(-10, 10)
      else:
        if current < maximum:
          newshift = (minimum + 2 * maximum) / 3  - current
          if newshift < 0: newshift = 10
        else:
          newshift = (2 * minimum + maximum) / 3 - current
        if newshift != 0:
          self._shift += newshift
        else:
          self._shift += random.randrange(-10, 10)
    else:
      self._shift += random.randrange(-10, 10)

  def post(self, method, params, key, secret):
    request = { 'nonce' : self.nonce() } # TODO: check for unique nonce
    request.update(params)
    data = urllib.urlencode(request)
    sign = hmac.new(secret, data, hashlib.sha512).hexdigest()
    headers = {"Content-type": "application/x-www-form-urlencoded", "Key": key, "Sign": sign}
    response = json.loads(urllib2.urlopen(urllib2.Request('https://www.ccedk.com/api/v1/' + method, data, headers)).read())
    if not response['response']:
      response['error'] = ",".join(response['errors'].values())
    return response

  def cancel_orders(self, unit, side, key, secret):
    response = self.post('order/list', {}, key, secret)
    if not response['response'] or not response['response']['entities']:
      return response
    for order in response['response']['entities']:
      if side == 'all' or (side == 'bid' and order['type'] == 'buy') or (side == 'ask' and order['type'] == 'sell'):
        if order['pair_id'] == self.pair_id[unit.upper()]:
          ret = self.post('order/cancel', { 'order_id' : order['order_id'] }, key, secret)
          if not ret['response']:
            if not 'error' in response: response['error'] = ""
            response['error'] += ",".join(ret['errors'].values())
    return response

  def place_order(self, unit, side, key, secret, amount, price):
    params = { "type" : 'buy' if side == 'bid' else 'sell',
               "price" : price,
               "pair_id" : int(self.pair_id[unit.upper()]),
               "volume" : amount }
    response = self.post('order/new', params, key, secret)
    if not 'error' in response:
      response['id'] = int(response['response']['entity']['order_id'])
    return response

  def get_balance(self, unit, key, secret):
    params = { "currency_id" : self.currency_id[unit.lower()] }
    response = self.post('balance/info', params, key, secret)
    if not 'error' in response:
      response['balance'] = float(response['response']['entity']['balance'])
    return response

  def get_price(self, unit):
    response = json.loads(urllib2.urlopen(urllib2.Request(
        'https://www.ccedk.com/api/v1/orderbook/info?' + urllib.urlencode({ 'nonce' : self.nonce(), 'pair_id' : self.pair_id[unit.upper()] })), timeout = 5).read())
    if not response['response']:
      response['error'] = ",".join(response['errors'].values())
      return response
    response.update({'bid': None, 'ask': None})
    if response['response']['entity']['bids']: response['bid'] = float(response['response']['entity']['bids'][0]['price'])
    if response['response']['entity']['asks']: response['ask'] = float(response['response']['entity']['asks'][0]['price'])
    return response

  def create_request(self, unit, key = None, secret = None):
    if not secret: return None, None
    request = { 'nonce' : self.nonce() }
    data = urllib.urlencode(request)
    sign = hmac.new(secret, data, hashlib.sha512).hexdigest()
    return request, sign

  def validate_request(self, key, unit, data, sign):
    headers = {"Content-type": "application/x-www-form-urlencoded", "Key": key, "Sign": sign}
    response = json.loads(urllib2.urlopen(urllib2.Request('https://www.ccedk.com/api/v1/order/list', urllib.urlencode(data), headers), timeout = 5).read())
    if not response['response']:
      response['error'] = ",".join(response['errors'].values())
      return response
    if not response['response']['entities']:
      response['response']['entities'] = []
    return [ {
      'id' : int(order['order_id']),
      'price' : float(order['price']),
      'type' : 'ask' if order['type'] == 'sell' else 'bid',
      'amount' : float(order['volume']),
      } for order in response['response']['entities'] if order['pair_id'] == self.pair_id[unit.upper()]]


class BitcoinCoId(Exchange):
  def __init__(self):
    super(BitcoinCoId, self).__init__(0.0)
    try:
      ping = time.time()
      response = json.loads(urllib2.urlopen(urllib2.Request('https://vip.bitcoin.co.id/api/summaries')).read())
      self._shift = float(response['tickers']['btc_idr']['server_time']) - ping
    except:
      pass

  def __repr__(self): return "bitcoincoid"

  def adjust(self, error):
    if "Nonce must be greater than" in error: # (TODO: regex)
      if ':' in error: error = error.split(':')[1].strip()
      error = error.replace('.', '').split()
      self._shift += 100.0 + (int(error[5]) - int(error[8])) / 1000.0
    else:
      self._shift = self._shift + 100.0

  def nonce(self, factor = 1000.0):
    n = int((time.time() + self._shift) * float(factor))
    if n - self._nonce < 300:
      n = self._nonce + 300
    self._nonce = n
    return n

  def post(self, method, params, key, secret):
    request = { 'nonce' : self.nonce(), 'method' : method }
    request.update(params)
    data = urllib.urlencode(request)
    sign = hmac.new(secret, data, hashlib.sha512).hexdigest()
    headers = { 'Sign' : sign, 'Key' : key }
    response = json.loads(urllib2.urlopen(urllib2.Request('https://vip.bitcoin.co.id/tapi', data, headers)).read())
    return response

  def cancel_orders(self, unit, side, key, secret):
    response = self.post('openOrders', {'pair' : 'nbt_' + unit.lower()}, key, secret)
    if response['success'] == 0 or not response['return']['orders']: return response
    for order in response['return']['orders']:
      if side == 'all' or (side == 'bid' and order['type'] == 'buy') or (side == 'ask' and order['type'] == 'sell'):
        params = { 'pair' : 'nbt_' + unit.lower(), 'order_id' : order['order_id'], 'type' :  order['type'] }
        ret = self.post('cancelOrder', params, key, secret)
        if 'error' in ret:
          if not 'error' in response: response['error'] = ""
          response['error'] += "," + ret['error']
    return response

  def place_order(self, unit, side, key, secret, amount, price):
    params = { 'pair' : 'nbt_' + unit.lower(), 'type' : 'buy' if side == 'bid' else 'sell', 'price' : price }
    if side == 'bid':
      params[unit.lower()] = amount * price
    else:
      params['nbt'] = amount
      params[unit] = amount * price
    response = self.post('trade', params, key, secret)
    if response['success'] == 1:
      response['id'] = int(response['return']['order_id'])
    return response

  def get_balance(self, unit, key, secret):
    response = self.post('getInfo', {}, key, secret)
    if response['success'] == 1:
      response['balance'] = float(response['return']['balance'][unit.lower()])
    return response

  def get_price(self, unit):
    response = json.loads(urllib2.urlopen(urllib2.Request('https://vip.bitcoin.co.id/api/nbt_%s/depth' % unit.lower()), timeout = 5).read())
    if 'error' in response:
      return response
    response.update({'bid': None, 'ask': None})
    if response['buy']: response['bid'] = float(response['buy'][0][0])
    if response['sell']: response['ask'] = float(response['sell'][0][0])
    return response

  def create_request(self, unit, key = None, secret = None):
    if not secret: return None, None
    request = { 'nonce' : self.nonce(), 'pair' : 'nbt_' + unit.lower(), 'method' : 'openOrders' }
    data = urllib.urlencode(request)
    sign = hmac.new(secret, data, hashlib.sha512).hexdigest()
    return request, sign

  def validate_request(self, key, unit, data, sign):
    headers = {"Key": key, "Sign": sign}
    response = json.loads(urllib2.urlopen(urllib2.Request('https://vip.bitcoin.co.id/tapi', urllib.urlencode(data), headers), timeout = 5).read())
    if response['success'] == 0:
      return response
    if not response['return']['orders']:
      response['return']['orders'] = []
    return [ {
      'id' : int(order['order_id']),
      'price' : float(order['price']),
      'type' : 'ask' if order['type'] == 'sell' else 'bid',
      'amount' : float(order['remain_' + (unit.lower() if order['type'] == 'buy' else 'nbt')]) / (float(order['price']) if order['type'] == 'buy' else 1.0),
      } for order in response['return']['orders']]


class BTER(Exchange):
  def __init__(self):
    super(BTER, self).__init__(0.002)

  def __repr__(self): return "bter"

  def adjust(self, error):
    pass

  def https_request(self, method, params, headers = None, timeout = None):
    if not headers: headers = {}
    connection = httplib.HTTPSConnection('data.bter.com', timeout = timeout)
    connection.request('POST', '/api/1/private/' + method, params, headers)
    response = connection.getresponse().read()
    return json.loads(response)

  def post(self, method, params, key, secret):
    data = urllib.urlencode(params)
    sign = hmac.new(secret, data, hashlib.sha512).hexdigest()
    headers = { 'Sign' : sign, 'Key' : key, "Content-type": "application/x-www-form-urlencoded" }
    return self.https_request(method, data, headers)

  def cancel_orders(self, unit, side, key, secret):
    response = self.post('orderlist', {}, key, secret)
    if not response['result']:
      response['error'] = response['msg']
      return response
    if not response['orders']: response['orders'] = []
    for order in response['orders']:
      if side == 'all' or (side == 'ask' and order['sell_type'] != unit) or (side == 'bid' and order['buy_type'] != unit):
        if order['pair'] == 'nbt_' + unit.lower():
          params = { 'order_id' : order['oid'] }
          ret = self.post('cancelorder', params, key, secret)
          if not ret['result']:
            if not 'error' in response: response['error'] = ""
            response['error'] += "," + ret['msg']
    return response

  def place_order(self, unit, side, key, secret, amount, price):
    params = { 'pair' : 'nbt_' + unit.lower(), 'type' : 'buy' if side == 'bid' else 'sell', 'rate' : price, 'amount' : amount }
    response = self.post('placeorder', params, key, secret)
    if response['result']:
      response['id'] = int(response['order_id'])
    else:
      response['error'] = response['msg']
    return response

  def get_balance(self, unit, key, secret):
    response = self.post('getfunds', {}, key, secret)
    if response['result']:
      if unit.upper() in response['available_funds']:
        response['balance'] = float(response['available_funds'][unit.upper()])
      else:
        response['balance'] = 0.0
    else: response['error'] = response['msg']
    return response

  def get_price(self, unit):
    connection = httplib.HTTPSConnection('data.bter.com', timeout = 5)
    connection.request('GET', '/api/1/depth/nbt_' + unit.lower())
    response = json.loads(connection.getresponse().read())
    if not 'result' in response or not response['result']:
      response['error'] = response['msg'] if 'msg' in response else 'invalid response: %s' % str(response)
      return response
    response.update({'bid': None, 'ask': None})
    if response['bids']: response['bid'] = float(response['bids'][0][0])
    if response['asks']: response['ask'] = float(response['asks'][-1][0])
    return response

  def create_request(self, unit, key = None, secret = None):
    if not secret: return None, None
    request = {} # no nonce required
    data = urllib.urlencode(request)
    sign = hmac.new(secret, data, hashlib.sha512).hexdigest()
    return request, sign

  def validate_request(self, key, unit, data, sign):
    headers = { 'Sign' : sign, 'Key' : key, "Content-type": "application/x-www-form-urlencoded" }
    response = self.https_request('orderlist', urllib.urlencode(data), headers, timeout = 15)
    if not 'result' in response or not response['result']:
      response['error'] = response['msg'] if 'msg' in response else 'invalid response: %s' % str(response)
      return response
    if not response['orders']:
      response['orders'] = []
    return [ {
      'id' : int(order['oid']),
      'price' : float(order['rate']),
      'type' : 'ask' if order['buy_type'].lower() == unit.lower() else 'bid',
      'amount' : float(order['amount']) / (1.0 if order['buy_type'].lower() == unit.lower() else float(order['rate'])),
      } for order in response['orders'] if order['pair'] == 'nbt_' + unit.lower() ]


class Peatio(Exchange):
  def __init__(self):
    super(Peatio, self).__init__(0.002)

  def __repr__(self): return "testing"

  def adjust(self, error):
    if "is invalid, current timestamp is" in error:
      try:
        tonce = int(error.split()[2])
        times = int(error.split()[-1].replace('.', ''))
        self._shift = int(float(times - tonce)/1000.0)
      except:
        print error
        pass
    else: print error

  def urlencode(self, params): # from https://github.com/JohnnyZhao/peatio-client-python/blob/master/lib/auth.py#L11
    keys = sorted(params.keys())
    query = ''
    for key in keys:
      value = params[key]
      if key != "orders":
        query = "%s&%s=%s" % (query, key, value) if len(query) else "%s=%s" % (key, value)
      else:
        d = {key: params[key]}
        for v in value:
          ks = v.keys()
          ks.sort()
          for k in ks:
            item = "orders[][%s]=%s" % (k, v[k])
            query = "%s&%s" % (query, item) if len(query) else "%s" % item
    return query

  def query(self, qtype, method, params, key, secret):
    request = { 'tonce' : self.nonce(), 'access_key' : key }
    request.update(params)
    data = self.urlencode(request)
    msg = "%s|/api/v2/%s|%s" % (qtype, method, data)
    data += "&signature=" + hmac.new(secret, msg, hashlib.sha256).hexdigest()
    connection = httplib.HTTPSConnection('178.62.140.24', timeout = 5)
    connection.request(qtype, '/api/v2/' + method + '?' + data)
    return json.loads(connection.getresponse().read())

  def post(self, method, params, key, secret):
    return self.query('POST', method, params, key, secret)

  def get(self, method, params, key, secret):
    return self.query('GET', method, params, key, secret)

  def cancel_orders(self, unit, side, key, secret):
    response = self.get('orders.json', { 'market' : "nbt%s"%unit.lower() }, key, secret)
    if 'error' in response:
      response['error'] = response['error']['message']
      return response
    for order in response:
      if side == 'all' or (side == 'bid' and order['side'] == 'buy') or (side == 'ask' and order['side'] == 'sell'):
        ret = self.post('order/delete.json', { 'id' : order['id'] }, key, secret)
        if 'error' in ret:
          if isinstance(response,list): response = { 'error': "" }
          response['error'] += "," + ret['error']['message']
    return response

  def place_order(self, unit, side, key, secret, amount, price):
    params = { 'market' : "nbt%s"%unit.lower(), "side" : 'buy' if side == 'bid' else 'sell', "volume" : amount, "price" : price  }
    response = self.post('orders', params, key, secret)
    if 'error' in response:
      response['error'] = response['error']['message']
    else:
      response['id'] = int(response['id'])
    return response

  def get_balance(self, unit, key, secret):
    response = self.get('members/me.json', {}, key, secret)
    if 'error' in response:
      response['error'] = response['error']['message']
    else:
      response['balance'] = 0.0
      for pair in response['accounts']:
        if pair['currency'] == unit.lower():
          response['balance'] = float(pair['balance'])
    return response

  def get_price(self, unit):
    connection = httplib.HTTPSConnection('178.62.140.24', timeout = 15)
    connection.request('GET', '/api/v2/depth.json?' + self.urlencode({'market' : "nbt%s"%unit.lower(), 'limit' : 1}))
    response = json.loads(connection.getresponse().read())
    if 'error' in response:
      response['error'] = response['error']['message']
      return response
    response.update({'bid': None, 'ask': None})
    if response['bids']: response['bid'] = float(response['bids'][0][0])
    if response['asks']: response['ask'] = float(response['asks'][-1][0])
    return response

  def create_request(self, unit, key = None, secret = None):
    if not secret: return None, None
    request = { 'tonce' : self.nonce(), 'access_key' : key, 'market' : "nbt%s"%unit.lower() }
    data = self.urlencode(request)
    msg = "GET|/api/v2/orders.json|%s" % data
    request['signature'] = hmac.new(secret, msg, hashlib.sha256).hexdigest()
    return request, ''

  def validate_request(self, key, unit, data, sign):
    if not 'market' in data or data['market'] != "nbt%s"%unit.lower():
      return { 'error' : 'invalid market' }
    connection = httplib.HTTPSConnection('178.62.140.24', timeout = 15)
    connection.request('GET', '/api/v2/orders.json?' + self.urlencode(data))
    response = json.loads(connection.getresponse().read())
    if 'error' in response:
      response['error'] = response['error']['message']
      return response
    return [ {
      'id' : int(order['id']),
      'price' : float(order['price']),
      'type' : 'ask' if order['side'] == 'sell' else 'bid',
      'amount' : float(order['remaining_volume']),
      } for order in response ]