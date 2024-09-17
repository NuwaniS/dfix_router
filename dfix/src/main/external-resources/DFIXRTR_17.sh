#!/bin/bash
#(Java 17)
#JBOSS_5 = 0, JBOSS_6 = 1, JBOSS_6_EAP = 2, JBOSS_7.3_EAP = 3, JBOSS_7.4_EAP = 4 , WS = 5 ,MQ9=6
SERVERS=(JBOSS_5 JBOSS_6 JBOSS_6_EAP JBOSS_7.3_EAP JBOSS_7.4_EAP WS MQ9)
SERVER_TYPE=4

JAVA_OPTS="-Xms512M -Xmx1024M -XX:-UseGCOverheadLimit"
##### Setting for DFIXCluster ###
#JAVA_OPTS="$JAVA_OPTS -Djgroups.bind_addr=127.0.0.1 -Djgroups.tcpping.initial_hosts=127.0.0.1[7200],127.0.0.1[7201]"


cd $(dirname $0)
CLASSPATH=.
for lib_jar in ./lib17/*.jar; do  CLASSPATH=${CLASSPATH}:${lib_jar}; done
for lib_jar in ./lib17/${SERVERS[${SERVER_TYPE}]}/*.jar; do  CLASSPATH=${CLASSPATH}:${lib_jar}; done
for lib_jar in ./lib17/DFN/*.jar; do  CLASSPATH=${CLASSPATH}:${lib_jar}; done

JAVA_OPTS="$JAVA_OPTS -DLog4jContextSelector=org.apache.logging.log4j.core.async.AsyncLoggerContextSelector"

export CLASSPATH

JAVA_OPTS="$JAVA_OPTS -Dcom.ibm.mq.cfg.useIBMCipherMappings=false "

if [ $SERVER_TYPE == 5 ]
then
JAVA_OPTS="$JAVA_OPTS -Dorg.omg.CORBA.ORBClass=com.ibm.CORBA.iiop.ORB -Dorg.omg.CORBA.ORBSingletonClass=com.ibm.rmi.corba.ORBSingleton -Djavax.rmi.CORBA.StubClass=com.ibm.rmi.javax.rmi.CORBA.StubDelegateImpl -Djavax.rmi.CORBA.PortableRemoteObjectClass=com.ibm.rmi.javax.rmi.PortableRemoteObject -Djavax.rmi.CORBA.UtilClass=com.ibm.ws.orb.WSUtilDelegateImpl -Dcom.ibm.CORBA.RequestTimeout=600"
fi

echo ${SERVERS[${SERVER_TYPE}]}
echo $JAVA_HOME

java --add-opens java.base/java.nio=ALL-UNNAMED --add-opens java.base/jdk.internal.misc=ALL-UNNAMED  $JAVA_OPTS -classpath $CLASSPATH com.mubasher.oms.dfixrouter.server.DFIXRouterManager
# > nohup.out 2>&1 &
