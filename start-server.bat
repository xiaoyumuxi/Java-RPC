@echo off
echo Starting gRPC Server...
echo.
call mvn exec:java -Dexec.mainClass="com.example.grpc.server.GrpcServer"
