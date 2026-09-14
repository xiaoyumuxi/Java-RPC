package com.xiaoyu.rpc.core.interceptor;

import com.xiaoyu.rpc.common.vo.RpcRequest;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class RpcInvocationContext {

    private final RpcSide side;
    private final String requestId;
    private final String interfaceName;
    private final String methodName;
    private final long startNanos;
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();

    public RpcInvocationContext(RpcSide side, RpcRequest request) {
        this.side = side;
        this.requestId = request.getRequestId();
        this.interfaceName = request.getInterfaceName();
        this.methodName = request.getMethodName();
        this.startNanos = System.nanoTime();
    }

    public RpcSide getSide() {
        return side;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getInterfaceName() {
        return interfaceName;
    }

    public String getMethodName() {
        return methodName;
    }

    public long getStartNanos() {
        return startNanos;
    }

    public void putAttribute(String key, Object value) {
        if (value == null) {
            attributes.remove(key);
        } else {
            attributes.put(key, value);
        }
    }

    public Object getAttribute(String key) {
        return attributes.get(key);
    }

    public Map<String, Object> getAttributes() {
        return Map.copyOf(attributes);
    }
}
