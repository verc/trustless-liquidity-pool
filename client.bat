@echo off
SET users=pool.conf
if not exist pool.conf (
  if exist pool.conf.txt (
    SET users=pool.conf.txt
  ) else (
    @echo on
    echo error: You must specify a file called pool.conf with your exchange data
    pause
    exit
  )
)
@echo on
client.py %users%
