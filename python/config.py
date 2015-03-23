import os

# pool configuration
_port = 2222
# daily interest rates
_interest = {
  'poloniex' : {
    'btc' : {
      'bid': {
        'rate' : 0.025,
        'target' : 20.0
        },
      'ask': {
        'rate' : 0.025,
        'target' : 20.0
        }
      }
  },
  'ccedk' : {
    'btc' : {
      'bid': {
        'rate' : 0.025,
        'target' : 20.0
        },
      'ask': {
        'rate' : 0.025,
        'target' : 20.0
        }
      }
  },
  'bitcoincoid' : {
    'btc' : {
      'bid': {
        'rate' : 0.025,
        'target' : 20.0
        },
      'ask': {
        'rate' : 0.025,
        'target' : 20.0
        }
      }
    },
  'bter' : {
    'btc' : {
      'bid': {
        'rate' : 0.025,
        'target' : 20.0
        },
      'ask': {
        'rate' : 0.025,
        'target' : 20.0
        }
      }
    }
}

_nuconfig = '%s/.nu/nu.conf'%os.getenv("HOME") # path to nu.conf
_tolerance = 0.0085 # price tolerance
_sampling = 35 # number of requests validated per minute
_autopayout = True # try to send payouts automatically
_minpayout = 0.1 # minimum balance to trigger payout
_grantaddress = "" # custodian grant address
