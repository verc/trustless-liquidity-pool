#!/usr/bin/python

import sys
from Crypto import Random
from Crypto.Cipher import AES

def pad(s):
  return s + b"\0" * (AES.block_size - len(s) % AES.block_size)

def encrypt(message, key, key_size=256):
  message = pad(message)
  iv = Random.new().read(AES.block_size)
  cipher = AES.new(key, AES.MODE_CBC, iv)
  return iv + cipher.encrypt(message)

def decrypt(ciphertext, key):
  iv = ciphertext[:AES.block_size]
  cipher = AES.new(key, AES.MODE_CBC, iv)
  plaintext = cipher.decrypt(ciphertext[AES.block_size:])
  return plaintext.rstrip(b"\0")

def encrypt_file(plaintext, fileout, key):
  enc = encrypt(plaintext, key)
  with open(fileout, 'wb') as fo:
    fo.write(enc)
  
def decrypt_file(file_name, key):
  with open(file_name, 'rb') as fo:
      ciphertext = fo.read()
  dec = decrypt(ciphertext, key)
  return dec


#key = b'\xbf\xc0\x85)\x10nc\x94\x02)j\xdf\xcb\xc4\x94\x9d(\x9e[EX\xc8\xd5\xbfI{\xa2$\x05(\xd5\x18'
kes = 'iamastrongpassword'

text = open('users.dat').read()
print text
print "+++++++++++++"
encrypt_file(text, 'users.enc', key)
print decrypt_file('users.enc', key)