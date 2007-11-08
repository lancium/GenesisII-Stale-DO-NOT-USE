#!/bin/sh

exec java -Xms32M -Xmx128M -classpath "@JSERVER_DIRECTORY@/ext/bouncycastle/bcprov-jdk15-133.jar:@JSERVER_DIRECTORY@/ext/genii/GenesisII-security.jar:@JSERVER_DIRECTORY@/ext/genii/morgan-utilities.jar:@JSERVER_DIRECTORY@/lib:@JSERVER_DIRECTORY@/security" -Dlog4j.configuration=log4j.properties "-Dedu.virginia.vcgr.genii.install-base-dir=@JSERVER_DIRECTORY@" "-Dedu.virginia.vcgr.genii.client.configuration.base-dir=@JSERVER_DIRECTORY@" "-Dedu.virginia.vcgr.genii.client.configuration.user-dir=$GENII_USER_DIR" org.morgan.util.launcher.Launcher "@JSERVER_DIRECTORY@/jar-desc.xml" edu.virginia.vcgr.ogrsh.server.OGRSHServer "$@"
