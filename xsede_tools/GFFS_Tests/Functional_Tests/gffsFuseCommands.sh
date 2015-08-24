#!/bin/bash

# Author: Chris Koeritz
# Author: Vanamala Venkataswamy

export WORKDIR="$( \cd "$(\dirname "$0")" && \pwd )"  # obtain the script's working directory.
cd "$WORKDIR"

source "../../prepare_tools.sh" "../../prepare_tools.sh"
if [ -z "$TEST_TEMP" ]; then
  echo The xsede tool suite could not be automatically located.
  exit 1
fi

if [ -z "$XSEDE_TEST_SENTINEL" ]; then echo Please run prepare_tools.sh before testing.; exit 1; fi
source "$XSEDE_TEST_ROOT/library/establish_environment.sh"

# how long should we snooze before checking that fuse has gotten the
# notification that new files are available?
FUSE_SNOOZE_TIME=34
# how many times we should test that fuse sees a new file in the grid.
#FUSE_COPY_COUNT=1
FUSE_COPY_COUNT=20

# where we hook in the fuse mount.
MOUNT_POINT="$TEST_TEMP/mount-gffsFuseCommands"
# the user's home directory from fuse perspective.
HOME_DIR="$MOUNT_POINT/$RNSPATH"
# directories we create and manipulate during test.
TESTING_DIR="$HOME_DIR/fuse-test"
TESTING_DIR_ALTERNATE="$HOME_DIR/fuse-test-alt"
# the main testing directory from grid's perspective.
GRID_TEST_DIR=$RNSPATH/fuse-test

oneTimeSetUp()
{
  sanity_test_and_init  # make sure test environment is good.

  if ! fuse_supported; then return 0; fi

  # need to just go for it and try to unmount; the directory left for a dead mount
  # is still present but is not seen by checks.
  fusermount -u "$MOUNT_POINT" &>/dev/null
  sync ; sleep 2
  if [ -d "$MOUNT_POINT" ]; then
    rmdir "$MOUNT_POINT"
  fi
  mkdir "$MOUNT_POINT"
}

testFuseMounting()
{
  if ! fuse_supported; then return 0; fi

  echo "Testing 'fuse --mount' command"
  echo "Mounting $MOUNT_POINT using Fuse Mount command"
  fuse --mount local:"$MOUNT_POINT"
  sleep 30

  test_fuse_mount "$MOUNT_POINT"
  check_if_failed "Mounting grid to local directory"

  # make sure we can clear out any previous junk.
  \rm -rf "$TESTING_DIR" "$TESTING_DIR_ALTERNATE"
  assertEquals "Cleaning any prior folders under mount" 0 $?
}

testChangingDirToMount()
{
  if ! fuse_supported; then return 0; fi
  cd "$MOUNT_POINT"
  assertEquals "Tesing 'cd' to mounted directory" 0 $?
}

testPrintingWorkingDir()
{
  if ! fuse_supported; then return 0; fi
  pwd
  assertEquals "Testing 'pwd'" 0 $?
}

testMakingDirectoryOnMount()
{
  if ! fuse_supported; then return 0; fi
  cd "$HOME_DIR"
  echo "Current directories (local and grid):"
  pwd
  grid pwd
  #cat $GRID_OUTPUT_FILE
  echo "Mount directory:"
  ls "$MOUNT_POINT"
  echo "RNS (home) path:"
  ls "$HOME_DIR"
  \rm -rf "$TESTING_DIR"  # clean it out first.
  mkdir "$TESTING_DIR"
  assertEquals "Testing 'mkdir' on mounted $RNSPATH dir" 0 $?
  ls -l "$TESTING_DIR"
  assertEquals "Testing 'ls' on mounted $RNSPATH dir" 0 $?
}

testLocalLs()
{
  if ! fuse_supported; then return 0; fi
  grid ls local:./
  assertEquals "Testing 'ls' on local dir" 0 $?
}

testCreatingFileOnMount()
{
  if ! fuse_supported; then return 0; fi
  echo "Hello \n Test File\n" > $TEST_TEMP/local-file.txt
  cp $TEST_TEMP/local-file.txt "$TESTING_DIR/grid-file.txt"
  assertEquals "Testing copy of local file to mounted grid folder." 0 $?

  diff "$TESTING_DIR/grid-file.txt" "$TEST_TEMP/local-file.txt"
  assertEquals "Checking contents of new file" 0 $?
}

testCopyingFromMountToLocal()
{
  if ! fuse_supported; then return 0; fi
  echo "Testing 'cp' file from mounted dir to local dir"
  cp "$TESTING_DIR/grid-file.txt" "$TEST_TEMP/local-file1.txt"
  cat $TEST_TEMP/local-file1.txt
}

testCheckDiffsOnFiles()
{
  if ! fuse_supported; then return 0; fi
  diff "$TEST_TEMP/local-file.txt" "$TESTING_DIR/grid-file.txt"
  assertEquals "Diff the local-file with the grid-file" 0 $?

  diff "$TESTING_DIR/grid-file.txt" "$TEST_TEMP/local-file1.txt"
  assertEquals "Diff the grid-file with the local-file" 0 $?

  diff $TEST_TEMP/local-file.txt $TEST_TEMP/local-file1.txt
  assertEquals "Diff the local-file with original local-file" 0 $?
}

testTouchingFiles()
{
  if ! fuse_supported; then return 0; fi
  touch "$TESTING_DIR/test-file.txt"
  assertEquals "Testing 'touch test-file.txt' on mounted dir" 0 $?
 
  ls -l "$TESTING_DIR/test-file.txt"
  assertEquals "Testing ls on the test-file" 0 $?
}

testEchoCreatingAFile()
{
  if ! fuse_supported; then return 0; fi
  echo "Hello\\n This is test file.\\n.\\n.\\n.\\n End of test file" > "$TESTING_DIR/test-file.txt"
  assertEquals "Testing 'echo' writing to test-file.txt on mounted dir" 0 $?
  ls -l "$TESTING_DIR/test-file.txt"
  assertEquals "Testing ls on echoed test-file" 0 $?
  cat "$TESTING_DIR/test-file.txt"
  assertEquals "Testing cat on echoed test-file" 0 $?
}

testUpdatingFileContents()
{
  if ! fuse_supported; then return 0; fi
  local lorem_ipsum="Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed gravida, metus nec porttitor pharetra."
  echo "$lorem_ipsum" >"$TESTING_DIR/lorem"
  echo "$lorem_ipsum" >$TEST_TEMP/merol
  diff "$TEST_TEMP/merol" "$TESTING_DIR/lorem"
  assertEquals "File on grid should be the same as local version." 0 $?
  # now put some more in there; we should have file & cache consistency such that
  # the update is available right away.
  echo "$lorem_ipsum" >>"$TESTING_DIR/lorem"
  echo "$lorem_ipsum" >>$TEST_TEMP/merol
  diff "$TEST_TEMP/merol" "$TESTING_DIR/lorem"
  assertEquals "File on grid should be the same as local version after doubling." 0 $?
  echo "$lorem_ipsum" >>"$TESTING_DIR/lorem"
  echo "$lorem_ipsum" >>$TEST_TEMP/merol
  sleep $(expr $RANDOM % 3 + 2)  # arbitrary sleep shouldn't make a difference.
  diff "$TEST_TEMP/merol" "$TESTING_DIR/lorem"
  assertEquals "File on grid should be the same as local version after trebling." 0 $?
}

testChmoddingFile()
{
  if ! fuse_supported; then return 0; fi
  echo "ls before 'chmod' on test-file.txt"
  ls -l "$TESTING_DIR/test-file.txt"
  assertEquals "Checking ls for current file attributes" 0 $?
  echo "more stuff into fuse mounted file" >>"$TESTING_DIR/test-file.txt"
  assertEquals "Testing append on test file" 0 $?
  chmod +x "$TESTING_DIR/test-file.txt"
  assertEquals "Testing 'chmod +x test-file.txt' on mounted dir" 0 $?
#disturbing: test -x works even though ls -al shows file is *not* executable.
#  test -x "$TESTING_DIR/test-file.txt"
# assertEquals "Checking executable status after 'chmod' on test-file.txt" 0 $?
  ls -al "$TESTING_DIR/test-file.txt" | grep '^-rwx'
  assertEquals "Checking executable status after 'chmod' on test-file.txt" 0 $?
echo ls of it:
ls -al "$TESTING_DIR/test-file.txt" 
}

testRemovingFile()
{
  if ! fuse_supported; then return 0; fi
  \rm "$TESTING_DIR/test-file.txt"
  assertEquals "Testing 'rm test-file.txt' from mounted dir" 0 $?
  ls -l "$TESTING_DIR/"
  assertEquals "Testing ls on folder where file used to be" 0 $?
}

testMovingFileFromMount()
{
  if ! fuse_supported; then return 0; fi
  # make sure this is out of the way before the move.
  \rm -rf "$TEST_TEMP/foondir"
  mv "$TESTING_DIR" "$TEST_TEMP/foondir"
  assertEquals "Testing 'mv $TESTING_DIR ./' from mounted dir to local dir" 0 $?
  # make sure it really showed up there.
  okay=1
  if [ ! -d "$TEST_TEMP/foondir" ]; then
    okay=0
  fi
  assertEquals "Files showed up where expected" 1 $okay
} 

testMovingFileFromLocal()
{
  if ! fuse_supported; then return 0; fi
  # squelch complaints about ownership that are not germane on the fuse mount.
  mv "$TEST_TEMP/foondir" "$TESTING_DIR_ALTERNATE" 2>/dev/null
  assertEquals "Testing 'mv ./fuse-test $TESTING_DIR_ALTERNATE' from local dir to mounted dir" 0 $?
  ls -l "$HOME_DIR"
  ls -l "$TESTING_DIR_ALTERNATE"
}

testRemovingDirectory()
{
  if ! fuse_supported; then return 0; fi
  rmdir "$TESTING_DIR_ALTERNATE"
  assertEquals "Testing 'rmdir $TESTING_DIR_ALTERNATE' on mounted dir" 0 $?
  ls -l "$HOME_DIR"
  assertEquals "Testing ls on outer folder" 0 $?
}

testNewFileInGridShowsUp()
{
  if ! fuse_supported; then return 0; fi
  local newfile=new-file-testing-fuse-yo
  for ((i=0; i < $FUSE_COPY_COUNT; i++)); do
    local currfile=$newfile-$i
    grid echo this is a new file in the grid. \\\> $RNSPATH/$currfile
    assertEquals "Writing a new file in the grid" 0 $?
    # pause needed since fuse must see notification over network before it updates cache.
    sleep $FUSE_SNOOZE_TIME
    ls -1 "$HOME_DIR" | grep $currfile
    assertEquals "Testing that newly created file shows up in fuse directory listing" 0 $?
    grid rm $RNSPATH/$currfile
    assertEquals "Cleaning up newly created file" 0 $?
    # pause needed since fuse must see notification over network before it updates cache.
    sleep $FUSE_SNOOZE_TIME
    ls -1 "$HOME_DIR" | grep $currfile
    assertNotEquals "Testing that newly deleted file is missing in fuse directory listing" 0 $?
  done
}

testUnmountingFuseMount()
{
  if ! fuse_supported; then return 0; fi
  cd "$WORKDIR"  # change dir off fuse mount.
  \rm $TEST_TEMP/local-file.txt $TEST_TEMP/local-file1.txt 
  grid fuse --unmount local:"$MOUNT_POINT"
  retval=$?
  sleep 30
  assertEquals "Testing 'fuse --unmount' command" 0 $retval
  rmdir "$MOUNT_POINT"
  assertEquals "Checking that directory is no longer mounted" 0 $?
}

oneTimeTearDown()
{
  echo fuse job:
  jobs
}

# load and run shUnit2
source "$SHUNIT_DIR/shunit2"

