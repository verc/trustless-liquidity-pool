@echo off
SET users=users.dat
if not exist users.dat (
  if exist users.txt (
    SET users=users.txt
  ) else (
    @echo on
    echo error: You must specify a file called users.txt with your exchange data
    pause
    exit
  )
)
@echo on
client.py 104.245.36.10:2222 %users%
