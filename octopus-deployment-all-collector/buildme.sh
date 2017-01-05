#!/bin/bash
mvn clean install -DskipTests -Dpmd.skip
cp octopus.template target/application.properties
echo "Configuring Octopus Environment collector"
wget $1/d4dMastersCICD/readmasterjsonnew/28 -O target/temp.properties
cat target/temp.properties >> target/application.properties
echo "dbhost="$2 >> target/application.properties
#echo "dbhost=localhost" >> target/application.properties
cd target
cat application.properties
 java -jar octopus-deployment-all-collector-2.0.2-SNAPSHOT.jar
#tail -f nohup.out