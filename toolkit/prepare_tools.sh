#!/bin/bash

# Author: Chris Koeritz
#
# Note:
# We do not want to "exit" from this file at all (nor from any file that it
# invokes either), since this script is intended for use by the bash 'source'
# command.  If we exit, that will exit from the calling shell as well, which
# torpedoes whatever one was doing in that shell.
# There is a variable below called BADNESS that indicates when errors
# occurred during processing, and if it's not empty at the end of the script
# then we will consider this a failed run, and we will not set the test's
# sentinel variable which other scripts check to see if the environment
# was loaded properly.

# make sure whether they have defined the top-level location for us.
if [ ! -z "$1" ]; then
  # first attempt is to use the first parameter, if one is provided.  this should
  # be an absolute path reference to this very file, from which we can deduce the
  # starting directory.
  GRITTY_TESTING_TOP_LEVEL="$( cd "$( dirname "$1" )" && \pwd )"
  # for this case, they also don't need to be stranded in a new shell, because we
  # assume they have sourced this file instead of bashing it.
  NO_SUBSHELL=true
fi
if [ -z "$GRITTY_TESTING_TOP_LEVEL" ]; then
  # otherwise, if they didn't explicitly set the top-level directory, we will
  # do it using some unix trickery.
  if [[ "$0" =~ .*bash ]]; then
    echo "----"
    echo "This script was not launched properly with 'source'.  The script should"
    echo "be started like this: source prepare_tools.sh prepare_tools.sh"
    echo "The double entry is required for bash's source command to find the path."
    BADNESS=true
  fi
  GRITTY_TESTING_TOP_LEVEL="$( cd "$( dirname "$0" 2>/dev/null )" && \pwd )"
else
  # we assume they are managing this script more closely and do not need (or want) a bash sub-shell.
  NO_SUBSHELL=true
fi
GRITTY_TESTING_TOP_LEVEL="$(echo "$GRITTY_TESTING_TOP_LEVEL" | sed -e 's/\/cygdrive\/\(.\)/\1:/')"

# the top-level directory for tests, i.e. the root of testing hierarchy.
export GFFS_TOOLKIT_ROOT="$GRITTY_TESTING_TOP_LEVEL"

source "$GFFS_TOOLKIT_ROOT/library/helper_methods.sh"

# where the shunit library resides.
export SHUNIT_DIR="$GFFS_TOOLKIT_ROOT/shunit"

# establish the TMP variable if it's not already set.
export TMP
if [ -z "$TMP" ]; then
  TMP="$HOME/tmp"
  if [ ! -d "$TMP" ]; then mkdir "$TMP"; fi
fi
TMP="$(echo "$TMP" | sed -e 's/\/cygdrive\/\(.\)/\1:/')"
if [ ! -d "$TMP" ]; then 
  echo "The TMP directory was set as $TMP but cannot be created or found."
  echo "If there is a file at that location, please move or delete it."
  exit 1
fi

##############

# commonly used environment variables...

# TEST_TEMP is a folder where we can generate a collection of junk files.
export TEST_TEMP="$TMP/gffs_tools_output_${USER}"
if [ ! -d "$TEST_TEMP" ]; then
  mkdir -p "$TEST_TEMP"
fi

# this variable points to the last output from a grid command.
export GRID_OUTPUT_FILE="$TEST_TEMP/grid_output.log"
export GRID_TIMING_FILE="$TEST_TEMP/grid_times.log"
export CONGLOMERATED_GRID_OUTPUT="$TEST_TEMP/full_grid_output.log"

# the location where our munged jsdl files will reside.
export GENERATED_JSDL_FOLDER="$TEST_TEMP/patched_jsdl"
if [ ! -d "$GENERATED_JSDL_FOLDER" ]; then
  mkdir -p "$GENERATED_JSDL_FOLDER"
fi

##############

# uncomment this to enable extra output.
export DEBUGGING=true

##############

# turn this printout off in non-debugging mode or if the terminal setting
# seems to indicate that we're running in a login environment (where any
# echoing to standard out can screw up scp and sftp for that account).
if [ ! -z "$DEBUGGING" -a -z "$SHOWED_SETTINGS_ALREADY" \
    -a -z "$BADNESS" -a -z "$SILENT_RUNNING" -a "${TERM}" != "dumb" \
    -a -z "$PBS_ENVIRONMENT" ]; then
  echo "==========================================================="
  echo "Genesis II and GFFS Toolkit environment loaded."
  var GENII_INSTALL_DIR GENII_USER_DIR GFFS_TOOLKIT_ROOT GFFS_TOOLKIT_CONFIG_FILE TMP TEST_TEMP
  echo "==========================================================="
fi

if [ ! -z "$(uname -a | grep -i darwin)" -a -z "$BADNESS" ]; then
  # add in the mac binaries if this is darwin.
  export PATH="$GFFS_TOOLKIT_ROOT/bin/macosx:$PATH"
else
  # no change, but we want to make sure sub-shells inherit the path.
  export PATH="$PATH"
fi

if [ -z "$NO_SUBSHELL" -a -z "$BADNESS" ]; then
  # at this point we go into a new interactive shell, so as to ensure the
  # environment parameters stay right.
  # the self-location code at the top doesn't work properly if this file is
  # sourced into a current environment.
  bash
fi

if [ ! -z "$BADNESS" ]; then
  echo
  echo "----"
  echo "There were errors in setting up the xsede tests--see above messages."
  unset GFFS_TOOLKIT_SENTINEL GFFS_TOOLKIT_ROOT GRITTY_TESTING_TOP_LEVEL SHUNIT_DIR BADNESS
else
  # if things were successful, we can finally set our indicator for the scripts to check.
  export GFFS_TOOLKIT_SENTINEL=initialized
fi

