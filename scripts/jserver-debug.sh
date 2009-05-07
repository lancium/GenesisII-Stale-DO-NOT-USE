#!/bin/sh

_RC_GENII_INSTALL_DIR="%{INSTALL_PATH}"
_RC_LOCAL_JAVA_DIR="$_RC_GENII_INSTALL_DIR/Java/linux-i586/jre"

exec "$_RC_LOCAL_JAVA_DIR/bin/java" -agentlib:jdwp=transport=dt_socket,address=8000,server=y,suspend=n -Xms16M -Xmx512M -classpath "%{INSTALL_PATH}/lib:%{INSTALL_PATH}/ApplicationWatcher/app-manager.jar" "-Dlog4j.configuration=%{LOG4JCONFIG}" "-Djava.library.path=%{INSTALL_PATH}/jni-lib" "-Dedu.virginia.vcgr.genii.install-base-dir=%{INSTALL_PATH}" edu.virginia.vcgr.appmgr.launcher.ApplicationLauncher "%{INSTALL_PATH}/AppilcationWatcher/genii-ogrshserv-application.properties "$@"
