package com.example.grpc.server;

import com.example.grpc.proto.*;
import io.grpc.stub.StreamObserver;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * gRPC Service Implementation
 * This class demonstrates all four types of gRPC communication patterns:
 * 1. Unary RPC (simple request-response)
 * 2. Server Streaming RPC (one request, multiple responses)
 * 3. Client Streaming RPC (multiple requests, one response)
 * 4. Bidirectional Streaming RPC (multiple requests and responses)
 */
public class UserServiceImpl extends UserServiceGrpc.UserServiceImplBase {
    
    // In-memory storage for demo purposes
    private final Map<Integer, UserResponse> users = new ConcurrentHashMap<>();
    private final AtomicInteger userIdCounter = new AtomicInteger(1);
    
    public UserServiceImpl() {
        // Initialize with some sample data
        initializeSampleData();
    }
    
    private void initializeSampleData() {
        for (int i = 1; i <= 10; i++) {
            UserResponse user = UserResponse.newBuilder()
                    .setUserId(i)
                    .setName("User" + i)
                    .setEmail("user" + i + "@example.com")
                    .setAge(20 + i)
                    .setCreatedAt(System.currentTimeMillis())
                    .build();
            users.put(i, user);
            userIdCounter.set(i + 1);
        }
    }
    
    /**
     * Type 1: Unary RPC - Simple request-response pattern
     * Client sends one request, server sends one response
     */
    @Override
    public void getUser(GetUserRequest request, StreamObserver<UserResponse> responseObserver) {
        System.out.println("[Unary RPC] Getting user with ID: " + request.getUserId());
        
        UserResponse user = users.get(request.getUserId());
        
        if (user != null) {
            // Send the response
            responseObserver.onNext(user);
            responseObserver.onCompleted();
            System.out.println("[Unary RPC] User found and sent: " + user.getName());
        } else {
            // User not found, send error
            responseObserver.onError(new RuntimeException("User not found with ID: " + request.getUserId()));
            System.out.println("[Unary RPC] User not found");
        }
    }
    
    /**
     * Type 2: Server Streaming RPC - One request, stream of responses
     * Client sends one request, server sends multiple responses
     */
    @Override
    public void listUsers(ListUsersRequest request, StreamObserver<UserResponse> responseObserver) {
        System.out.println("[Server Streaming RPC] Listing users, page size: " + request.getPageSize());
        
        int pageSize = request.getPageSize() > 0 ? request.getPageSize() : 5;
        int count = 0;
        
        // Stream users one by one
        for (UserResponse user : users.values()) {
            if (count >= pageSize) {
                break;
            }
            
            // Simulate some processing delay
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            // Send each user as a separate response
            responseObserver.onNext(user);
            System.out.println("[Server Streaming RPC] Sent user: " + user.getName());
            count++;
        }
        
        // Complete the stream
        responseObserver.onCompleted();
        System.out.println("[Server Streaming RPC] Completed streaming " + count + " users");
    }
    
    /**
     * Type 3: Client Streaming RPC - Stream of requests, one response
     * Client sends multiple requests, server sends one response at the end
     */
    @Override
    public StreamObserver<CreateUserRequest> createUsers(StreamObserver<CreateUsersResponse> responseObserver) {
        System.out.println("[Client Streaming RPC] Starting to receive user creation requests");
        
        return new StreamObserver<CreateUserRequest>() {
            private final List<Integer> createdUserIds = new ArrayList<>();
            
            @Override
            public void onNext(CreateUserRequest request) {
                // Receive each user creation request
                int userId = userIdCounter.getAndIncrement();
                UserResponse user = UserResponse.newBuilder()
                        .setUserId(userId)
                        .setName(request.getName())
                        .setEmail(request.getEmail())
                        .setAge(request.getAge())
                        .setCreatedAt(System.currentTimeMillis())
                        .build();
                
                users.put(userId, user);
                createdUserIds.add(userId);
                System.out.println("[Client Streaming RPC] Created user: " + user.getName() + " with ID: " + userId);
            }
            
            @Override
            public void onError(Throwable t) {
                System.err.println("[Client Streaming RPC] Error occurred: " + t.getMessage());
            }
            
            @Override
            public void onCompleted() {
                // All requests received, send the final response
                CreateUsersResponse response = CreateUsersResponse.newBuilder()
                        .setCreatedCount(createdUserIds.size())
                        .addAllUserIds(createdUserIds)
                        .build();
                
                responseObserver.onNext(response);
                responseObserver.onCompleted();
                System.out.println("[Client Streaming RPC] Completed creating " + createdUserIds.size() + " users");
            }
        };
    }
    
    /**
     * Type 4: Bidirectional Streaming RPC - Stream of requests and responses
     * Client and server can send multiple messages to each other independently
     */
    @Override
    public StreamObserver<ChatMessage> chat(StreamObserver<ChatMessage> responseObserver) {
        System.out.println("[Bidirectional Streaming RPC] Chat session started");
        
        return new StreamObserver<ChatMessage>() {
            @Override
            public void onNext(ChatMessage message) {
                // Receive a message from client
                System.out.println("[Bidirectional Streaming RPC] Received: " + message.getUserName() + ": " + message.getMessage());
                
                // Echo the message back with server prefix
                ChatMessage response = ChatMessage.newBuilder()
                        .setUserName("Server")
                        .setMessage("Echo: " + message.getMessage())
                        .setTimestamp(System.currentTimeMillis())
                        .build();
                
                // Send response back to client
                responseObserver.onNext(response);
                System.out.println("[Bidirectional Streaming RPC] Sent echo response");
                
                // Simulate some additional server messages
                if (message.getMessage().toLowerCase().contains("hello")) {
                    ChatMessage greeting = ChatMessage.newBuilder()
                            .setUserName("Server")
                            .setMessage("Welcome to gRPC chat!")
                            .setTimestamp(System.currentTimeMillis())
                            .build();
                    responseObserver.onNext(greeting);
                }
            }
            
            @Override
            public void onError(Throwable t) {
                System.err.println("[Bidirectional Streaming RPC] Error: " + t.getMessage());
            }
            
            @Override
            public void onCompleted() {
                // Client has finished sending messages
                ChatMessage farewell = ChatMessage.newBuilder()
                        .setUserName("Server")
                        .setMessage("Chat session ended. Goodbye!")
                        .setTimestamp(System.currentTimeMillis())
                        .build();
                
                responseObserver.onNext(farewell);
                responseObserver.onCompleted();
                System.out.println("[Bidirectional Streaming RPC] Chat session completed");
            }
        };
    }
}
