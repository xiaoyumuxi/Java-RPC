package com.xiaoyu.rpc.core.client;

import com.xiaoyu.rpc.common.extension.ExtensionLoader;
import com.xiaoyu.rpc.common.serialization.Serializer;
import com.xiaoyu.rpc.common.serialization.SerializerCode;
import com.xiaoyu.rpc.common.vo.RpcRequest;
import com.xiaoyu.rpc.common.vo.RpcResponse;
import com.xiaoyu.rpc.core.config.RpcConfig;
import com.xiaoyu.rpc.core.registry.ServiceDiscovery;
import com.xiaoyu.rpc.core.transport.Transport;
import com.xiaoyu.rpc.core.transport.TransportClient;
import com.xiaoyu.rpc.core.util.TypeUtils;

import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public class RpcClient implements AutoCloseable {

    private final TransportClient transportClient;
    private final ServiceDiscovery serviceDiscovery;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public RpcClient() {
        RpcConfig config = RpcConfig.getInstance();
        this.serviceDiscovery = ExtensionLoader.getExtensionLoader(ServiceDiscovery.class)
                .getExtension(config.getRegistryType());

        Transport transport = ExtensionLoader.getExtensionLoader(Transport.class).getExtension(config.getTransport());
        this.transportClient = transport.createClient();
    }

    RpcClient(TransportClient transportClient, ServiceDiscovery serviceDiscovery) {
        this.transportClient = Objects.requireNonNull(transportClient, "transportClient");
        this.serviceDiscovery = Objects.requireNonNull(serviceDiscovery, "serviceDiscovery");
    }

    public CompletableFuture<Object> sendRequest(RpcRequest request, Class<?> returnType) {
        if (closed.get()) {
            return CompletableFuture.failedFuture(new IllegalStateException("RpcClient 已关闭"));
        }

        try {
            InetSocketAddress address = serviceDiscovery.lookupService(request.getInterfaceName());

            if (address == null) {
                return CompletableFuture.failedFuture(
                        new RuntimeException("未发现服务: " + request.getInterfaceName()));
            }

            CompletableFuture<Object> transportFuture = transportClient.sendRequest(request, address);

            return transportFuture.thenApply(result -> {
                if (!(result instanceof RpcResponse)) {
                    String actualType = result == null ? "null" : result.getClass().getName();
                    throw new RuntimeException("Unexpected response type: " + actualType);
                }

                RpcResponse response = (RpcResponse) result;
                if (returnType == void.class || returnType == Void.class) {
                    return null;
                }

                byte[] data = response.getData().toByteArray();
                Serializer serializer = SerializerCode
                        .getSerializerByCode(RpcConfig.getInstance().getSerializerCode());
                Class<?> deserializeType = TypeUtils.wrapPrimitive(returnType);
                return serializer.deserialize(data, deserializeType);
            });

        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            transportClient.close();
        }
    }
}
