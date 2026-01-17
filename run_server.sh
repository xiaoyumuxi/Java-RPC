#!/bin/bash

# Ensure modules are built
# mvn clean package -DskipTests

# Run the provider using generated JARs and dependency classpath
echo "Starting RPC Provider..."
java -cp rpc-provider/target/rpc-provider-1.0-SNAPSHOT.jar:rpc-core/target/rpc-core-1.0-SNAPSHOT.jar:rpc-common/target/rpc-common-1.0-SNAPSHOT.jar:rpc-api/target/rpc-api-1.0-SNAPSHOT.jar:$(mvn -q dependency:build-classpath -Dmdep.outputFile=/dev/stdout -pl rpc-provider -am) com.xiaoyu.rpc.provider.ProviderApp
