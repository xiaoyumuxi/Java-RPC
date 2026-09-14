import sys

import grpc
from google.protobuf import wrappers_pb2

import rpc_meta_pb2
import rpc_meta_pb2_grpc


def run():
    channel = grpc.insecure_channel('localhost:8080')
    stub = rpc_meta_pb2_grpc.GrpcServiceStub(channel)

    param = wrappers_pb2.StringValue(value='World')
    rpc_request = rpc_meta_pb2.RpcRequest(
        interface_name='com.xiaoyu.rpc.api.HelloService',
        method_name='sayHello',
        param_types=['java.lang.String'],
        parameters=[param.SerializeToString()],
        request_id='python-ci-request',
    )

    try:
        response = stub.handle(rpc_request, timeout=5)
        if response.message != 'Success':
            raise RuntimeError(f'RPC failed: {response.message}')

        result = wrappers_pb2.StringValue()
        result.ParseFromString(response.data)
        if 'World' not in result.value:
            raise RuntimeError(f'Unexpected RPC result: {result.value!r}')

        print('RpcResponse received:')
        print(f'Data: {result.value}')
        print(f'Message: {response.message}')
        print(f'RequestID: {response.request_id}')
    finally:
        channel.close()


if __name__ == '__main__':
    try:
        run()
    except grpc.RpcError as exc:
        print(f'gRPC Error: {exc.code()} - {exc.details()}', file=sys.stderr)
        sys.exit(1)
    except Exception as exc:
        print(f'Client Error: {exc}', file=sys.stderr)
        sys.exit(1)
