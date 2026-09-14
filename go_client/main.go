package main

import (
	"context"
	"fmt"
	"log"
	"strings"
	"time"

	"go_client/pb"

	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials/insecure"
	"google.golang.org/protobuf/proto"
	"google.golang.org/protobuf/types/known/wrapperspb"
)

func main() {
	addr := "localhost:8080"
	conn, err := grpc.Dial(addr, grpc.WithTransportCredentials(insecure.NewCredentials()))
	if err != nil {
		log.Fatalf("did not connect: %v", err)
	}
	defer conn.Close()

	client := pb.NewGrpcServiceClient(conn)

	param := wrapperspb.String("World")
	paramBytes, err := proto.Marshal(param)
	if err != nil {
		log.Fatalf("failed to marshal param: %v", err)
	}

	req := &pb.RpcRequest{
		InterfaceName: "com.xiaoyu.rpc.api.HelloService",
		MethodName:    "sayHello",
		ParamTypes:    []string{"java.lang.String"},
		Parameters:    [][]byte{paramBytes},
		RequestId:     fmt.Sprintf("go-req-%d", time.Now().UnixNano()),
	}

	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	fmt.Printf("Sending RpcRequest: interface=%s, method=%s, param=%s\n", req.InterfaceName, req.MethodName, "World")
	resp, err := client.Handle(ctx, req)
	if err != nil {
		log.Fatalf("could not call handle: %v", err)
	}
	if resp.Message != "Success" {
		log.Fatalf("RPC failed: %s", resp.Message)
	}

	resultVal := &wrapperspb.StringValue{}
	if err := proto.Unmarshal(resp.Data, resultVal); err != nil {
		log.Fatalf("failed to unmarshal data: %v", err)
	}
	if !strings.Contains(resultVal.Value, "World") {
		log.Fatalf("unexpected RPC result: %q", resultVal.Value)
	}

	fmt.Println("RpcResponse received:")
	fmt.Printf("Data: %s\n", resultVal.Value)
	fmt.Printf("Message: %s\n", resp.Message)
	fmt.Printf("RequestID: %s\n", resp.RequestId)
}
