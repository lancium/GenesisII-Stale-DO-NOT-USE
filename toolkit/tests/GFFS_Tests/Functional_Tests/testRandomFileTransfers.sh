#!/bin/bash

# Tests that the file transfer mechanism reliably copies files.
# Author: Chris Koeritz

export WORKDIR="$( \cd "$(\dirname "$0")" && \pwd )"  # obtain the script's working directory.
cd "$WORKDIR"

export SHOWED_SETTINGS_ALREADY=true
if [ -z "$GFFS_TOOLKIT_SENTINEL" ]; then
  source ../../../prepare_tools.sh ../../../prepare_tools.sh 
fi
source "$GFFS_TOOLKIT_ROOT/library/establish_environment.sh"

function decide_on_filenames_and_sizes()
{
  # constants constraining our test files.
  MAX_TEST_FILES=28
#  MAX_TEST_FILES=3
#hmmm: above just for getting script right.

  # the maximum file we will try to transfer.
  MAX_TEST_SIZE=148897792 # fairly arbitrary 142 mb.

  # set up the filenames we'll use as source material.
  export TESTING_DIR="$TEST_TEMP/transfer_test"
  # recreate our storage directory if it's not there.
  mkdir $TEST_TEMP/transfer_test 2>/dev/null
  EXAMPLE_FILES=()
  for ((i = 0; i < $MAX_TEST_FILES; i++)); do
    EXAMPLE_FILES+=("$(mktemp ${TESTING_DIR}/test_file.XXXXXX)")
  done

  echo -n noisy debug of the file list:
  for ((i = 0; i < ${#EXAMPLE_FILES[@]}; i++)); do
    echo -n " ${EXAMPLE_FILES[$i]}"
  done
  echo

  # set up the random size for each file.
  EXAMPLE_SIZES=()
  for ((i = 0; i < $MAX_TEST_FILES; i++)); do
    # we build up the random value with from 1 to 3 calls to the $RANDOM variable.
    # this gives us three ranges from which the example file sizes will be picked.
    bigrand=
    for ((j = 0; j < $(expr $i % 3) + 1; j++)); do
      bigrand+="$RANDOM"
    done
    tmpsize=$(expr $bigrand % $MAX_TEST_SIZE)
    EXAMPLE_SIZES+=($tmpsize)
  done

#echo "faking the sizes to be divisible by 5 and about 64mb!"
#EXAMPLE_SIZES=()  #reset the chosen values
#EXAMPLE_SIZES+=(67108860 67108880 67109005)

#another fakeout, just to crank up test fast with minimal files.
#EXAMPLE_SIZES=()  #reset the chosen values
#EXAMPLE_SIZES+=(5 20 38)

  echo -n noisy debug of the file sizes:
  for ((i = 0; i < ${#EXAMPLE_SIZES[@]}; i++)); do
    echo -n " ${EXAMPLE_SIZES[$i]}"
  done
  echo
}

function copyOneFileUp()
{
  local filename="$1"; shift
  local size="$1"; shift

  local base="$(basename "$filename")"
  local gridPath="$RNSPATH/$base"

#echo "filename=$filename size=$size base=$base gridPath=$gridPath"

  timed_grid cp "local:$filename" "$gridPath"
  retval=$?
  assertEquals "Copying local file $filename" 0 $retval
  if [ $retval -eq 0 ]; then
    real_time=$(calculateTimeTaken)
    echo "Time taken to copy $filename with $size bytes is $real_time s"
    actual_size=$(\ls -l $filename | awk '{print $5}')
    showBandwidth "$real_time" $size
  fi
}

function copyOneFileDown()
{
  local filename="$1"; shift
  local size="$1"; shift

  local base="$(basename "$filename")"
  local gridPath="$RNSPATH/$base"
  local newLocal="${filename}.new"

#echo "filename=$filename size=$size base=$base gridPath=$gridPath newLocal=$newLocal"

  timed_grid cp "$gridPath" "local:$newLocal"
  retval=$?
  assertEquals "Copying remote file $gridPath" 0 $retval
  if [ $retval -eq 0 ]; then
    real_time=$(calculateTimeTaken)
    echo "Time taken to copy $gridPath with $size bytes is $real_time s"
    actual_size=$(\ls -l $filename | awk '{print $5}')
    showBandwidth "$real_time" $size
  fi
}

function compareBeforeAndAfter()
{
  local filename="$1"; shift
  local size="$1"; shift

  local newLocal="${filename}.new"

  local md5Orig="$(md5sum "$filename" | awk '{print $1}')"
  assertEquals "Computing md5sum for $filename" 0 $?
  local md5New="$(md5sum "$newLocal" | awk '{print $1}')"
  assertEquals "Computing md5sum for $newLocal" 0 $?
  local sizeOrig="$(stat -c "%s" "$filename")"
  assertEquals "Computing size for $filename" 0 $?
  local sizeNew="$(stat -c "%s" "$newLocal")"
  assertEquals "Computing size for $newLocal" 0 $?

echo "md5Orig=$md5Orig md5New=$md5New sizeOrig=$sizeOrig sizeNew=$sizeNew"

  test "$md5Orig" == "$md5New"
  assertEquals "Agreement of md5sums for before and after" 0 $?

  test "$sizeOrig" -eq "$sizeNew"
  assertEquals "Agreement of size for before and after" 0 $?

  test "$sizeOrig" -eq "$size"
  assertEquals "Size correct for before file" 0 $?
  test "$sizeNew" -eq "$size"
  assertEquals "Size correct for after file" 0 $?
}

##############

oneTimeSetUp()
{
  sanity_test_and_init  # make sure test environment is good.

  # clean up from last test run.
  oneTimeTearDown

  # pick some file names and sizes for testing.
  decide_on_filenames_and_sizes
}

testCreateFiles()
{
  for ((i = 0; i < $MAX_TEST_FILES; i++)); do
    local file="${EXAMPLE_FILES[$i]}"
    local size="${EXAMPLE_SIZES[$i]}"
    dd if=/dev/urandom of=$file bs=1 count=$size
    echo "created $size byte file in $file"
  done
}

testCopyFilesUp()
{
  for ((i = 0; i < $MAX_TEST_FILES; i++)); do
    copyOneFileUp "${EXAMPLE_FILES[$i]}" "${EXAMPLE_SIZES[$i]}"
  done
}

testCopyFilesDown()
{
  for ((i = 0; i < $MAX_TEST_FILES; i++)); do
    copyOneFileDown "${EXAMPLE_FILES[$i]}" "${EXAMPLE_SIZES[$i]}"
  done
}

testFilesCameOutOkay()
{
  for ((i = 0; i < $MAX_TEST_FILES; i++)); do
    compareBeforeAndAfter "${EXAMPLE_FILES[$i]}" "${EXAMPLE_SIZES[$i]}"
  done
}

oneTimeTearDown()
{
#hmmm: maybe don't clean up if there were errors?
#if fail count -eq then do cleanup?
  echo cleaning up after test.
  if [ ${#EXAMPLE_FILES[@]} -gt 0 ]; then
    gridnames=()
    for ((i = 0; i < ${#EXAMPLE_FILES[@]}; i++)); do
      gridnames+=("$RNSPATH/$(basename "${EXAMPLE_FILES[$i]}")")
    done
##echo "cleaning grid names list: ${gridnames[@]}"
#    grid rm "${gridnames[@]}"
  fi
echo "*** cleaning turned off currently."
#  rm -rf "$TESTING_DIR"
}

# load and run shUnit2
source "$SHUNIT_DIR/shunit2"
