package com.xiaoyu.rpc.core.server;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务注册仓库
 * 用于存放本地已注册的服务实例
 */
public class ServiceRepository {

    // 缓存服务实例: interfaceName -> serviceBean
    private static final Map<String, Object> SERVICE_MAP = new ConcurrentHashMap<>();

    public static void registerService(String interfaceName, Object serviceBean) {
        SERVICE_MAP.put(interfaceName, serviceBean);
    }

    public static Object getService(String interfaceName) {
        return SERVICE_MAP.get(interfaceName);
    }
}
