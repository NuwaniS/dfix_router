title = DFIXRouter License Generator.

echo OFF
set CLASSPATH=.;.\lib\DFN\*
set CLASSPATH=%CLASSPATH%;.\lib\*
set "JAVA_OPTS=-Xdebug -Xrunjdwp:transport=dt_socket,address=5005,server=y,suspend=n"

java %JAVA_OPTS% -classpath %CLASSPATH% com.mubasher.dfix.license.DataCollector
pause