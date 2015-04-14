import sys

add = {}
for line in open(sys.argv[1]).readlines():
  line = line.strip().split()
  if not line[1] in add:
    add[line[1]] = []
  add[line[1]].append(line[0])

credits = {}
for line in open(sys.argv[2]).readlines():
  line = line.strip().split()
  if not line[3] in credits:
    credits[line[3]] = 0.0
  credits[line[3]] += float(line[2])

out = {}
for addr in add:
  out[addr] = min(sum([credits[k] for k in add[addr] if k in credits]), 0.01)
print out