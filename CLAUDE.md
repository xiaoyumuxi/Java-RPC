# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Java RPC (Remote Procedure Call) framework implementation project. The project appears to be in its initial setup phase.

## Setup and Initialization

Since this is a new Java project, you'll need to:

1. Initialize a Maven or Gradle project structure
2. Add necessary dependencies for RPC functionality (likely gRPC, Apache Thrift, or a custom RPC framework)
3. Set up proper package structure following Java conventions

## Common Development Commands

Once the project is properly initialized with Maven:

```bash
# Build the project
mvn clean compile

# Run tests
mvn test

# Run a specific test
mvn test -Dtest=TestClassName

# Package the project
mvn clean package

# Install to local repository
mvn clean install
```

If using Gradle:

```bash
# Build the project
./gradlew build

# Run tests
./gradlew test

# Run a specific test
./gradlew test --tests TestClassName

# Build JAR
./gradlew jar
```

## Architecture Guidelines

When implementing the RPC framework:

1. **Service Definitions**: Define service interfaces using Protocol Buffers (for gRPC) or Thrift IDL
2. **Server Implementation**: Implement service handlers that extend generated stub classes
3. **Client Implementation**: Create client stubs for remote service calls
4. **Serialization**: Ensure proper serialization/deserialization of messages
5. **Error Handling**: Implement proper exception handling for network failures and service errors
6. **Transport Layer**: Configure network transport (HTTP/2 for gRPC, TCP for custom implementations)

## Key Components Typically Found in RPC Projects

- `proto/` or `idl/`: Protocol buffer or IDL definitions
- `src/main/java/`: Main source code
  - `service/`: Service implementations
  - `client/`: Client stubs and utilities
  - `server/`: Server setup and configuration
  - `serialization/`: Custom serializers if needed
  - `transport/`: Network transport layer
- `src/test/java/`: Unit and integration tests
- `pom.xml` or `build.gradle`: Build configuration

## Development Notes

- Use appropriate logging framework (SLF4J with Logback or Log4j2)
- Configure proper timeout settings for RPC calls
- Implement connection pooling for clients
- Add metrics and monitoring capabilities
- Ensure thread safety in service implementations