import os

# pool configuration
_port = 3333
# daily interest rates
_interest = {
  'bittrex' : {
    'btc' : {
      'bid': {
        'rate' : 0.00025,
        'target' : 10000.0
        },
      'ask': {
        'rate' : 0.00025,
        'target' : 10000.0
        }
      }
  },
  'poloniex' : {
    'btc' : {
      'bid': {
        'rate' : 0.001,
        'target' : 500.0
        },
      'ask': {
        'rate' : 0.001,
        'target' : 500.0
        }
      }
  },
  'ccedk' : {
    'btc' : {
      'bid': {
        'rate' : 0.001,
        'target' : 500.0
        },
      'ask': {
        'rate' : 0.001,
        'target' : 500.0
        }
      }
  },
  'bitcoincoid' : {
    'btc' : {
      'bid': {
        'rate' : 0.001,
        'target' : 500.0
        },
      'ask': {
        'rate' : 0.001,
        'target' : 500.0
        }
      }
    },
  'bter' : {
    'btc' : {
      'bid': {
        'rate' : 0.001,
        'target' : 500.0
        },
      'ask': {
        'rate' : 0.001,
        'target' : 500.0
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
_master = ""
_slaves = []

import sys
_port=int(sys.argv[1])
_master = sys.argv[2]
if sys.argv[3] != "":
  _slaves = [ sys.argv[3] ]