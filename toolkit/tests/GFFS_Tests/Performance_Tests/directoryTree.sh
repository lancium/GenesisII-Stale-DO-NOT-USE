#!/bin/bash

#Author: Vanamala Venkataswamy
#mods: Chris Koeritz

export WORKDIR="$( \cd "$(\dirname "$0")" && \pwd )"  # obtain the script's working directory.
cd "$WORKDIR"

if [ -z "$GFFS_TOOLKIT_SENTINEL" ]; then echo Please run prepare_tools.sh before testing.; exit 3; fi
source "$GFFS_TOOLKIT_ROOT/library/establish_environment.sh"

modifier="$1"; shift
if [ "$modifier" == "abusive" ]; then
  ABUSIVE_MODE=true
fi

# where we will export a part of rns space from the grid.
export TEST_AREA="$EXPORTPATH"
# where we will fuse mount the grid locally.
export MOUNT_POINT="$TEST_TEMP/mount-directoryTree"
# the place in the grid where the exported directory will appear.
export FULL_EXPORT_PATH="$RNSPATH/export-local"

# this is non-empty if the directory is expected to be local.
local_export=yes

oneTimeSetUp()
{
  sanity_test_and_init  # make sure test environment is good.
}

testCleanupPriorTestRuns()
{
  # remove any previous mount point.
  fusermount -u "$MOUNT_POINT" &>/dev/null
  if [ -e "$MOUNT_POINT" ]; then rmdir "$MOUNT_POINT"; fi
  # take out prior exports too.
  grid ls $FULL_EXPORT_PATH &>/dev/null
  if [ $? == 0 ]; then
    grid export --quit $FULL_EXPORT_PATH
    if [ $? -ne 0 ]; then
      echo "The grid unlink attempt on $FULL_EXPORT_PATH failed although we"
      echo "believe the file is present.  This is not a good sign."
    fi
    # now clean it up regardless, if it's still there.
    grid unlink $FULL_EXPORT_PATH &>/dev/null
  fi
  # trash any older test directories that were left lying around.
  rm -rf $TEST_TEMP/testDir
  grid rm -rf ${RNSPATH}/testDir &>/dev/null
  # create a new mount point.
  mkdir "$MOUNT_POINT"
  assertEquals "Making mount point directory at $MOUNT_POINT" 0 $?

  # create the export path, if we can.  this should be a pre-existing directory,
  # and this creation attempt will only work if we're doing a simple bootstrap test.
  if [ ! -d "$TEST_AREA" ]; then
    echo "Note: we are creating an export path at $TEST_AREA.  This will not succeed unless"
    echo "this is a simple bootstrap test or the container is local to the test machine."
    mkdir -p "$TEST_AREA"
    if [ $? -ne 0 ]; then
      echo "The local make directory operation failed, and the directory for EXPORTPATH"
      echo "did not exist at $EXPORTPATH.  This configuration will have problems if this"
      echo "is a bootstrapped test!  A real export test against a remote container could"
      echo "still work if the path exists locally there."
      # remove the thought that we might have a local export we can look at.
      unset local_export
    fi
  else
    # test area already existed, so make sure nothing was left behind.
    if [ -d "$TEST_AREA/EMS_Tests" ]; then
      rm -rf "$TEST_AREA/EMS_Tests"
      assertEquals "Cleaning EMS_Tests from export area" 0 $?
    fi
    if [ -d "$TEST_AREA/testDir" ]; then
      rm -rf "$TEST_AREA/testDir"
      assertEquals "Cleaning testDir from export area" 0 $?
    fi
  fi
}

testCreateExport() {
  if ! fuse_supported; then return 0; fi
  # Create an export on the container from our test area.
  grid export --create $CONTAINERPATH/Services/LightWeightExportPortType local:$TEST_AREA grid:$FULL_EXPORT_PATH
  assertEquals "Creating export on $CONTAINERPATH, local path $TEST_AREA, at $FULL_EXPORT_PATH" 0 $?
  cat $GRID_OUTPUT_FILE
}

# moved to just after export creation to expedite this test.
testRecursiveCopyAndDeleteOnExport()
{
  if ! fuse_supported; then return 0; fi
  grid cp -r local:"$GFFS_TOOLKIT_ROOT/tests/EMS_Tests" $FULL_EXPORT_PATH
  assertEquals "copy directory recursively to export path" 0 $?
  grid ls $FULL_EXPORT_PATH/EMS_Tests/besFunctionality &>/dev/null
  assertEquals "directory is present on export path afterwards" 0 $?

  if [ ! -z "$local_export" ]; then
    if [ -d $TEST_AREA/EMS_Tests/besFunctionality -a -d $TEST_AREA/EMS_Tests/faultJobsTests ]; then
      true
    else
      false
    fi
    assertEquals "certain directories are present on real filesystem of export" 0 $?
  fi

  grid rm -r $FULL_EXPORT_PATH/EMS_Tests
  assertEquals "remove copied directory from export path" 0 $?
  grid ls $FULL_EXPORT_PATH/EMS_Tests &>/dev/null
  assertNotEquals "directory really should be gone after removal" 0 $?
}

testFuseMount () {
  if ! fuse_supported; then return 0; fi
  # Create a grid mount point, mount the grid
echo mount point is $MOUNT_POINT
  fuse --mount local:"$MOUNT_POINT"
  sleep 20

  test_fuse_mount "$MOUNT_POINT"
  check_if_failed "Mounting grid to local directory"

  grid ls
  cat $GRID_OUTPUT_FILE
}

testCreateDirectory () {
  if ! fuse_supported; then return 0; fi
  if [ -z "$ABUSIVE_MODE" ]; then
    # normal strength fanned out directory creation.
    fan_out_directories $TEST_TEMP/testDir 3 6 3
  else
    # harshness, an abusively wide space of directory fanning out.
    fan_out_directories $TEST_TEMP/testDir 18 16 14
  fi
}

testListingExportViaFuse()
{
  if ! fuse_supported; then return 0; fi
  grid echo "This is a line of text of a certain length" \\\> $FULL_EXPORT_PATH/length-test-1
  grid echo "line of different length" \\\> $FULL_EXPORT_PATH/length-test-2
  grid echo "shorty" \\\> $FULL_EXPORT_PATH/length-test-3
  local outfile="$(mktemp "$TEST_TEMP/export-test-out.XXXXXX")"
  \ls -1al "$MOUNT_POINT/$FULL_EXPORT_PATH" | grep length-test >"$outfile"
  size1=$(cat "$outfile" | grep length-test-1 | awk '{print $5'})
  size2=$(cat "$outfile" | grep length-test-2 | awk '{print $5'})
  size3=$(cat "$outfile" | grep length-test-3 | awk '{print $5'})
  #echo "size1 = $size1   size2 = $size2   size3 = $size3"
  assertNotEquals "test for unique sizes on export viewed via fuse (1 vs 2)" $size1 $size2
  assertNotEquals "test for unique sizes on export viewed via fuse (1 vs 3)" $size1 $size3
  assertNotEquals "test for unique sizes on export viewed via fuse (2 vs 3)" $size2 $size3
}

testFuseRecursiveCp() {
  if ! fuse_supported; then return 0; fi
  # Recursively copy files from the test directory into the target.
  time cp -r $TEST_TEMP/testDir "$MOUNT_POINT/$RNSPATH" 
  assertEquals "Recursively copying from testDir to $MOUNT_POINT/$RNSPATH" 0 $?
  # Then ls -lR the directory (not yet: and count the number of lines)
  time ls -lR "$MOUNT_POINT/$RNSPATH" &>/dev/null
  assertEquals "Recursively listing the copied files in testDir to $MOUNT_POINT/$RNSPATh" 0 $?
}

testFuseRecursiveCpOntoExport()
{
  if ! fuse_supported; then return 0; fi
  # Recursively copy files from the test directory into the exported folder.
  time cp -r $TEST_TEMP/testDir "$MOUNT_POINT/$FULL_EXPORT_PATH" 
  assertEquals "Recursively copying from testDir to $MOUNT_POINT/$FULL_EXPORT_PATH" 0 $?
  echo "Recursively listing the copied files in $MOUNT_POINT/$FULL_EXPORT_PATH"
  time ls -lR "$MOUNT_POINT/$FULL_EXPORT_PATH" &>/dev/null
  assertEquals "directory copied to export was listable" 0 $?
}

testRemovingTestDir()
{
  if ! fuse_supported; then return 0; fi
  grid rm -r ${RNSPATH}/testDir
  assertEquals "cleaning up test directory in RNS" 0 $?

#turn these on once we identify issues in this test on 126 test vms.
#  rm -rf "$MOUNT_POINT/$FULL_EXPORT_PATH"
#  assertEquals "cleaning up test directory in exported path" 0 $?
}

oneTimeTearDown() {
  fusermount -u "$MOUNT_POINT" &>/dev/null
  rmdir "$MOUNT_POINT"
  rm -rf $TEST_TEMP/testDir
  grid export --quit $FULL_EXPORT_PATH &>/dev/null
}

# load and run shUnit2
source "$SHUNIT_DIR/shunit2"

