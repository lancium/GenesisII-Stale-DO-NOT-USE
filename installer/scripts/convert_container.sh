#!/bin/bash

# takes an existing container that was installed with the interactive installer
# and produces a newfangled container configuration that lives inside the user
# state directory.

##############

# major variables for the script:

# the property file we will supply with container configuration.
export INSTALLER_FILE="$GENII_USER_DIR/installation.properties"

if [ -f "$INSTALLER_FILE" ]; then
  echo "This script is intended to convert a container into the unified configuration"
  echo "format.  However, it appears that it has already been run because an"
  echo "installation.properties file already exists at:"
  echo "  $INSTALLER_FILE"
  echo "Please remove that if you are sure that you want to convert the container"
  echo "that was previously installed by the interactive installer."
  exit 1
fi

# where we will hide the owner certificate for the container.
export LOCAL_CERTS_DIR="$GENII_USER_DIR/certs"

# storage for our specialized java service wrapper config file.
export WRAPPER_DIR="$GENII_USER_DIR/wrapper"

##############

# tls keystore defaults.

# where we expect the container to get its TLS cert now.
export LOCAL_TLS_CERT="$LOCAL_CERTS_DIR/tls-cert.pfx"

##############

# signing keystore item defaults.

SIGNING_KEYSTORE_FILE_PROPERTY="signing-cert.pfx"
SIGNING_KEY_PASSWORD_PROPERTY="container"
SIGNING_KEYSTORE_PASSWORD_PROPERTY="container"
SIGNING_KEY_ALIAS_PROPERTY=Container

##############

# the container's signing cert will be stored here now.
export LOCAL_SIGNING_CERT="$LOCAL_CERTS_DIR/$SIGNING_KEYSTORE_FILE_PROPERTY"

if [ -z "$GENII_DEPLOYMENT_DIR" ]; then
  export GENII_DEPLOYMENT_DIR="$GENII_INSTALL_DIR/deployments"
fi

##############

function print_instructions()
{
  echo "This script can convert an older installation (installed by the interactive"
  echo "GenesisII installer) into the newer unified format where the container"
  echo "configuration is more self-contained and resides under the container's state"
  echo "directory (pointed at by the GENII_USER_DIR variable)."
  echo
  echo "The script requires that the GENII_INSTALL_DIR and GENII_USER_DIR are"
  echo "established as environment variables prior to converting the container."
  echo "Those variables should be set and made persistent for the user account, or"
  echo "there will be problems finding the right settings to run the container."
  echo "This can be accomplished by, for example, adding the variables to ~/.profile"
  echo "or ~/.bashrc like so (but using the actual install path):"
  echo "   export GENII_INSTALL_DIR=/opt/genesis2-xsede"
  echo "Note that for the conversion process, the GENII_INSTALL_DIR should point"
  echo "at the _newer_ installation that has been installed by the administrator"
  echo "(and that install can be either interactive or RPM/DEB based)."
  echo
  echo "The script requires the older installation directory as a parameter."
  echo "It is alright for this directory to be the same as the new GENII_INSTALL_DIR"
  echo "but both must be provided.  For example:"
  echo
  local scriptname="$(basename $0)"
  echo "$scriptname $HOME/GenesisII"
  echo
  echo "An additional parameter can be passed to skip the manual entry prompts;"
  echo "if the second parameter contains 'stop', then any stray container processes"
  echo "will automatically be stopped, and if it contains 'depcopy' then the existing"
  echo "deployments folder will automatically be copied to the state directory."
  echo "For example:"
  echo
  echo "$scriptname $HOME/GenesisII stop"
  echo "$scriptname $HOME/GenesisII stop+depcopy"
  echo "$scriptname $HOME/GenesisII depcopy"
  echo
}

##############

# validate the parameters we were given.

if [ -z "$GENII_USER_DIR" -o -z "$GENII_INSTALL_DIR" ]; then
  print_instructions
  echo
  if [ -z "$GENII_USER_DIR" ]; then
    echo "GENII_USER_DIR was not defined."
  fi
  if [ -z "$GENII_INSTALL_DIR" ]; then
    echo "GENII_INSTALL_DIR was not defined."
  fi
  exit 1
fi

# an extra check to make sure they're using the new installer as the GENII_INSTALL_DIR.
if [ ! -f "$GENII_INSTALL_DIR/current.version" \
    -o ! -f "$GENII_INSTALL_DIR/current.deployment" ]; then
  print_instructions
  echo
  echo "It appears that the GENII_INSTALL_DIR variable is not pointing at the"
  echo "newer installation.  Please set this variable to the location where the"
  echo "2.7.500+ installation is located."
  exit 1
fi

OLD_INSTALL="$1"; shift

if [ -z "$OLD_INSTALL" ]; then
  print_instructions
  echo
  echo The older install location was not passed on the command line.
  exit 1
fi

EXTRA_FLAGS="$1"; shift

if [[ "$EXTRA_FLAGS" =~ stop ]]; then
  STOP_CONTAINER=true
  echo The script will automatically zap any Genesis Java processes.
fi
if [[ "$EXTRA_FLAGS" =~ depcopy ]]; then
  COPY_DEPLOYMENTS=true
  echo The script will automatically copy the deployments folder.
fi

if [ -z "$TMP" -o ! -d "$TMP" ]; then
  # if no TMP is defined, we'll go with the standard.
  export TMP=/tmp
fi

export OLD_DEPLOYMENT_DIR="$OLD_INSTALL/deployments"

JAVA_PATH=$(which java)
if [ -z "$JAVA_PATH" ]; then
  print_instructions
  echo
  echo The GFFS container requires that Java be installed and be findable in the
  echo PATH.  The recommended JVM is the latest Java 7 available from Oracle.
  exit 1
fi

##############

# load our helper scripts.

if [ ! -f "$GENII_INSTALL_DIR/scripts/installation_helpers.sh" ]; then
  echo "The installation_helpers.sh script could not be located in the GenesisII"
  echo "installation, located in GENII_INSTALL_DIR, which is currently:"
  echo "  $GENII_INSTALL_DIR"
  echo "This is most likely because the current install was created with the"
  echo "Genesisi v2.7.499 installer or earlier.  Please upgrade to the latest"
  echo "Genesis 2.7.500+ interactive or RPM/DEB installer before proceeding."
  exit 1
fi

source "$GENII_INSTALL_DIR/scripts/installation_helpers.sh"

##############

# setup the config directories.

if [ ! -d "$GENII_USER_DIR" ]; then
  print_instructions
  echo
  echo "The GENII_USER_DIR does not exist yet!  There is no container configuration"
  echo "that can be converted currently.  Is this the right directory?:"
  echo "  $GENII_USER_DIR"
  exit 1
fi

if [ ! -f "$INSTALLER_FILE" ]; then
  # the file doesn't exist yet.  we'll write a simple template for it.
  echo -e \
    "# This file provides GFFS container configuration properties.\n" \
    > "$INSTALLER_FILE"
  if [ $? -ne 0 ]; then
    echo "Writing a default installer file failed!  Target is: $INSTALLER_FILE"
    exit 1
  fi
fi

# stop any running container to be sure we aren't changing config
# items while it's running.  this is a bit duplicative because we're trying
# to support converting a container where the install might be different
# but could actually be an already upgraded folder in the same place.
echo "Stopping any existing container before configuration proceeds..."
if [ -d "$WRAPPER_DIR" ]; then
  # if the wrapper dir exists already, we need to whack it so we can have
  # a clean shutdown of the running container.  this script is not meant
  # to convert an already converted container, but sometimes there are issues
  # while running it.  so we'll just clean up.
  rm -rf "$WRAPPER_DIR"
fi
tried_stopping=
if [ -f "$GENII_INSTALL_DIR/GFFSContainer" ]; then
  "$GENII_INSTALL_DIR/GFFSContainer" stop
  tried_stopping=true
fi
if [ -f "$GENII_INSTALL_DIR/XCGContainer" ]; then
  "$GENII_INSTALL_DIR/XCGContainer" stop
  # clean up this older file.
  \mv "$GENII_INSTALL_DIR/XCGContainer" "$GENII_INSTALL_DIR/XCGContainer.old"
  tried_stopping=true
fi

##############
# swap back to old installation to try its stop methods.  we switch the
# GENII_INSTALL_DIR variable to support the older install's scripts trying
# to use it.
holdInst="$GENII_INSTALL_DIR"
export GENII_INSTALL_DIR="$OLD_INSTALL"
if [ -f "$OLD_INSTALL/GFFSContainer" ]; then
  "$OLD_INSTALL/GFFSContainer" stop
  tried_stopping=true
fi
if [ -f "$OLD_INSTALL/XCGContainer" ]; then
  "$OLD_INSTALL/XCGContainer" stop
  # clean up this older file.
  \mv "$OLD_INSTALL/XCGContainer" "$OLD_INSTALL/XCGContainer.old"
  tried_stopping=true
fi
# and flip back to current day install.
export GENII_INSTALL_DIR="$holdInst"

if [ ! -z "$tried_stopping" ]; then
  echo Waiting for container to completely stop.
  sleep 5
fi

##############

# now make sure we really think they're all stopped, and give the user the option
# to force a shutdown of any containers we find running in their account.
list_temp="$(mktemp $TMP/javaprocesses.XXXXXX)"
bash "$GENII_INSTALL_DIR/xsede_tools/library/list_genesis_javas.sh" >"$list_temp"
if [ -s "$list_temp" ]; then

  if [ -z "$STOP_CONTAINER" ]; then
    echo
    echo "There are still Java processes running that we have identified as Genesis II"
    echo "containers:"
    cat "$list_temp"
    echo "The conversion process may fail if the container being converted is"
    echo "still running."
    echo "Would you like these processes to be shut down automatically? (Y/n)"
    read line
  else
    line=y
  fi
  stop_processes=
  if [ -z "$line" -o "$line" == "y" -o "$line" == "Y" ]; then
    stop_processes=true
  fi
  if [[ "$line" =~ [yY][eE][Ss] ]]; then
    stop_processes=true
  fi
  if [ ! -z "$stop_processes" ]; then
    echo "Stopping Genesis II Java processes..."
    bash "$GENII_INSTALL_DIR/xsede_tools/library/zap_genesis_javas.sh" >"$list_temp"
    echo "Processes have been stopped."
    echo
  fi
fi

##############

if [ ! -d "$WRAPPER_DIR" ]; then
  mkdir "$WRAPPER_DIR"
fi

##############

# load some of the variables from our config file.
context_file="$(retrieve_compiler_variable genii.deployment-context)"
new_dep="$(retrieve_compiler_variable genii.new-deployment)"
user_path="$(retrieve_compiler_variable genii.user-path)"

##############

# find the old deployment name.
var="edu.virginia.vcgr.genii.container.deployment-name"
file="$OLD_INSTALL/container.properties"
old_dep="$(seek_variable "$var" "$file")"
if [ -z "$old_dep" ]; then complain_re_missing_deployment_variable; fi

# find the hostname for the container.
var="edu.virginia.vcgr.genii.container.external-hostname-override"
file="$OLD_DEPLOYMENT_DIR/$old_dep/configuration/server-config.xml"
CONTAINER_HOSTNAME_PROPERTY="$(seek_variable_in_xml "$var" "$file")"
if [ -z "$CONTAINER_HOSTNAME_PROPERTY" ]; then
  # try again with the newest installation.
  echo "info: $var was missing in $file, trying alternate."
  file="$GENII_DEPLOYMENT_DIR/$new_dep/configuration/server-config.xml"
  CONTAINER_HOSTNAME_PROPERTY="$(seek_variable_in_xml "$var" "$file")"
echo got value = $CONTAINER_HOSTNAME_PROPERTY
  if [[ -z "$CONTAINER_HOSTNAME_PROPERTY" \
      || "$CONTAINER_HOSTNAME_PROPERTY" =~ .*installer:.* ]]; then
    # try again with the hostname command.
    echo "info: $var also missing in $file, falling back to hostname command."
    CONTAINER_HOSTNAME_PROPERTY="$(hostname -f)"
    echo "Moving forward with intuited hostname of '$CONTAINER_HOSTNAME_PROPERTY'."
  fi
fi

# find the network port.
var="edu.virginia.vcgr.genii.container.listen-port"
file="$OLD_DEPLOYMENT_DIR/$old_dep/configuration/web-container.properties"
CONTAINER_PORT_PROPERTY="$(seek_variable "$var" "$file")"
if [ -z "$CONTAINER_PORT_PROPERTY" ]; then complain_re_missing_deployment_variable; fi

# find the tls keystore file name.  we will canonicalize this to tls-cert.pfx.
var="edu.virginia.vcgr.genii.container.security.ssl.key-store"
file="$OLD_DEPLOYMENT_DIR/$old_dep/configuration/security.properties"
TLS_KEYSTORE_FILE_PROPERTY="$(seek_variable "$var" "$file")"
if [ -z "$TLS_KEYSTORE_FILE_PROPERTY" ]; then complain_re_missing_deployment_variable; fi
# add to the keystore file to get a full path.
TLS_KEYSTORE_FILE_PROPERTY="$OLD_DEPLOYMENT_DIR/$old_dep/security/$TLS_KEYSTORE_FILE_PROPERTY"
if [ ! -f "$TLS_KEYSTORE_FILE_PROPERTY" ]; then
  echo 
  echo -e "The file specified for a TLS keypair cannot be found:\n$TLS_KEYSTORE_FILE_PROPERTY"
  exit 1
fi

# find the password for the key in our tls keystore file.
var="edu.virginia.vcgr.genii.container.security.ssl.key-password"
file="$OLD_DEPLOYMENT_DIR/$old_dep/configuration/security.properties"
TLS_KEY_PASSWORD_PROPERTY="$(seek_variable "$var" "$file")"
if [ -z "$TLS_KEY_PASSWORD_PROPERTY" ]; then complain_re_missing_deployment_variable; fi

# find the password for the tls keystore file itself.
var="edu.virginia.vcgr.genii.container.security.ssl.key-store-password"
file="$OLD_DEPLOYMENT_DIR/$old_dep/configuration/security.properties"
TLS_KEYSTORE_PASSWORD_PROPERTY="$(seek_variable "$var" "$file")"
if [ -z "$TLS_KEYSTORE_PASSWORD_PROPERTY" ]; then complain_re_missing_deployment_variable; fi

# find the signing keystore file name.  we will canonicalize this to signing-cert.pfx.
var="edu.virginia.vcgr.genii.container.security.resource-identity.key-store"
file="$OLD_DEPLOYMENT_DIR/$old_dep/configuration/security.properties"
SIGNING_KEYSTORE_FILE_PROPERTY="$(seek_variable "$var" "$file")"
if [ -z "$SIGNING_KEYSTORE_FILE_PROPERTY" ]; then complain_re_missing_deployment_variable; fi
# make a full path out of the signing file.
SIGNING_KEYSTORE_FILE_PROPERTY="$OLD_DEPLOYMENT_DIR/$old_dep/security/$SIGNING_KEYSTORE_FILE_PROPERTY"
if [ ! -f "$SIGNING_KEYSTORE_FILE_PROPERTY" ]; then
  echo 
  echo -e "The file specified for a signing keypair cannot be found:\n$SIGNING_KEYSTORE_FILE_PROPERTY"
  exit 1
fi

# find the password for the key in our signing keystore file.
var="edu.virginia.vcgr.genii.container.security.resource-identity.key-password"
file="$OLD_DEPLOYMENT_DIR/$old_dep/configuration/security.properties"
SIGNING_KEY_PASSWORD_PROPERTY="$(seek_variable "$var" "$file")"
if [ -z "$SIGNING_KEY_PASSWORD_PROPERTY" ]; then complain_re_missing_deployment_variable; fi

# find the password for the signing keystore file itself.
var="edu.virginia.vcgr.genii.container.security.resource-identity.key-store-password"
file="$OLD_DEPLOYMENT_DIR/$old_dep/configuration/security.properties"
SIGNING_KEYSTORE_PASSWORD_PROPERTY="$(seek_variable "$var" "$file")"
if [ -z "$SIGNING_KEYSTORE_PASSWORD_PROPERTY" ]; then complain_re_missing_deployment_variable; fi

# find the alias for the key in the signing keystore.
var="edu.virginia.vcgr.genii.container.security.resource-identity.container-alias"
file="$OLD_DEPLOYMENT_DIR/$old_dep/configuration/security.properties"
SIGNING_KEY_ALIAS_PROPERTY="$(seek_variable "$var" "$file")"
if [ -z "$SIGNING_KEY_ALIAS_PROPERTY" ]; then complain_re_missing_deployment_variable; fi

##############

# now see if they want the deployments dir copied.
if [ -z "$COPY_DEPLOYMENTS" ]; then
  echo
  echo "You have the option of copying the installation's deployments directory"
  echo "into the container state directory.  This will save any specialized or"
  echo "modified deployment configuration for the container.  If your container"
  echo "uses the standard deployment provided by the installer (i.e., you haven't"
  echo "modified it and do not intend to), then this step is not needed."
  echo
  echo "Copy the installation deployments folder to the container's state directory? (y/N)"
  read line
else
  line=y
fi
copy_deployments_folder=
if [ "$line" == "y" -o "$line" == "Y" ]; then
  copy_deployments_folder=true
fi
if [[ "$line" =~ [yY][eE][Ss] ]]; then
  copy_deployments_folder=true
fi
if [ ! -z "$copy_deployments_folder" ]; then
  echo "Copying the installation's deployment folder now..."
  echo "from: '$OLD_DEPLOYMENT_DIR'"
  echo "to: '$GENII_USER_DIR/deployments'"
  # clean any existing folder out.
  if [ -e "$GENII_USER_DIR/deployments" ]; then
    \rm -rf "$GENII_USER_DIR/deployments"
  fi
  # do the copy.
  cp -R "$OLD_DEPLOYMENT_DIR" "$GENII_USER_DIR/deployments"
  if [ $? -ne 0 ]; then
    echo Copying the old deployments folder failed.
    exit 1
  fi
  save_def="$(mktemp -d "$GENII_USER_DIR/deployments/old-default.XXXXXX")"
  mv "$GENII_USER_DIR/deployments/default" "$save_def"
  if [ $? -ne 0 ]; then
    echo "Moving the old deployment's default folder out of the way failed."
    exit 1
  fi
  ln -s "$GENII_DEPLOYMENT_DIR/default" "$GENII_USER_DIR/deployments/default"
  if [ $? -ne 0 ]; then
    echo "Linking newer default deployment into place failed."
    exit 1
  fi

  # get a copy of the context file into the new location.
  cp "$GENII_DEPLOYMENT_DIR/$new_dep/$context_file" "$GENII_USER_DIR/deployments/$old_dep/$context_file"
  if [ $? -ne 0 ]; then
    echo "Copying context file into deployment folder failed."
    exit 1
  fi
  # set deployment dir now that we know it's available.
  export GENII_DEPLOYMENT_DIR="$GENII_USER_DIR/deployments"
  # reset the deployment name to use the old name.
  new_dep="$old_dep"

  # fix the server config if it still has the old database connection pool.
  sed -i -e 's/\.DatabaseConnectionPool/.ServerDatabaseConnectionPool/' "$GENII_DEPLOYMENT_DIR/$new_dep/configuration/server-config.xml"

  # add the new grid-certificates config line if it's not present.
  grep -q "edu.virginia.vcgr.genii.client.security.ssl.grid-certificates.location" "$GENII_DEPLOYMENT_DIR/$new_dep/configuration/security.properties"
  if [ $? -ne 0 ]; then
    # the security properties are missing the newer grid-certificates dir, so add that config line.
    echo "edu.virginia.vcgr.genii.client.security.ssl.grid-certificates.location=grid-certificates" >>"$GENII_DEPLOYMENT_DIR/$new_dep/configuration/security.properties"
    if [ ! -d "$GENII_DEPLOYMENT_DIR/$new_dep/security/grid-certificates" ]; then
      # create the referenced folder; it really shouldn't be there yet if config was missing.
      mkdir "$GENII_DEPLOYMENT_DIR/$new_dep/security/grid-certificates"
    fi
  fi

fi


##############

if [ -z "$copy_deployments_folder" ]; then

# first we'll make our storage folders.
if [ ! -d "$LOCAL_CERTS_DIR" ]; then
  mkdir "$LOCAL_CERTS_DIR"
fi

# get any existing kerberos settings.
echo Copying kerberos keytabs and settings, if found.
cp -R -n "$OLD_DEPLOYMENT_DIR/$old_dep/security"/*keytab* "$LOCAL_CERTS_DIR" 2>/dev/null
if [ $? -ne 0 ]; then
  echo no kerberos keytab files found.
fi

# grab any gffs kerberos settings from the security properties.
grep "^[ 	]*gffs-sts\.kerberos\." <"$OLD_DEPLOYMENT_DIR/$old_dep/configuration/security.properties" >>$INSTALLER_FILE
if [ $? -ne 0 ]; then
  echo no kerberos settings found.
fi

fi  # not copying deployment.

##############

echo "Calculated these values from existing deployment:"
dump_important_variables

##############

# write the config values we were given.
# we should have at least a blank installation property file now, but possibly
# one that is being reconfigured.  so let's replace the former values.

echo Writing configuration to installer file: $INSTALLER_FILE

if [ -z "$copy_deployments_folder" ]; then

# host and port.

replace_if_exists_or_add "$INSTALLER_FILE" "edu.virginia.vcgr.genii.container.external-hostname-override=.*" "edu.virginia.vcgr.genii.container.external-hostname-override=$CONTAINER_HOSTNAME_PROPERTY"

replace_if_exists_or_add "$INSTALLER_FILE" "edu.virginia.vcgr.genii.container.listen-port=.*" "edu.virginia.vcgr.genii.container.listen-port=$CONTAINER_PORT_PROPERTY"

# tls keystore info.

replace_if_exists_or_add "$INSTALLER_FILE" "edu.virginia.vcgr.genii.container.security.ssl.key-password=.*" "edu.virginia.vcgr.genii.container.security.ssl.key-password=$TLS_KEY_PASSWORD_PROPERTY"

replace_if_exists_or_add "$INSTALLER_FILE" "edu.virginia.vcgr.genii.container.security.ssl.key-store-password=.*" "edu.virginia.vcgr.genii.container.security.ssl.key-store-password=$TLS_KEYSTORE_PASSWORD_PROPERTY"

# signing keystore info.

replace_if_exists_or_add "$INSTALLER_FILE" "edu.virginia.vcgr.genii.container.security.resource-identity.key-password=.*" "edu.virginia.vcgr.genii.container.security.resource-identity.key-password=$SIGNING_KEY_PASSWORD_PROPERTY"

replace_if_exists_or_add "$INSTALLER_FILE" "edu.virginia.vcgr.genii.container.security.resource-identity.key-store-password=.*" "edu.virginia.vcgr.genii.container.security.resource-identity.key-store-password=$SIGNING_KEYSTORE_PASSWORD_PROPERTY"

replace_if_exists_or_add "$INSTALLER_FILE" "edu.virginia.vcgr.genii.container.security.resource-identity.container-alias=.*" "edu.virginia.vcgr.genii.container.security.resource-identity.container-alias=$SIGNING_KEY_ALIAS_PROPERTY"

fi  # not copying deployments folder.

##############

# add in the values we provide defaults for; these can be overridden by people
# if needed, although we will slam defaults back in there if they run the
# convert container script again.

if [ -z "$copy_deployments_folder" ]; then

replace_if_exists_or_add "$INSTALLER_FILE" "edu.virginia.vcgr.genii.container.security.default-owners=.*" "edu.virginia.vcgr.genii.container.security.certs-dir=$LOCAL_CERTS_DIR"

replace_if_exists_or_add "$INSTALLER_FILE" "edu.virginia.vcgr.genii.container.security.ssl.key-store=.*" "edu.virginia.vcgr.genii.container.security.ssl.key-store=$(basename $LOCAL_TLS_CERT)"

replace_if_exists_or_add "$INSTALLER_FILE" "edu.virginia.vcgr.genii.container.security.ssl.key-store-type=.*" "edu.virginia.vcgr.genii.container.security.ssl.key-store-type=PKCS12"

replace_if_exists_or_add "$INSTALLER_FILE" "edu.virginia.vcgr.genii.container.security.resource-identity.key-store=.*" "edu.virginia.vcgr.genii.container.security.resource-identity.key-store=$(basename $LOCAL_SIGNING_CERT)"

replace_if_exists_or_add "$INSTALLER_FILE" "edu.virginia.vcgr.genii.container.security.resource-identity.key-store-type=.*" "edu.virginia.vcgr.genii.container.security.resource-identity.key-store-type=PKCS12"

fi  # not copying deployments folder.

# deployment info gets hooked in with the latest information (even when we copied deployments).

replace_if_exists_or_add "$INSTALLER_FILE" "edu.virginia.vcgr.genii.gridInitCommand=.*" "edu.virginia.vcgr.genii.gridInitCommand=\"local:$GENII_DEPLOYMENT_DIR/$new_dep/$context_file\" \"$new_dep\""

replace_if_exists_or_add "$INSTALLER_FILE" "edu.virginia.vcgr.genii.container.deployment-name=.*" "edu.virginia.vcgr.genii.container.deployment-name=$new_dep"

##############

# set up the service wrapper configuration.
if [ ! -f "$WRAPPER_DIR/wrapper.conf" ]; then
  cp "$GENII_INSTALL_DIR/JavaServiceWrapper/wrapper/conf/wrapper.conf" "$WRAPPER_DIR/wrapper.conf"
fi

# we need to calculate the JRE directory for java, since our config file has
# "bin/java" in it already.
JRE_DIR="$( dirname $(dirname $JAVA_PATH) )"
replace_phrase_in_file "$WRAPPER_DIR/wrapper.conf" "\${installer:sys.preferredJre}" "$JRE_DIR"

# plug in a default amount of memory.  this can be adjusted manually if desired.
replace_phrase_in_file "$WRAPPER_DIR/wrapper.conf" "\${installer:genii.container-memory}" "2048"

# fix the logging directory for the service wrapper.
replace_phrase_in_file "$WRAPPER_DIR/wrapper.conf" "wrapper.logfile=.*" "wrapper.logfile=$HOME/.GenesisII/wrapper.log"

##############

if [ -z "$copy_deployments_folder" ]; then

# set up the TLS certificate.
echo "Copying TLS certificate for container..."
cp -f "$TLS_KEYSTORE_FILE_PROPERTY" "$LOCAL_TLS_CERT"
if [ $? -ne 0 ]; then
  echo "Failed to copy the TLS keypair into place!"
  echo "Tried copying $TLS_KEYSTORE_FILE_PROPERTY into $LOCAL_TLS_CERT"
  exit 1
fi

# set up the signing certificate.
echo "Copying signing certificate for container..."
cp -f "$SIGNING_KEYSTORE_FILE_PROPERTY" "$LOCAL_SIGNING_CERT"
if [ $? -ne 0 ]; then
  echo "Failed to copy the signing keypair into place!"
  echo "Tried copying $SIGNING_KEYSTORE_FILE_PROPERTY into $LOCAL_SIGNING_CERT"
  exit 1
fi

fi  # not copying deployments folder.

##############

# create a service-url file for this container.
echo "https://$CONTAINER_HOSTNAME_PROPERTY:$CONTAINER_PORT_PROPERTY/axis/services/VCGRContainerPortType" >"$GENII_USER_DIR/service-url.txt"

##############

if [ -z "$copy_deployments_folder" ]; then

# get the owner's certificate.
echo "Copying admin certificate for container if exists..."
cp "$OLD_DEPLOYMENT_DIR/$old_dep/security/admin.cer" "$LOCAL_CERTS_DIR/admin.cer" 2>/dev/null
#don't care if it did not exist since it is optional.

echo "Copying owner certificate for container..."
cp "$OLD_DEPLOYMENT_DIR/$old_dep/security/owner.cer" "$LOCAL_CERTS_DIR/owner.cer" 2>/dev/null
if [ $? -ne 0 ]; then
  # try again with admin cert.
  cp "$OLD_DEPLOYMENT_DIR/$old_dep/security/admin.cer" "$LOCAL_CERTS_DIR/owner.cer"
  if [ $? -ne 0 ]; then
    echo "Failed to copy the owner certificate from the existing container."
    echo "This should be located at:"
    echo "  $OLD_DEPLOYMENT_DIR/$old_dep/security/owner.cer"
    echo "or an admin certificate should be at:"
    echo "  $OLD_DEPLOYMENT_DIR/$old_dep/security/admin.cer"
    exit 1
  fi
fi
if [ ! -d "$LOCAL_CERTS_DIR/default-owners" ]; then
  mkdir "$LOCAL_CERTS_DIR/default-owners"
  if [ $? -ne 0 ]; then
    echo "Failed to create the default-owners folder in the local certs dir."
    exit 1
  fi
fi
cp "$LOCAL_CERTS_DIR/owner.cer" "$LOCAL_CERTS_DIR/default-owners"
if [ $? -ne 0 ]; then
  echo "Failed to copy the owner certificate into the default-owners folder."
  exit 1
fi

fi  # not copying deployments folder.

##############

# now do some heavy-weight operations where we actually use the gffs software.

echo Connecting to the grid...
# clean up any prior context information.
rm -f "$GENII_USER_DIR/user-config.xml"
# get connected to the grid.
"$GENII_INSTALL_DIR/grid" connect "local:$GENII_DEPLOYMENT_DIR/$new_dep/$context_file" "$new_dep"
if [ $? -ne 0 ]; then
  echo "Failed to connect to the grid!"
  echo "There may be more information in: ~/.GenesisII/grid-client.log"
  exit 1
fi
echo "Connection to grid succeeded."
echo

echo
echo Done converting your container to a unified configuration.
echo
echo The service URL for your container is stored in:
echo "$GENII_USER_DIR/service-url.txt"
echo
echo You can start the container service with:
echo "$GENII_INSTALL_DIR/GFFSContainer start"
echo

