#!/bin/bash

# useful functions that are somewhat general.  these are not needed for
# the basic setup of the test environment, but they are used by other
# test and tool functions and also by specific tests.
#
# Author: Chris Koeritz

# prints out a timestamp with the current date and time up to seconds.
function date_string()
{
  date +"%Y_%b_%e_%H%M_%S" | sed -e 's/ //g'
}

# displays the value of a variable in bash friendly format.
# (donated by the feisty meow scripts at http://feistymeow.org)
function var() {
  while true; do
    local varname="$1"; shift
    if [ -z "$varname" ]; then
      break
    fi
    if [ -z "${!varname}" ]; then
      echo "$varname undefined"
    else
      echo "$varname=${!varname}"
    fi
  done
}

# given a file name and a phrase to look for, this replaces all instances of
# it with a piece of replacement text.  note that slashes are okay in the two
# text pieces, but we are using pound signs as the regular expression
# separator; phrases including the octothorpe (#) will cause syntax errors.
function replace_phrase_in_file()
{
  local file="$1"; shift
  local phrase="$1"; shift
  local replacement="$1"; shift
  if [ -z "$file" -o -z "$phrase" ]; then
    echo "replace_phrase_in_file: needs a filename, a phrase to replace, and the"
    echo "text to replace that phrase with."
    return 1
  fi
  sed -i -e "s%$phrase%$replacement%g" "$file"
}

# prints an error message (from parameters) and exits if the previous command failed.
function check_if_failed()
{
  if [ $? -ne 0 ]; then
    echo Step failed: $*
    exit 1
  fi
}

# takes a first parameter that is the name for a combined error and output log,
# and then runs all the other parameters as a command.
function logged_command()
{
  local my_output="$1"; shift
#  echo "logged_command args: $(printf -- "[%s] " "${@}")"
  eval "${@}" >>"$my_output" 2>&1
  local retval=$?
  if [ $retval == 0 ]; then
    # good so far, but check for more subtle ways of failing; if there is
    # an occurrence of our fail message in the output, that also indicates
    # the command did not succeed.
    grep "\[FAILURE\]" $my_output
    # we do not want to see that phrase in the log.
    if [ $? != 0 ]; then
      return 0  # fine exit, can ignore log.
    fi
  fi
  if [[ ! "$my_output" =~ .*fuse_output.* ]]; then
    # this was a failure, so we need to see the log.
    # fuse errors currently don't count since they are multifarious.
    cat "$my_output"
  fi
  return 1
}

# runs an arbitrary command.  if the command fails, then the output from it is
# displayed and an error code is returned.  otherwise the output is discarded.
function run_any_command()
{
  local my_output="$(mktemp $TEST_TEMP/grid_logs/out_run_any_cmd_$(date_string).XXXXXX)"
  logged_command "$my_output" "${@}"
  local retval=$?
  # make the external version of the log file available.  if we're multiplexing users,
  # this will be meaningless, which is why we used unique names above.
  \cp -f "$my_output" "$GRID_OUTPUT_FILE"
  return $retval
}


# returns 0 if there should be no problems using fuse, or non-zero if this platform
# does not currently support fuse.
function fuse_supported()
{
  local retval=0
  local platform="$(uname -a | tr A-Z a-z)"
  if [[ $platform =~ .*darwin.* ]]; then retval=1; fi
  if [[ $platform =~ .*cygwin.* ]]; then retval=1; fi
  if [[ $platform =~ .*ming.* ]]; then retval=1; fi
  return $retval
}

# returns 0 if there should be no problems creating links in the file system,
# or non-zero if this platform does not support symbolic links.
function links_supported()
{
  local retval=0
  local platform="$(uname -a | tr A-Z a-z)"
  if [[ $platform =~ .*cygwin.* ]]; then retval=1; fi
  if [[ $platform =~ .*ming.* ]]; then retval=1; fi
  return $retval
}

# Create a test directory (in the first parameter) with $2 subdirectories,
# each with $3 subdirs, each with $4 files.
fan_out_directories()
{
  local dir_name="$1"; shift
  local top_count=$1; shift
  local mid_count=$1; shift
  local file_count=$1; shift
  mkdir "$dir_name"
  for (( di=0 ; di<$top_count ; di++ )); do
    mkdir "$dir_name"/sub$di
    for (( dj=0 ; dj<$mid_count ; dj++ )); do
      mkdir "$dir_name"/sub$di/sub$dj
      for (( dk=0 ; dk<$file_count ; dk++ )); do
        echo "file $di$dj$dk" > "$dir_name"/sub$di/sub$dj/file$di$dj$dk
      done
    done
  done
}
##############

  # copied from open source codebase at: http://feistymeow.org
  # locates a process given a search pattern to match in the process list.
  function psfind() {
    local -a patterns=("${@}")
    local PID_DUMP="$(mktemp "$TMP/zz_pidlist.XXXXXX")"
    local -a PIDS_SOUGHT
    if [ "$OS" == "Windows_NT" ]; then
      if [ ! -d c:/tmp ]; then
        mkdir c:/tmp
      fi
      # windows7 magical mystery tour lets us create a file c:\\tmp_pids.txt, but then it's not
      # really there in the root of drive c: when we look for it later.  hoping to fix that
      # problem by using a subdir, which also might be magical thinking from windows perspective.
      tmppid=c:\\tmp\\pids.txt
      # we have abandoned all hope of relying on ps on windows.  instead we use wmic to get full
      # command lines for processes.
      wmic /locale:ms_409 PROCESS get processid,commandline </dev/null >"$tmppid"
      local flag='/c'
      if [ ! -z "$(uname -a | grep "^MING" )" ]; then
        flag='//c'
      fi
      # we 'type' the file to get rid of the unicode result from wmic.
      # needs to be a windows format filename for 'type' to work.
      cmd $flag type "$tmppid" >$PID_DUMP
      \rm "$tmppid"
#      local CR='
#'  # embedded carriage return.
#      local appropriate_pattern="s/^.*  *\([0-9][0-9]*\)[ $CR]*\$/\1/p"
      local appropriate_pattern="s/^.*  *\([0-9][0-9]*\) *\$/\1/p"
      for i in "${patterns[@]}"; do
        PIDS_SOUGHT+=($(cat $PID_DUMP \
          | grep -i "$i" \
          | sed -n -e "$appropriate_pattern"))
      done
    else
      /bin/ps $extra_flags wux >$PID_DUMP
      # pattern to use for peeling off the process numbers.
      local appropriate_pattern='s/^[-a-zA-Z_0-9][-a-zA-Z_0-9]*  *\([0-9][0-9]*\).*$/\1/p'
      # remove the first line of the file, search for the pattern the
      # user wants to find, and just pluck the process ids out of the
      # results.
      for i in "${patterns[@]}"; do
        PIDS_SOUGHT+=($(cat $PID_DUMP \
          | sed -e '1d' \
          | grep -i "$i" \
          | sed -n -e "$appropriate_pattern"))
      done
    fi
    if [ ${#PIDS_SOUGHT[*]} -ne 0 ]; then
      local PIDS_SOUGHT2=$(printf -- '%s\n' ${PIDS_SOUGHT[@]} | sort | uniq)
      PIDS_SOUGHT=()
      PIDS_SOUGHT=${PIDS_SOUGHT2[*]}
      echo ${PIDS_SOUGHT[*]}
    fi
    /bin/rm $PID_DUMP
  }

#######

# tests the supposed fuse mount that is passed in as the first parameter.
function test_fuse_mount()
{
  local mount_point="$1"; shift
  local trunc_mount="$(basename "$(dirname $mount_point)").$(basename "$mount_point")"

  checkMount="$(mount)"
echo "checkmount is: '$checkMount'"
echo "mount point seeking is: '$trunc_mount'"
  local retval=1
  if [[ "$checkMount" =~ .*$trunc_mount.* ]]; then
echo found the mount in the list
    retval=0
  fi
  if [ $retval -ne 0 ]; then
    echo "Finding mount point '$trunc_mount' failed."
    return 1
  fi
echo dir has:
  ls -l "$mount_point" 
#&>/dev/null
  return $?
}

#######

# also borrowed from feisty meow scripts...  by consent of author (chris koeritz).

  # switches from a /X/path form to an X:/ form.  this also processes cygwin paths.
  function unix_to_dos_path() {
    # we usually remove dos slashes in favor of forward slashes.
    if [ ! -z "$SERIOUS_SLASH_TREATMENT" ]; then
      # unless this flag is set, in which case we force dos slashes.
      echo "$1" | sed -e 's/\\/\//g' | sed -e 's/\/cygdrive//' | sed -e 's/\/\([a-zA-Z]\)\/\(.*\)/\1:\/\2/' | sed -e 's/\//\\/g'
    else
      echo "$1" | sed -e 's/\\/\//g' | sed -e 's/\/cygdrive//' | sed -e 's/\/\([a-zA-Z]\)\/\(.*\)/\1:\/\2/'
    fi
  }
  
  # switches from an X:/ form to an /X/path form.
  function dos_to_unix_path() {
    # we always remove dos slashes in favor of forward slashes.
    echo "$1" | sed -e 's/\\/\//g' | sed -e 's/\([a-zA-Z]\):\/\(.*\)/\/\1\/\2/'
  }

#######


