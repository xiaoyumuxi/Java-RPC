package com.xiaoyu.rpc.core.server;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务注册仓库
 * 用于存放本地已注册的服务实例
 */
public class ServiceRepository {

    // 进程内服务表：key 是接口全限定名，value 是具体实现对象
    private static final Map<String, Object> SERVICE_MAP = new ConcurrentHashMap<>();

    public static void registerService(String interfaceName, Object serviceBean) {
        // 同一接口重复注册时以后一次为准，便于启动期覆盖旧实现
        SERVICE_MAP.put(interfaceName, serviceBean);
    }

    public static Object getService(String interfaceName) {
        return SERVICE_MAP.get(interfaceName);
    }
}
