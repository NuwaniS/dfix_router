title = DirectFN FIX Router(Java 8)

echo OFF
set CLASSPATH=.;.\lib\DFN\*
set CLASSPATH=%CLASSPATH%;.\lib\*
rem ### JVM memory allocation pool parameters - modify as appropriate. ###
set "JAVA_OPTS=-Xms512M -Xmx1024M "

rem ######################## For Community JBoss - 5 ############
rem set CLASSPATH=%CLASSPATH%;.\lib\JBOSS_5\*

rem ######################## For Community JBoss - 6 ############
rem set CLASSPATH=%CLASSPATH%;.\lib\JBOSS_6\*

rem ######################## For EAP JBoss - 6 ############
rem set CLASSPATH=%CLASSPATH%;.\lib\JBOSS_6_EAP\*

rem ######################## For EAP JBoss - 7.3 ############
rem set CLASSPATH=%CLASSPATH%;.\lib\JBOSS_7.3_EAP\*

rem ######################## For EAP JBoss - 7.4 ############
set CLASSPATH=%CLASSPATH%;.\lib\JBOSS_7.4_EAP\*

rem ######################## For JBoss Wildfly ############
rem set CLASSPATH=%CLASSPATH%;.\lib\JBOSS_WILDFLY\*

rem ######################## For MQ9 Cluster ############
rem set CLASSPATH=%CLASSPATH%;.\lib\MQ9\*

rem ######################## For Web Sphere ############
rem set CLASSPATH=%CLASSPATH%;.\lib\WS\*
rem set "JAVA_OPTS=%JAVA_OPTS% -Dorg.omg.CORBA.ORBClass=com.ibm.CORBA.iiop.ORB -Dorg.omg.CORBA.ORBSingletonClass=com.ibm.rmi.corba.ORBSingleton -Djavax.rmi.CORBA.StubClass=com.ibm.rmi.javax.rmi.CORBA.StubDelegateImpl -Djavax.rmi.CORBA.PortableRemoteObjectClass=com.ibm.rmi.javax.rmi.PortableRemoteObject -Djavax.rmi.CORBA.UtilClass=com.ibm.ws.orb.WSUtilDelegateImpl -Dcom.ibm.CORBA.RequestTimeout=600"


set "JAVA_OPTS=%JAVA_OPTS% -Djavax.net.ssl.keyStore=system/keystore -Djavax.net.ssl.keyStorePassword=password"

set "JAVA_OPTS=%JAVA_OPTS% -XX:-UseGCOverheadLimit"

rem ### Sample JPDA settings for remote socket debugging ###
set "JAVA_OPTS=%JAVA_OPTS% -Xdebug -Xrunjdwp:transport=dt_socket,address=5005,server=y,suspend=n"

rem ### Setting for DFIXCluster ###
rem set "JAVA_OPTS=%JAVA_OPTS% -Djgroups.bind_addr=127.0.0.1 -Djgroups.tcpping.initial_hosts=127.0.0.1[7200],127.0.0.1[7201]"

set "JAVA_OPTS=%JAVA_OPTS% -DLog4jContextSelector=org.apache.logging.log4j.core.async.AsyncLoggerContextSelector"
set "JAVA_OPTS=%JAVA_OPTS% -Dorg.quickfixj.CharsetSupport.setCharSet=Windows-1256"

echo ON
echo %CLASSPATH%

java  %JAVA_OPTS% -classpath %CLASSPATH% com.mubasher.oms.dfixrouter.server.DFIXRouterManager
pause


