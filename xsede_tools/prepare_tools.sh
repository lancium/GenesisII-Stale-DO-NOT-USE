#!/bin/bash

# Author: Chris Koeritz
# Author: Vanamala Venkataswamy

# we do not want to "exit" from this file at all, since it is used with the
# bash source command and that will exit from the calling shell as well.
# there is a variable below called BADNESS that indicates when errors
# occurred during processing, and if it's not empty at the end of the script
# then we will not set the test sentinel variable which other scripts
# check.

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
export XSEDE_TEST_ROOT="$GRITTY_TESTING_TOP_LEVEL"

source "$XSEDE_TEST_ROOT/library/helper_methods.sh"

# where the shunit library resides.
export SHUNIT_DIR="$XSEDE_TEST_ROOT/shunit"

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

# TEST_TEMP is a folder where we can generate a collection of junk files.
export TEST_TEMP="$TMP/xsede_test_output_${USER}"
if [ ! -d "$TEST_TEMP" ]; then
  mkdir -p "$TEST_TEMP"
fi

# the location where our munged jsdl files will reside.
export GENERATED_JSDL_FOLDER="$TEST_TEMP/patched_jsdl"
if [ ! -d "$GENERATED_JSDL_FOLDER" ]; then
  mkdir -p "$GENERATED_JSDL_FOLDER"
fi

## this is the main source of parameters for the tests.
#export XSEDE_TOOLS_CONFIG_FILE
#if [ -z "$XSEDE_TOOLS_CONFIG_FILE" ]; then
#  # old default is used first if we don't have a setting for the config file.
#  XSEDE_TOOLS_CONFIG_FILE="$XSEDE_TEST_ROOT/inputfile.txt"
#fi
#if [ ! -f "$XSEDE_TOOLS_CONFIG_FILE" ]; then
#  # newer more rational config file name which also matches well with our
#  # environment variable...  but this file must exist or we're stumped.
#  XSEDE_TOOLS_CONFIG_FILE="$XSEDE_TEST_ROOT/xsede_tools.cfg"
#fi
#if [ ! -f "$XSEDE_TOOLS_CONFIG_FILE" -a -z "$BADNESS" ]; then
#  echo "----"
#  echo "This script requires that you prepare a customized file in:"
#  echo "    $XSEDE_TOOLS_CONFIG_FILE"
#  echo "with the details of your grid installation.  There are some example"
#  echo "config files in the folder '$XSEDE_TEST_ROOT/examples'."
#  BADNESS=true
#fi

# uncomment this to enable extra output.
export DEBUGGING=true

# turn this off in non-debugging mode.
if [ ! -z "$DEBUGGING" -a -z "$SHOWED_SETTINGS_ALREADY" \
    -a -z "$BADNESS" -a "${TERM}" != "dumb" -a -z "$PBS_ENVIRONMENT" ]; then
  echo "==========================================================="
  echo "Tests are running from $XSEDE_TEST_ROOT"
  echo "Root of temporaries (TMP) is set to $TMP"
  echo "Test temporaries (TEST_TEMP) are in $TEST_TEMP"
  echo "SHUNIT_DIR set to $SHUNIT_DIR"
#hmmm: fix that to be same as SHUNIT_PARENT as needed by shunit.
  echo "==========================================================="
fi

if [ ! -z "$(uname -a | grep -i darwin)" -a -z "$BADNESS" ]; then
  # add in the mac binaries if this is darwin.
  export PATH="$XSEDE_TEST_ROOT/bin/macosx:$PATH"
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
  unset XSEDE_TEST_SENTINEL XSEDE_TEST_ROOT GRITTY_TESTING_TOP_LEVEL SHUNIT_DIR BADNESS
else
  # if things were successful, we can finally set our indicator for the scripts to check.
  export XSEDE_TEST_SENTINEL=initialized
fi

