if [ ! -z "$JAVA_HOME" ] ; then 
    JAVA=$JAVA_HOME/bin/java
else
    JAVA=`which java`
fi

if [ ! -x "$JAVA" ] ; then
  echo Cannot find java. Set JAVA_HOME or add java to path.
  exit 1
fi

if [[ ! `ls alien4cloud-ui-${alien.version}.war 2> /dev/null` ]] ; then
  if [[ ! `ls alien4cloud-standalone/alien4cloud-ui-${alien.version}.war 2> /dev/null` ]] ; then
    echo Command must be run from the directory where the WAR is installed or its parent.
    exit 4
  fi
  cd alien4cloud-standalone
fi

if [ -z "$JAVA_OPTS" ] ; then
    JAVA_OPTS="-Xms256m -Xmx1024m -XX:MaxPermSize=1024m"
fi

#    java -jar target/dependency/jetty-runner.jar target/*.war
$JAVA ${JAVA_OPTS} \
    -jar alien4cloud-ui-${alien.version}.war \
    "$@"
