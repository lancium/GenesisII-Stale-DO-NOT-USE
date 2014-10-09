#!/bin/bash

# Creates an archive from a build of the GenesisII codebase.
#
# Author: Chris Koeritz

export WORKDIR="$( \cd "$(\dirname "$0")" && \pwd )"  # obtain the script's working directory.

build_folder="$1"; shift
storage_folder="$1"; shift
additional_tag="$1"; shift  # optional
replacement_name="$1"; shift  # optional
if [ ! -d "$build_folder" -o ! -d "$storage_folder" ]; then
  echo This script packs up a build folder after a GenesisII build.
  echo It needs two folders: the first pointing at where the build resides, and
  echo the second specifying where to store the archive.
  exit 1
fi
if [ ! -z "$additional_tag" ]; then
  # add a separator character.
  additional_tag="-$additional_tag"
fi

pushd $build_folder &>/dev/null

date_string="$(date +"%Y_%b_%e_%H%M" | sed -e 's/ //g')"
#echo date_string is $date_string

CREATED_FILENAME="GenesisII-build-${date_string}"
if [ ! -z "$replacement_name" ]; then
  # drop the default name if they gave us one.
  CREATED_FILENAME="$replacement_name"
fi

EXCLUDES=(--exclude=".svn" --exclude="*.class" --exclude="*.log" --exclude="*.log.*" --exclude="Gene*tar.gz" --exclude="*.fred")

tar -czf "$storage_folder/${CREATED_FILENAME}${additional_tag}.tar.gz" * ${EXCLUDES[*]}

popd &>/dev/null


