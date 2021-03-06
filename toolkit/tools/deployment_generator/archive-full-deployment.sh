#!/bin/bash

# creates an archive of the deployment generator package with absolutely everything.
# this is intended for archival safe-keeping of a grid's deployment configuration only.
# it has all the keypairs for the grid.

####

export WORKDIR="$( \cd "$(\dirname "$0")" && \pwd )"  # obtain the script's working directory.

if [ -z "$GFFS_TOOLKIT_SENTINEL" ]; then echo Please run prepare_tools.sh before testing.; exit 3; fi

# load the rest of the tool environment.
source "$GFFS_TOOLKIT_ROOT/library/establish_environment.sh"

# otherwise load the rest of the tool environment.
source "$GFFS_TOOLKIT_ROOT/library/establish_environment.sh"

##############

# must be in synch with generator methods value.
DEPLOYMENT_MEMORY_FILE=saved-deployment-info.txt

if [ ! -f $DEPLOYMENT_MEMORY_FILE ]; then
  echo "This does not appear to be a valid deployment folder, because the file"
  echo "'saved-deployment-info.txt' is missing."
  exit 1
fi

# get the variables we need to know about the deployment.
source $DEPLOYMENT_MEMORY_FILE

# build some variables we'll need for finding things.
DEP_DIR="$GENII_INSTALL_DIR/deployments/$DEP_NAME"

####

function date_stringer() 
{ 
  local sep="$1";
  shift;
  if [ -z "$sep" ]; then
    sep='_';
  fi;
  date +"%Y$sep%m$sep%d$sep%H%M$sep%S" | tr -d '/\n/'
}

ARCHIVE_NAME=$HOME/deployment_archive_with_keypairs_$(date_stringer).tar.gz

pushd ..
tar -czf $ARCHIVE_NAME deployment_generator "$GENII_INSTALL_DIR/context.xml" "$DEP_DIR" --exclude=".svn"
if [ $? -ne 0 ]; then
  echo Failed to pack the deployment archive.
  exit 1
fi
popd


