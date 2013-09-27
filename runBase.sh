#!/bin/bash

# NOTE:: This is a q/d script
#REM First 2 paths are probably out of date: square with the uncommented path before trying
#REM Yes it would be better to define this as a var
#REM

#REM No debug
#REM java -cp ..\..\lib\groovy-all-1.7.2.jar:lib/httpclient-4.1.2.jar:lib/httpcore-4.1.2.jar:lib/commons-logging-1.1.1.jar:lib/opencsv-2.3.jar:lib/json-lib-2.4-jdk15.jar:lib/commons-lang-2.6.jar:lib/ezmorph-1.0.6.jar:lib/commons-collections-3.2.1.jar:lib/commons-beanutils-1.8.3.jar groovy.lang.GroovyShell load.groovy %*

#REM Debug of Context and RequestHeaders
#REM java -cp ..\..\lib\groovy-all-1.7.2.jar:lib/httpclient-4.1.2.jar:lib/httpcore-4.1.2.jar:lib/commons-logging-1.1.1.jar:lib/opencsv-2.3.jar:lib/json-lib-2.4-jdk15.jar:lib/commons-lang-2.6.jar:lib/ezmorph-1.0.6.jar:lib/commons-collections-3.2.1.jar:lib/commons-beanutils-1.8.3.jar -Dorg.apache.commons.logging.Log=org.apache.commons.logging.impl.SimpleLog -Dorg.apache.commons.logging.simplelog.showdatetime=true -Dorg.apache.commons.logging.simplelog.log.org.apache.http=DEBUG -Dorg.apache.commons.logging.simplelog.log.org.apache.http.wire=ERROR groovy.lang.GroovyShell load.groovy %*

#REM Full debug of Context and request parameters
java -Djavax.net.debug=none -cp lib/groovy-all-1.7.8.jar:lib/httpclient-4.1.2.jar:lib/httpcore-4.1.2.jar:lib/commons-logging-1.1.1.jar:lib/opencsv-2.3.jar:lib/json-lib-2.4-jdk15.jar:lib/commons-lang-2.6.jar:lib/ezmorph-1.0.6.jar:lib/commons-collections-3.2.1.jar:lib/commons-beanutils-1.8.3.jar -Dorg.apache.commons.logging.Log=org.apache.commons.logging.impl.SimpleLog -Dorg.apache.commons.logging.simplelog.showdatetime=true -Dorg.apache.commons.logging.simplelog.log.org.apache.http=DEBUG groovy.lang.GroovyShell load.groovy $*
