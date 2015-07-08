#!/bin/bash
#
# Runs the trac server startup if it's not already going.
# This is a lighter-weight solution than adding trac to init scripts,
# and it can be run as a normal user with the trac repository in their
# home directory.
#
# Author: Chris Koeritz

source "$FEISTY_MEOW_SCRIPTS/core/functions.sh"

# we only try to do something if trac is missing.
if [ "$(psfind -u $USER tracd)" ]; then
  exit 0
fi

#...adjust this for where you have your repository.
# this configuration also assumes that stunnel is routing the non-ssl trac
# service to the web; the below does not make trac visible off-machine.
tracd -s --hostname=localhost -p 8000 --basic-auth="GenesisII,/home/trac/tracsites/authFile," /home/trac/tracsites/GenesisII &

