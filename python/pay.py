import sys

try:
  users = { line.strip().split()[4] : line.strip().split()[7] for line in open(sys.argv[1]).readlines() if line.strip().split()[2] == "new" }
  credits = {}
  for line in open(sys.argv[2]).readlines():
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