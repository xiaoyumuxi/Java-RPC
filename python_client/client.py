import grpc
import rpc_meta_pb2
import rpc_meta_pb2_grpc
from google.protobuf import wrappers_pb2

def run():
    # Connect to the Java gRPC server
    channel = grpc.insecure_channel('localhost:8080')
    stub = rpc_meta_pb2_grpc.GrpcServiceStub(channel)

    # Wrap the parameter in a Protobuf StringValue
    param = wrappers_pb2.StringValue(value="World")
    param_bytes = param.SerializeToString()

    rpc_request = rpc_meta_pb2.RpcRequest(
        interface_name="com.xiaoyu.rpc.api.HelloService",
        method_name="sayHello",
        param_types=["java.lang.String"],
        parameters=[param_bytes] 
    )

    try:
        response = stub.handle(rpc_request)
        print("RpcResponse received:")
        # The return value is also a StringValue serialized object
        if response.message == "Success":
            result_val = wrappers_pb2.StringValue()
            result_val.ParseFromString(response.data)
            print(f"Data: {result_val.value}")
        else:
            print(f"Data (Raw): {response.data}")
        print(f"Message: {response.message}")
    except grpc.RpcError as e:
        print(f"gRPC Error: {e.code()} - {e.details()}")

if __name__ == '__main__':
    run()
