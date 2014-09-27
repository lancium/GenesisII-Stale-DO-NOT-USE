#!/bin/bash

##############

# bootstrap our information about the installation, starting with where it
# resides.
GENII_INSTALL_DIR="$1"; shift

##############

# load our helper scripts.

if [ ! -f "$GENII_INSTALL_DIR/scripts/installation_helpers.sh" ]; then
  echo "The installation_helpers.sh script could not be located in the existing"
  echo "installation.  This is most likely because this install was created with"
  echo "the Genesisi v2.7.499 installer or earlier.  Please upgrade to the latest"
  echo "Genesis 2.7.500+ interactive installer before proceeding."
  exit 1
fi

source "$GENII_INSTALL_DIR/scripts/installation_helpers.sh"

##############

replace_compiler_variables "$GENII_INSTALL_DIR/RELEASE"
replace_compiler_variables "$GENII_INSTALL_DIR/container.properties"

replace_installdir_variables "$GENII_INSTALL_DIR"

# make a link for the Container startup script.
rm -f "$GENII_INSTALL_DIR/GFFSContainer"
ln -s "$GENII_INSTALL_DIR/JavaServiceWrapper/wrapper/bin/GFFSContainer" "$GENII_INSTALL_DIR/GFFSContainer"

# cannot package multiple binary formats in rpm, so this code won't work.
# create the JNI directory for this platform.
#if [ ! -d $GENII_INSTALL_DIR/jni-lib ]; then
#  mkdir $GENII_INSTALL_DIR/jni-lib
#fi
#hostarch=$(arch)
#if [ "$hostarch" == x86_64 ]; then
#  cp -f $GENII_INSTALL_DIR/jni-libs/lin64/* $GENII_INSTALL_DIR/jni-lib/
#elif [ "$hostarch" == x86_32 \
#      -o "$hostarch" == i686 \
#      -o "$hostarch" == i586 ]; then
#  cp -f $GENII_INSTALL_DIR/jni-libs/lin32/* $GENII_INSTALL_DIR/jni-lib/
#fi

# clean up some older files.
\rm -rf "$GENII_INSTALL_DIR/ApplicationWatcher" "$GENII_INSTALL_DIR/XCGContainer" "$GENII_INSTALL_DIR/lib/gffs-container.jar" 

# set the permissions on our files properly.
find "$GENII_INSTALL_DIR" -type d -exec chmod -c a+rx "{}" ';' &>/dev/null
find "$GENII_INSTALL_DIR" -type f -exec chmod -c a+r "{}" ';' &>/dev/null
find "$GENII_INSTALL_DIR" -type f -iname "*.sh" -exec chmod -c a+rx "{}" ';' &>/dev/null

##############

echo "Finished preparing installation for GenesisII GFFS."
exit 0


