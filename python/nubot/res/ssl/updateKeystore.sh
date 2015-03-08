#!/bin/bash
#This script downloads the latest keystore 
#first rename existing keystore
mv nubot_keystore.jks nubot_keystore_old.jks
#then download from repository (branch develop)
wget -O nubot_keystore.jks https://bitbucket.org/JordanLeePeershares/nubottrading/src/cfa0c7699ccd96300c1b1f77d416b9a6f1fa6e8d/NuBot/res/ssl/nubot_keystore.jks\?at\=develop