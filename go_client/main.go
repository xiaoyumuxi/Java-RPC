package main

import (
	"context"
	"fmt"
	"log"
	"time"

	"go_client/pb"

	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials/insecure"
	"google.golang.org/protobuf/proto"
	"google.golang.org/protobuf/types/known/wrapperspb"
)

func main() {
	// Connect to the Java gRPC server
	addr := "localhost:8080"
	conn, err := grpc.Dial(addr, grpc.WithTransportCredentials(insecure.NewCredentials()))
	if err != nil {
		log.Fatalf("did not connect: %v", err)
	}
	defer conn.Close()

	client := pb.NewGrpcServiceClient(conn)

	// Wrap the parameter in a Protobuf StringValue
	param := wrapperspb.String( "World")
	paramBytes, err := proto.Marshal(param)
	if err != nil {
		log.Fatalf("failed to marshal param: %v", err)
	}

	// Prepare the RPC request
	req := &pb.RpcRequest{
		InterfaceName: "com.xiaoyu.rpc.api.HelloService",
		MethodName:    "sayHello",
		ParamTypes:    []string{"java.lang.String"},
		Parameters:    [][]byte{paramBytes},
		RequestId:     fmt.Sprintf("go-req-%d", time.Now().UnixNano()),
	}

	// Set a timeout for the call
	ctx, cancel := context.WithTimeout(context.Background(), time.Second*5)
	defer cancel()

	// Call the handle method
	fmt.Printf("Sending RpcRequest: interface=%s, method=%s, param=%s\n", req.InterfaceName, req.MethodName, "World")
	resp, err := client.Handle(ctx, req)
	if err != nil {
		log.Fatalf("could not call handle: %v", err)
	}

	fmt.Println("RpcResponse received:")
	if resp.Message == "Success" {
		resultVal := &wrapperspb.StringValue{}
		if err := proto.Unmarshal(resp.Data, resultVal); err != nil {
			log.Printf("failed to unmarshal data: %v", err)
			fmt.Printf("Data (Raw): %v\n", resp.Data)
		} else {
			fmt.Printf("Data: %s\n", resultVal.Value)
		}
	} else {
		fmt.Printf("Data (Raw): %v\n", resp.Data)
	}
	fmt.Printf("Message: %s\n", resp.Message)
	fmt.Printf("RequestID: %s\n", resp.RequestId)
}
