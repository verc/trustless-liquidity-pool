import os

# pool configuration
_port = 2222
# daily interest rates
_interest = {
  'poloniex' : {
    'btc' : {
      'bid': {
        'rate' : 0.01,
        'target' : 50.0
        },
      'ask': {
        'rate' : 0.01,
        'target' : 50.0
        }
      }
  },
  'ccedk' : {
    'btc' : {
      'bid': {
        'rate' : 0.01,
        'target' : 50.0
        },
      'ask': {
        'rate' : 0.01,
        'target' : 50.0
        }
      }
  },
  'bitcoincoid' : {
    'btc' : {
      'bid': {
        'rate' : 0.01,
        'target' : 50.0
        },
      'ask': {
        'rate' : 0.01,
        'target' : 50.0
        }
      }
    },
  'bter' : {
    'btc' : {
      'bid': {
        'rate' : 0.01,
        'target' : 50.0
        },
      'ask': {
        'rate' : 0.01,
        'target' : 50.0
        }
      }
    },
  'testing' : {
    'btc' : {
      'bid': {
        'rate' : 0.0,
        'target' : 1000.0
        },
      'ask': {
        'rate' : 0.0,
        'target' : 1000.0
        }
      },
    'usd' : {
      'bid': {
        'rate' : 0.0,
        'target' : 100000.0
        },
      'ask': {
        'rate' : 0.0,
        'target' : 100000.0
        }
    },
    'eur' : {
      'bid': {
        'rate' : 0.0,
        'target' : 100000.0
      },
      'ask': {
        'rate' : 0.0,
        'target' : 100000.0
      }
    }
  }
}

_nuconfig = '%s/.nu/nu.conf'%os.getenv("HOME") # path to nu.conf
_tolerance = 0.0085 # price tolerance
_sampling = 30 # number of requests validated per minute
_autopayout = True # try to send payouts automatically
_minpayout = 0.1 # minimum balance to trigger payout
_grantaddress = "" # custodian grant address
