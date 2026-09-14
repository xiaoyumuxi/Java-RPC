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

public class RpcClient {

    private final TransportClient transportClient;
    private final ServiceDiscovery serviceDiscovery;

    public RpcClient() {
        RpcConfig config = RpcConfig.getInstance();
        // 初始化服务发现
        this.serviceDiscovery = ExtensionLoader.getExtensionLoader(ServiceDiscovery.class)
                .getExtension(config.getRegistryType());

        // 初始化传输层客户端
        Transport transport = ExtensionLoader.getExtensionLoader(Transport.class).getExtension(config.getTransport());
        this.transportClient = transport.createClient();
    }

    RpcClient(TransportClient transportClient, ServiceDiscovery serviceDiscovery) {
        this.transportClient = Objects.requireNonNull(transportClient, "transportClient");
        this.serviceDiscovery = Objects.requireNonNull(serviceDiscovery, "serviceDiscovery");
    }

    public CompletableFuture<Object> sendRequest(RpcRequest request, Class<?> returnType) {
        try {
            // 先做一次服务发现（同步查找，通常会命中本地缓存）
            InetSocketAddress address = serviceDiscovery.lookupService(request.getInterfaceName());

            if (address == null) {
                CompletableFuture<Object> future = new CompletableFuture<>();
                future.completeExceptionally(new RuntimeException("未发现服务: " + request.getInterfaceName()));
                return future;
            }

            // 交给传输层发送，返回异步 Future
            CompletableFuture<Object> transportFuture = transportClient.sendRequest(request, address);

            // 在回调里把响应体反序列化成目标返回类型
            return transportFuture.thenApply(result -> {
                if (!(result instanceof RpcResponse)) {
                    throw new RuntimeException("Unexpected response type: " + result.getClass());
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
            CompletableFuture<Object> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }
}
