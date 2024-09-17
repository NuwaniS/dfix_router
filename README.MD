# DFIX 
DFIXRouter is a Component of DFN(DirectFN Pvt Ltd) which is used to route the Exchange Messages to OMS(Order Management System) and Working as a Simulator for Transaction received from OMS.
### If developing in Java 8 environment 
- you can only build for jdk 8 
- no additional configurations needed just run `mvn clean install`


### If developing in Java 17 environment
- update `JDK_VERSION` property in main pom.xml to J17
- update `JDK` property in main pom.xml to 17
- you can build for both jdk 17 and jdk 8 ( and any other version in between (not verified))
- to build for jdk 17 run `mvn clean install -Dmaven.compiler.release=17 -DJDK_VERSION=J17`
- to build for jdk 8 run `mvn clean install -Dmaven.compiler.release=8 -DJDK_VERSION=J8`


### To get dependency vulnerability report run `mvn dependency-check:aggregate`


### Under `./dfix/target` , 
- `DFIXRTR_X_X_X_<V>.<vvv>.<vv>.<v>+<J17/J8>.jar` is the release obfuscated build.
- `DFIXRTR_X_X_X_<V>.<vvv>.<vv>.<v>+<J17/J8>_yguard.jar` is the un-obfuscated build ( not for sharing).
- `DFIXRTR_X_X_X_<V>.<vvv>.<vv>.<v>+<J17/J8>_renamelog.xml` contains the obfuscate mapping  ( not for sharing).

JDK_17 Branch : 
NOTE  : 
- If you add or modify any dependency/plugin , verify development can be continued in both java 8 and 17 env , 
release can be built for both java 8 and 17 env 
- make sure to switch falcon version to the respective development env ( java 8 or 17 ) 

### Please refer below links for release notes and runtime and libraries
- Release Notes : https://directfn-fiber.atlassian.net/wiki/spaces/PROD/pages/121077800/Release+Notes+DFIX
- Runtime : https://directfn-fiber.atlassian.net/wiki/spaces/PROD/pages/121667590/Runtimes+DFIX
- Libraries : https://jfrog.directfn.net/artifactory/dfn_reusables/Componant_Runtimes/NTP/ntp_dfix/

#### if get java.lang.reflect.InaccessibleObjectException in JDK 17 uncomment below line
-  Error : Unable to make {member} accessible: module {A} does not 'opens {package}' to {B}") ,on Java >=9
- If error appear in Tests add below line to surfire plugin (only include required packages)
```<argLine>--add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.lang.reflect=ALL-UNNAMED --add-opens java.base/java.net=ALL-UNNAMED --add-opens java.base/java.io=ALL-UNNAMED --add-opens java.base/sun.security.rsa=ALL-UNNAMED --add-opens java.base/sun.security.pkcs=ALL-UNNAMED --add-opens java.base/sun.security.x509=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/java.util.stream=ALL-UNNAMED --add-opens java.base/jdk.internal.loader=ALL-UNNAMED --add-opens java.base/java.time=ALL-UNNAMED --add-opens java.xml/jdk.xml.internal=ALL-UNNAMED </argLine>```
- IF error appear in runtime add --add-opens java option as shown below
```java --add-opens java.base/java.nio=ALL-UNNAMED --add-opens java.base/jdk.internal.misc=ALL-UNNAMED $JAVA_OPTS -classpath $CLASSPATH com.mubasher.oms.dfixrouter.server.DFIXRouterManager ```
