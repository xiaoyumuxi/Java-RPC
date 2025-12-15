package com.example.grpc.client;

import com.example.grpc.proto.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * gRPC Client
 * 
 * This client demonstrates all four types of gRPC communication:
 * 1. Unary RPC (getUser)
 * 2. Server Streaming RPC (listUsers)
 * 3. Client Streaming RPC (createUsers)
 * 4. Bidirectional Streaming RPC (chat)
 * 
 * Study Points:
 * - Channel creation and management
 * - Blocking vs Async stubs
 * - StreamObserver for handling streaming responses
 * - Proper resource cleanup
 */
public class GrpcClient {
    
    private final ManagedChannel channel;
    private final UserServiceGrpc.UserServiceBlockingStub blockingStub;
    private final UserServiceGrpc.UserServiceStub asyncStub;
    
    /**
     * Construct client connecting to server at host:port
     */
    public GrpcClient(String host, int port) {
        // Create a channel - the connection to the server
        this.channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()  // Disable TLS for demo purposes
                .build();
        
        // Create stubs for making RPC calls
        // Blocking stub for synchronous calls
        this.blockingStub = UserServiceGrpc.newBlockingStub(channel);
        // Async stub for asynchronous calls
        this.asyncStub = UserServiceGrpc.newStub(channel);
    }
    
    /**
     * Shutdown the channel gracefully
     */
    public void shutdown() throws InterruptedException {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
    
    /**
     * Example 1: Unary RPC - Simple request-response
     * Synchronous call using blocking stub
     */
    public void getUserExample(int userId) {
        System.out.println("\n========================================");
        System.out.println("1. UNARY RPC Example - GetUser");
        System.out.println("========================================");
        
        GetUserRequest request = GetUserRequest.newBuilder()
                .setUserId(userId)
                .build();
        
        try {
            // Make the call - blocks until response is received
            UserResponse response = blockingStub.getUser(request);
            System.out.println("Received user: " + response.getName() + " (ID: " + response.getUserId() + ")");
            System.out.println("Email: " + response.getEmail() + ", Age: " + response.getAge());
        } catch (Exception e) {
            System.err.println("RPC failed: " + e.getMessage());
        }
    }
    
    /**
     * Example 2: Server Streaming RPC - One request, multiple responses
     * Server sends a stream of responses
     */
    public void listUsersExample(int pageSize) throws InterruptedException {
        System.out.println("\n========================================");
        System.out.println("2. SERVER STREAMING RPC Example - ListUsers");
        System.out.println("========================================");
        
        ListUsersRequest request = ListUsersRequest.newBuilder()
                .setPageSize(pageSize)
                .build();
        
        CountDownLatch latch = new CountDownLatch(1);
        
        // Async call to handle streaming response

        // Asynchronously call the listUsers RPC method with a StreamObserver to handle the streaming response
        // The asyncStub is used for non-blocking calls, allowing the server to send multiple UserResponse messages
        // StreamObserver<UserResponse> is a callback interface with three methods:
        // - onNext(): called for each user received from the server stream
        // - onError(): called if an error occurs during streaming
        // - onCompleted(): called when the server finishes sending all users
        asyncStub.listUsers(request, new StreamObserver<UserResponse>() {
            @Override
            public void onNext(UserResponse user) {
                // Called for each user in the stream
                System.out.println("Received user: " + user.getName() + " (ID: " + user.getUserId() + ")");
            }
            
            @Override
            public void onError(Throwable t) {
                System.err.println("ListUsers failed: " + t.getMessage());
                latch.countDown();
            }
            
            @Override
            public void onCompleted() {
                System.out.println("Server finished sending users");
                latch.countDown();
            }
        });
        
        // Wait for the stream to complete
        latch.await(10, TimeUnit.SECONDS);
    }
    
    /**
     * Example 3: Client Streaming RPC - Multiple requests, one response
     * Client sends a stream of requests, server responds once at the end
     */
    public void createUsersExample() throws InterruptedException {
        System.out.println("\n========================================");
        System.out.println("3. CLIENT STREAMING RPC Example - CreateUsers");
        System.out.println("========================================");
        
        CountDownLatch latch = new CountDownLatch(1);
        
        // StreamObserver for receiving the final response
        StreamObserver<CreateUsersResponse> responseObserver = new StreamObserver<CreateUsersResponse>() {
            @Override
            public void onNext(CreateUsersResponse response) {
                System.out.println("Created " + response.getCreatedCount() + " users");
                System.out.println("User IDs: " + response.getUserIdsList());
            }
            
            @Override
            public void onError(Throwable t) {
                System.err.println("CreateUsers failed: " + t.getMessage());
                latch.countDown();
            }
            
            @Override
            public void onCompleted() {
                System.out.println("Server confirmed all users created");
                latch.countDown();
            }
        };
        
        // Get the request observer to send requests
        StreamObserver<CreateUserRequest> requestObserver = asyncStub.createUsers(responseObserver);
        
        try {
            // Send multiple user creation requests
            String[] names = {"Alice", "Bob", "Charlie"};
            for (int i = 0; i < names.length; i++) {
                CreateUserRequest request = CreateUserRequest.newBuilder()
                        .setName(names[i])
                        .setEmail(names[i].toLowerCase() + "@example.com")
                        .setAge(25 + i)
                        .build();
                
                requestObserver.onNext(request);
                System.out.println("Sent create request for: " + names[i]);
                
                // Simulate some delay
                Thread.sleep(200);
            }
        } catch (Exception e) {
            requestObserver.onError(e);
            throw e;
        }
        
        // Mark the end of requests
        requestObserver.onCompleted();
        
        // Wait for response
        latch.await(10, TimeUnit.SECONDS);
    }
    
    /**
     * Example 4: Bidirectional Streaming RPC - Multiple requests and responses
     * Both client and server can send multiple messages independently
     */
    public void chatExample() throws InterruptedException {
        System.out.println("\n========================================");
        System.out.println("4. BIDIRECTIONAL STREAMING RPC Example - Chat");
        System.out.println("========================================");
        
        CountDownLatch latch = new CountDownLatch(1);
        
        // StreamObserver for receiving messages from server
        StreamObserver<ChatMessage> responseObserver = new StreamObserver<ChatMessage>() {
            @Override
            public void onNext(ChatMessage message) {
                System.out.println("[Received] " + message.getUserName() + ": " + message.getMessage());
            }
            
            @Override
            public void onError(Throwable t) {
                System.err.println("Chat failed: " + t.getMessage());
                latch.countDown();
            }
            
            @Override
            public void onCompleted() {
                System.out.println("Server ended the chat");
                latch.countDown();
            }
        };
        
        // Get the request observer to send messages
        StreamObserver<ChatMessage> requestObserver = asyncStub.chat(responseObserver);
        
        try {
            // Send multiple chat messages
            String[] messages = {
                "Hello, server!",
                "How are you?",
                "This is a bidirectional stream",
                "Goodbye!"
            };
            
            for (String msg : messages) {
                ChatMessage chatMessage = ChatMessage.newBuilder()
                        .setUserName("Client")
                        .setMessage(msg)
                        .setTimestamp(System.currentTimeMillis())
                        .build();
                
                requestObserver.onNext(chatMessage);
                System.out.println("[Sent] Client: " + msg);
                
                // Wait a bit to see responses
                Thread.sleep(1000);
            }
        } catch (Exception e) {
            requestObserver.onError(e);
            throw e;
        }
        
        // Mark the end of client messages
        requestObserver.onCompleted();
        
        // Wait for server to complete
        latch.await(10, TimeUnit.SECONDS);
    }
    
    /**
     * Main method to run all examples
     */
    public static void main(String[] args) {
        GrpcClient client = new GrpcClient("localhost", 50051);
        
        try {
            // Run all examples
            System.out.println("Starting gRPC Client Examples...");
            System.out.println("Make sure the server is running on localhost:50051");
            
            // Example 1: Unary RPC
            client.getUserExample(1);
            
            Thread.sleep(1000);
            
            // Example 2: Server Streaming RPC
            client.listUsersExample(5);
            
            Thread.sleep(1000);
            
            // Example 3: Client Streaming RPC
            client.createUsersExample();
            
            Thread.sleep(1000);
            
            // Example 4: Bidirectional Streaming RPC
            client.chatExample();
            
            System.out.println("\n========================================");
            System.out.println("All examples completed!");
            System.out.println("========================================");
            
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                client.shutdown();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
