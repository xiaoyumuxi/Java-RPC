package com.xiaoyu.rpc.core.interceptor;

import com.xiaoyu.rpc.common.vo.RpcRequest;
import com.xiaoyu.rpc.common.vo.RpcResponse;

public interface RpcInterceptor {

    default int order() {
        return 0;
    }

    default RpcRequest before(RpcInvocationContext context, RpcRequest request) {
        return request;
    }

    default void after(RpcInvocationContext context, RpcResponse response) {
    }

    default void onError(RpcInvocationContext context, Throwable error) {
    }
}
