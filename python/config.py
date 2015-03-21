import os

# pool configuration
_port = 2021
# daily interest rates
_interest = {
  'poloniex' : {
    'btc' : {
      'bid': {
        'rate' : 0.0025,
        'target' : 10.0
        },
      'ask': {
        'rate' : 0.0025,
        'target' : 10.0
        }
      }
  },
  'ccedk' : {
    'btc' : {
      'bid': {
        'rate' : 0.0025,
        'target' : 25.0
        },
      'ask': {
        'rate' : 0.0025,
        'target' : 25.0
        }
      }
  },
  'bitcoincoid' : {
    'btc' : {
      'bid': {
        'rate' : 0.0025,
        'target' : 25.0
        },
      'ask': {
        'rate' : 0.0025,
        'target' : 25.0
        }
      }
    },
  'bter' : {
    'btc' : {
      'bid': {
        'rate' : 0.0025,
        'target' : 25.0
        },
      'ask': {
        'rate' : 0.0025,
        'target' : 25.0
        }
      }
    }
}

_nuconfig = '%s/.nu/nu.conf'%os.getenv("HOME") # path to nu.conf
_tolerance = 0.0085 # price tolerance
_sampling = 20 # number of requests validated per minute
_autopayout = True # try to send payouts automatically
_minpayout = 0.03 # minimum balance to trigger payout
_grantaddress = "" # custodian grant address