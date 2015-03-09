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

  def get(self, method, params = None, headers = None):
    if not params: params = ''
    if not headers: headers = {}
    connection = httplib.HTTPSConnection(self._domain, timeout=60)
    connection.request('GET', method, urllib.urlencode(params), headers = headers)
    response = connection.getresponse().read()
    try:
      return json.loads(response)
    except:
      print >> sys.stderr, "response cannot be converted into json format:", response
      raise

  def post(self, method, params, headers = None):
    if not headers: headers = {}
    connection = httplib.HTTPSConnection(self._domain, timeout=60)
    connection.request('POST', method, urllib.urlencode(params), headers = headers)
    response = connection.getresponse().read()
    try:
      return json.loads(response)
    except:
      print >> sys.stderr, "response cannot be converted into json format:", response
      raise


class Poloniex(Exchange):
  def __init__(self):
    super(Poloniex, self).__init__('poloniex.com/tradingApi')

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
    self._id = {}
    while not self._id:
      try:
        markets = json.loads(urllib2.urlopen(urllib2.Request(
          'https://www.ccedk.com/api/v1/stats/marketdepthfull?' + urllib.urlencode({ 'nonce' : int(time.time() + self._shift) }))).read())
        for unit in markets['response']['entities']:
          if unit['pair_name'][:4] == 'NBT/':
            self._id[unit['pair_name'][4:]] = unit['pair_id']
      except TypeError:
        self._shift = ((self._shift + 3) % 20) - 10 # -6 7 0 -7 6 -1 -8 5 -2 -9 4 -3 -10 3 -4 9 2 -5 8 1
        print >> sys.stderr, "could not retrieve ccedk pair ids, will adjust shift to", self._shift
        time.sleep(1)

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
      } for order in response['response']['entities'] if order['pair_id'] == self._id[unit.upper()]]


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
      'amount' : float(order['remain_idr']),
      } for order in response['return']['orders']]