@echo off
echo Starting gRPC Client...
echo.
call mvn exec:java -Dexec.mainClass="com.example.grpc.client.GrpcClient"
