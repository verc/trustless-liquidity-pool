import sys
import json
import hmac
import time
import urllib
import urllib2
import hashlib
import httplib
import datetime

class Exchange(object):
  def __init__(self, domain):
    self._domain = domain
    self._shift = 1

  def adjust(self, error = None):
    self._shift = ((self._shift + 7) % 200) - 100 # -92 15 -78 29 -64 43 -50 57 ...

class Poloniex(Exchange):
  def __init__(self):
    super(Poloniex, self).__init__('poloniex.com/tradingApi')
    self._shift = 1
    self._nonce = 0

  def adjust(self, error):
    if error[:5] == 'Nonce': # Nonce must be greater than 1426131710000. You provided 1426032513010. (TODO: regex)
      error = error.replace('.', '').split()
      self._shift += 1 + (int(error[5]) - int(error[8])) / 1000
    else:
      self._shift = (self._shift + 10) % 1800

  def post(self, method, params, key, secret):
    request = { 'nonce' : int(time.time() + self._shift) * 1000, 'command' : method }
    if self._nonce >= request['nonce']:
      request['nonce'] += 1
    self._nonce = request['nonce']
    request.update(params)
    data = urllib.urlencode(request)
    sign = hmac.new(secret, data, hashlib.sha512).hexdigest()
    headers = { 'Sign' : sign, 'Key' : key }
    return json.loads(urllib2.urlopen(urllib2.Request('https://poloniex.com/tradingApi', data, headers)).read())

  def cancel_orders(self, unit, key, secret):
    response = self.post('returnOpenOrders', {'currencyPair' : "%s_NBT"%unit.upper()}, key, secret)
    if 'error' in response: return response
    for order in response:
      ret = self.post('cancelOrder', { 'currencyPair' : "%s_NBT"%unit.upper(), 'orderNumber' : order['orderNumber'] }, key, secret)
      if 'error' in ret:
        if isinstance(response,list): response = { 'error': "" }
        response['error'] += "," + ret['error']
    return response

  def place_order(self, unit, side, key, secret, amount, price):
    params = { 'currencyPair' : "%s_NBT"%unit.upper(), "rate" : price, "amount" : amount }
    response = self.post('buy' if side == 'bid' else 'sell', params, key, secret)
    if not 'error' in response:
      response['id'] = response['orderNumber']
    return response

  def get_balance(self, unit, key, secret):
    response = self.post('returnBalances', {}, key, secret)
    if not 'error' in response:
      response['balance'] = float(response[unit.upper()])
    return response

  def create_request(self, unit, key = None, secret = None):
    if not secret: return None, None
    request = { 'command' : 'returnOpenOrders', 'nonce' : int(time.time() + self._shift) * 1000,  'currencyPair' : "%s_NBT"%unit.upper()}
    data = urllib.urlencode(request)
    sign = hmac.new(secret, data, hashlib.sha512).hexdigest()
    return request, sign

  def validate_request(self, key, unit, data, sign):
    headers = { 'Sign' : sign, 'Key' : key }
    ret = urllib2.urlopen(urllib2.Request('https://poloniex.com/tradingApi', urllib.urlencode(data), headers))
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
    super(CCEDK, self).__init__('www.ccedk.com')
    self.pair_id = {}
    self.currency_id = {}
    while not self.pair_id or not self.currency_id:
      try:
        if not self.pair_id:
          response = json.loads(urllib2.urlopen(urllib2.Request(
            'https://www.ccedk.com/api/v1/stats/marketdepthfull?' + urllib.urlencode({ 'nonce' : int(time.time() + self._shift) }))).read())
          for unit in response['response']['entities']:
            if unit['pair_name'][:4] == 'NBT/':
              self.pair_id[unit['pair_name'][4:]] = unit['pair_id']
        if not self.currency_id:
          response = json.loads(urllib2.urlopen(urllib2.Request(
            'https://www.ccedk.com/api/v1/currency/list?' + urllib.urlencode({ 'nonce' : int(time.time() + self._shift) }))).read())
          for unit in response['response']['entities']:
            self.currency_id[unit['iso'].lower()] = unit['currency_id']
      except TypeError:
        self.adjust(",".join(response['errors'].values()))
        print >> sys.stderr, "could not retrieve ccedk ids, will adjust shift to", self._shift
        time.sleep(1)

  def adjust(self, error):
    if error[:15] == 'incorrect range': # Nonce must be greater than 1426131710000. You provided 1426032513010. (TODO: regex)
      minimum = int(error.strip().split()[-3].replace('`', ''))
      maximum = int(error.strip().split()[-1].replace('`', ''))
      current = int(error.split()[2].split('`')[3])
      if maximum == current:
        super(CCEDK, self).adjust(error)
      else:
        if current < maximum:
          self._shift += maximum - current
        else:
          self._shift += minimum - current
    else:
      super(CCEDK, self).adjust(error)

  def post(self, method, params, key, secret):
    request = { 'nonce' : int(time.time()  + self._shift) }
    request.update(params)
    data = urllib.urlencode(request)
    sign = hmac.new(secret, data, hashlib.sha512).hexdigest()
    headers = {"Content-type": "application/x-www-form-urlencoded", "Key": key, "Sign": sign}
    response = json.loads(urllib2.urlopen(urllib2.Request('https://www.ccedk.com/api/v1/' + method, data, headers)).read())
    if not response['response']:
      response['error'] = ",".join(response['errors'].values())
    return response

  def cancel_orders(self, unit, key, secret):
    response = self.post('order/list', {}, key, secret)
    if not response['response'] or not response['response']['entities']:
      return response
    for order in response['response']['entities']:
      if order['pair_id'] == self.pair_id[unit.upper()]:
        ret = self.post('order/cancel', { 'order_id' : order['order_id'] }, key, secret)
        if not ret['response']:
          if not response['error']: response['error'] = ""
          response['error'] += ",".join(ret['errors'].values())
    return response

  def place_order(self, unit, side, key, secret, amount, price):
    params = { "type" : 'buy' if side == 'bid' else 'sell',
               "price" : price,
               "pair_id" : int(self.pair_id[unit.upper()]),
               "volume" : amount }
    response = self.post('order/new', params, key, secret)
    if not 'error' in response:
      response['id'] = response['response']['entity']['order_id']
    return response

  def get_balance(self, unit, key, secret):
    params = { "currency_id" : self.currency_id[unit.lower()] }
    response = self.post('balance/info', params, key, secret)
    if not 'error' in response:
      response['balance'] = float(response['response']['entity']['balance'])
    return response

  def create_request(self, unit, key = None, secret = None):
    if not secret: return None, None
    request = { 'nonce' : int(time.time()  + self._shift) }
    data = urllib.urlencode(request)
    sign = hmac.new(secret, data, hashlib.sha512).hexdigest()
    return request, sign

  def validate_request(self, key, unit, data, sign):
    headers = {"Content-type": "application/x-www-form-urlencoded", "Key": key, "Sign": sign}
    response = json.loads(urllib2.urlopen(urllib2.Request('https://www.ccedk.com/api/v1/order/list', urllib.urlencode(data), headers)).read())
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
    super(BitcoinCoId, self).__init__('vip.bitcoin.co.id/tapi')

  def create_request(self, unit, key = None, secret = None):
    if not secret: return None, None
    request = { 'nonce' : int(time.time()  + self._shift) * 1000, 'pair' : 'nbt_' + unit.lower(), 'method' : 'openOrders' }
    data = urllib.urlencode(request)
    sign = hmac.new(secret, data, hashlib.sha512).hexdigest()
    return request, sign

  def validate_request(self, key, unit, data, sign):
    headers = {"Key": key, "Sign": sign}
    response = json.loads(urllib2.urlopen(urllib2.Request('https://vip.bitcoin.co.id/tapi', urllib.urlencode(data), headers)).read())
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