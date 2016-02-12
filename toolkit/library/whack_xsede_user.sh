#!/bin/bash

export WORKDIR="$( \cd "$(\dirname "$0")" && \pwd )"  # obtain the script's working directory.
cd "$WORKDIR"

if [ -z "$GFFS_TOOLKIT_SENTINEL" ]; then echo Please run prepare_tools.sh before testing.; exit 3; fi
source "$GFFS_TOOLKIT_ROOT/library/establish_environment.sh"

user=$1; shift

if [ -z "$user" ]; then
  echo This script needs one user name to completely clean up.  This will only
  echo operate on kerberos users stored on sts-1.xsede.org.
  exit 1
fi

if [ -z "$TMP" ]; then
  noisefile=/tmp/${USER}-drop_user_noise.txt
else
  noisefile=$TMP/drop_user_noise.txt
fi

echo dropping user $user

"$GENII_BINARY_DIR/grid" >$noisefile <<eof
  unlink /resources/xsede.org/containers/sts-1.xsede.org/Services/KerbAuthnPortType/$user
  onerror failed to remove kerberos port type entry for $user.
  unlink /users/xsede.org/$user
  onerror failed to remove user from /users/xsede.org for $user.
  rm -r /home/xsede.org/$user
  onerror failed to remove home directory for $user.
eof
if [ $? -ne 0 ]; then
  echo Attempt to drop user $user has failed.
  exit 1
fi


