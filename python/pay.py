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

if len(sys.argv[1]) == 1:
  print "usage:", sys.argv[0],'timestemp'

try:
  users = { line.strip().split()[4] : line.strip().split()[7] for line in open('logs/%s.log'%sys.argv[1]).readlines() if line.strip().split()[2] == "new" }
  credits = {}
  for line in open('logs/%s.credits'%sys.argv[1]).readlines():
    line = line.strip().split()
    if not line[3] in credits:
      credits[line[3]] = 0.0
    credits[line[3]] += float(line[2])
except:
  print >> sys.stderr, "could not read data"
  sys.exit(1)

out = {}
for addr in users:
  out[addr] = float("%.8f" % max(sum([credits[k] for k in users[addr] if k in credits]), 0.015))
print out