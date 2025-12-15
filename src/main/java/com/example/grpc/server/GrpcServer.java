package com.example.grpc.server;

import io.grpc.Server;
import io.grpc.ServerBuilder;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * gRPC Server
 * 
 * Key Components to Study:
 * 1. Server initialization with ServerBuilder
 * 2. Service registration
 * 3. Port binding
 * 4. Graceful shutdown
 * 
 * This is the entry point for the gRPC server
 */
public class GrpcServer {
    
    private Server server;
    private static final int PORT = 50051;
    
    /**
     * Start the gRPC server
     */
    public void start() throws IOException {
        // Build and start the server
        server = ServerBuilder.forPort(PORT)
                .addService(new UserServiceImpl())  // Register our service implementation
                .build()
                .start();
        
        System.out.println("========================================");
        System.out.println("gRPC Server started on port: " + PORT);
        System.out.println("========================================");
        System.out.println("Server is ready to accept connections...");
        System.out.println();
        
        // Add shutdown hook for graceful shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.err.println("*** Shutting down gRPC server (JVM shutdown)");
            try {
                GrpcServer.this.stop();
            } catch (InterruptedException e) {
                e.printStackTrace(System.err);
            }
            System.err.println("*** Server shut down");
        }));
    }
    
    /**
     * Stop the server gracefully
     */
    public void stop() throws InterruptedException {
        if (server != null) {
            server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
        }
    }
    
    /**
     * Block until the server is terminated
     */
    public void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }
    
    /**
     * Main method to run the server
     */
    public static void main(String[] args) throws IOException, InterruptedException {
        final GrpcServer server = new GrpcServer();
        server.start();
        server.blockUntilShutdown();
    }
}
