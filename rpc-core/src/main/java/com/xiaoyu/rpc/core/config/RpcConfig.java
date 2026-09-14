package com.xiaoyu.rpc.core.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;

/**
 * RPC配置类 - 从YAML文件读取配置
 */
public class RpcConfig {
    private static final Logger log = LoggerFactory.getLogger(RpcConfig.class);
    private static RpcConfig instance;

    // 序列化类型: JAVA, KRYO, PROTOBUF
    private String serializerType;
    // 服务端口
    private Integer serverPort;
    // 服务端地址
    private String serverHost;
    // 协议名称
    private String protocol;
    // 注册中心地址
    private String registryAddress;
    // 注册中心类型
    private String registryType = "nacos";
    // 代理类型
    private String proxyType = "jdk";
    // 负载均衡器
    private String loadBalancer = "roundrobin";
    // 传输层类型
    private String transport = "netty";
    // 最大报文长度
    private Integer maxMessageSize = 8 * 1024 * 1024;
    // Netty worker 线程数 (0 = CPU cores * 2)
    private Integer workerThreads = 0;
    // Netty boss 线程数
    private Integer bossThreads = 1;
    // 最大连接数
    private Integer maxConnections = 100;
    // 单次 RPC 请求超时时间
    private Integer requestTimeoutMillis = 5000;
    // 服务端业务线程数 (0 = CPU cores)
    private Integer businessThreads = 0;
    // 服务端业务线程池队列容量
    private Integer businessQueueCapacity = 1000;

    private RpcConfig() {
        loadConfig();
    }

    /**
     * 获取单例实例
     */
    public static synchronized RpcConfig getInstance() {
        if (instance == null) {
            instance = new RpcConfig();
        }
        return instance;
    }

    /**
     * 从YAML配置文件加载配置。
     * 配置优先级：System Properties > Nacos > 本地 rpc-config.yaml > 默认值。
     */
    private void loadConfig() {
        Yaml yaml = new Yaml();
        try (InputStream inputStream = RpcConfig.class.getClassLoader()
                .getResourceAsStream("rpc-config.yaml")) {

            if (inputStream != null) {
                Map<String, Object> config = yaml.load(inputStream);

                if (config != null && config.containsKey("rpc")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> rpcConfig = (Map<String, Object>) config.get("rpc");
                    updateConfigFields(rpcConfig);
                    log.info("本地 RPC 配置加载成功");
                } else {
                    log.warn("配置文件格式错误，使用默认配置");
                    setDefaultConfig();
                }
            } else {
                log.warn("未找到 rpc-config.yaml 文件，使用默认配置");
                setDefaultConfig();
            }

        } catch (Exception e) {
            log.error("加载配置文件失败，使用默认配置", e);
            setDefaultConfig();
        }

        // 先应用一次系统属性，使 rpc.registry / rpc.registry-address 能决定是否以及从哪里连接 Nacos。
        applySystemPropertyOverrides();

        if ("nacos".equalsIgnoreCase(this.registryType)) {
            loadNacosConfig();
        }

        // Nacos 配置加载后再次应用，保证 System Properties / Spring Boot 配置拥有最高优先级。
        applySystemPropertyOverrides();
        logCurrentConfig("配置加载完成");
    }

    /**
     * 从Nacos配置中心加载配置，并注册监听以实现热切换
     */
    private void loadNacosConfig() {
        try {
            String serverAddr = this.registryAddress != null && !this.registryAddress.isEmpty()
                    ? this.registryAddress
                    : "127.0.0.1:8848";
            String dataId = "rpc-config.yaml";
            String group = "DEFAULT_GROUP";

            Properties properties = new Properties();
            properties.put("serverAddr", serverAddr);

            ConfigService configService = NacosFactory.createConfigService(properties);

            String configInfo = configService.getConfig(dataId, group, 5000);
            if (configInfo != null && !configInfo.isEmpty()) {
                log.info("从Nacos加载配置文件成功");
                parseYamlConfigString(configInfo);
            } else {
                log.info("Nacos中不存在配置 dataId={}, 将使用本地配置", dataId);
            }

            configService.addListener(dataId, group, new Listener() {
                @Override
                public void receiveConfigInfo(String configInfo) {
                    log.info("检测到Nacos配置更新");
                    if (configInfo != null && !configInfo.isEmpty()) {
                        parseYamlConfigString(configInfo);
                        // 动态配置也不能覆盖显式的 JVM / Spring Boot 配置。
                        applySystemPropertyOverrides();
                        logCurrentConfig("Nacos 配置更新完成");
                    }
                }

                @Override
                public Executor getExecutor() {
                    return null;
                }
            });

            log.info("已注册Nacos配置监听器 dataId={}, group={}", dataId, group);
        } catch (Exception e) {
            log.error("加载Nacos配置失败，继续使用本地配置", e);
        }
    }

    /**
     * 解析 YAML 格式的字符串并更新配置属性
     */
    private void parseYamlConfigString(String yamlString) {
        Yaml yaml = new Yaml();
        try {
            Map<String, Object> config = yaml.load(yamlString);
            if (config != null && config.containsKey("rpc")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> rpcConfig = (Map<String, Object>) config.get("rpc");
                updateConfigFields(rpcConfig);
            }
        } catch (Exception e) {
            log.error("解析Nacos配置字符串失败", e);
        }
    }

    /**
     * 根据 Map 更新自身字段
     */
    private void updateConfigFields(Map<String, Object> rpcConfig) {
        if (rpcConfig.containsKey("serializer"))
            this.serializerType = (String) rpcConfig.get("serializer");
        if (rpcConfig.containsKey("server-port"))
            this.serverPort = (Integer) rpcConfig.get("server-port");
        if (rpcConfig.containsKey("server-host"))
            this.serverHost = (String) rpcConfig.get("server-host");
        if (rpcConfig.containsKey("protocol"))
            this.protocol = (String) rpcConfig.get("protocol");
        if (rpcConfig.containsKey("registry-address"))
            this.registryAddress = (String) rpcConfig.get("registry-address");
        if (rpcConfig.containsKey("registry"))
            this.registryType = (String) rpcConfig.get("registry");
        if (rpcConfig.containsKey("proxy"))
            this.proxyType = (String) rpcConfig.get("proxy");
        if (rpcConfig.containsKey("load-balancer"))
            this.loadBalancer = (String) rpcConfig.get("load-balancer");
        if (rpcConfig.containsKey("transport"))
            this.transport = (String) rpcConfig.get("transport");
        if (rpcConfig.containsKey("max-message-size"))
            this.maxMessageSize = (Integer) rpcConfig.get("max-message-size");
        if (rpcConfig.containsKey("worker-threads"))
            this.workerThreads = (Integer) rpcConfig.get("worker-threads");
        if (rpcConfig.containsKey("boss-threads"))
            this.bossThreads = (Integer) rpcConfig.get("boss-threads");
        if (rpcConfig.containsKey("max-connections"))
            this.maxConnections = (Integer) rpcConfig.get("max-connections");
        if (rpcConfig.containsKey("request-timeout-ms"))
            this.requestTimeoutMillis = (Integer) rpcConfig.get("request-timeout-ms");
        if (rpcConfig.containsKey("business-threads"))
            this.businessThreads = (Integer) rpcConfig.get("business-threads");
        if (rpcConfig.containsKey("business-queue-capacity"))
            this.businessQueueCapacity = (Integer) rpcConfig.get("business-queue-capacity");
    }

    /**
     * Spring Boot Starter 通过 System Properties 将配置同步到核心模块。
     */
    private void applySystemPropertyOverrides() {
        this.serverPort = getIntegerOverride("rpc.server-port", this.serverPort);
        this.serverHost = getStringOverride("rpc.server-host", this.serverHost);
        this.registryType = getStringOverride("rpc.registry", this.registryType);
        this.registryAddress = getStringOverride("rpc.registry-address", this.registryAddress);
        this.serializerType = getStringOverride("rpc.serializer", this.serializerType);
        this.transport = getStringOverride("rpc.transport", this.transport);
        this.protocol = getStringOverride("rpc.protocol", this.protocol);
        this.proxyType = getStringOverride("rpc.proxy", this.proxyType);
        this.loadBalancer = getStringOverride("rpc.load-balancer", this.loadBalancer);
        this.maxMessageSize = getIntegerOverride("rpc.max-message-size", this.maxMessageSize);
        this.workerThreads = getIntegerOverride("rpc.worker-threads", this.workerThreads);
        this.bossThreads = getIntegerOverride("rpc.boss-threads", this.bossThreads);
        this.maxConnections = getIntegerOverride("rpc.max-connections", this.maxConnections);
        this.requestTimeoutMillis = getIntegerOverride("rpc.request-timeout-ms", this.requestTimeoutMillis);
        this.businessThreads = getIntegerOverride("rpc.business-threads", this.businessThreads);
        this.businessQueueCapacity = getIntegerOverride("rpc.business-queue-capacity", this.businessQueueCapacity);
    }

    private String getStringOverride(String key, String currentValue) {
        String value = System.getProperty(key);
        return value == null || value.trim().isEmpty() ? currentValue : value.trim();
    }

    private Integer getIntegerOverride(String key, Integer currentValue) {
        String value = System.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            return currentValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            log.warn("忽略非法整数系统属性 {}={}", key, value);
            return currentValue;
        }
    }

    /**
     * 设置默认配置
     */
    private void setDefaultConfig() {
        this.serializerType = "PROTOBUF";
        this.serverPort = 8080;
        this.serverHost = "127.0.0.1";
        this.protocol = "netty";
        this.registryAddress = "127.0.0.1:8848";
        this.registryType = "nacos";
        this.proxyType = "jdk";
        this.loadBalancer = "roundrobin";
        this.transport = "netty";
        this.maxMessageSize = 8 * 1024 * 1024;
        this.workerThreads = 0;
        this.bossThreads = 1;
        this.maxConnections = 100;
        this.requestTimeoutMillis = 5000;
        this.businessThreads = 0;
        this.businessQueueCapacity = 1000;
    }

    private void logCurrentConfig(String prefix) {
        log.info("{}: serializer={}, server={}:{}, protocol={}, registry={}@{}, proxy={}, loadBalancer={}, transport={}, "
                        + "maxMessageSize={}, requestTimeoutMs={}, businessThreads={}, businessQueueCapacity={}",
                prefix, serializerType, serverHost, serverPort, protocol, registryType, registryAddress, proxyType,
                loadBalancer, transport, maxMessageSize, requestTimeoutMillis, businessThreads, businessQueueCapacity);
    }

    /**
     * 获取序列化器的字节码
     */
    public byte getSerializerCode() {
        return com.xiaoyu.rpc.common.serialization.SerializerCode.getSerializerByName(serializerType.toLowerCase())
                .getCode();
    }

    public String getSerializerType() {
        return serializerType;
    }

    public Integer getServerPort() {
        return serverPort;
    }

    public String getServerHost() {
        return serverHost;
    }

    public String getProtocol() {
        return protocol;
    }

    public String getRegistryAddress() {
        return registryAddress;
    }

    public String getRegistryType() {
        return registryType;
    }

    public String getProxyType() {
        return proxyType;
    }

    public String getLoadBalancer() {
        return loadBalancer;
    }

    public String getTransport() {
        return transport;
    }

    public Integer getMaxMessageSize() {
        return maxMessageSize;
    }

    public Integer getWorkerThreads() {
        return workerThreads;
    }

    public Integer getBossThreads() {
        return bossThreads;
    }

    public Integer getMaxConnections() {
        return maxConnections;
    }

    public Integer getRequestTimeoutMillis() {
        return requestTimeoutMillis;
    }

    public Integer getBusinessThreads() {
        return businessThreads;
    }

    public Integer getBusinessQueueCapacity() {
        return businessQueueCapacity;
    }

    @Override
    public String toString() {
        return "RpcConfig{" +
                "serializerType='" + serializerType + '\'' +
                ", serverPort=" + serverPort +
                ", serverHost='" + serverHost + '\'' +
                ", protocol='" + protocol + '\'' +
                ", registryAddress='" + registryAddress + '\'' +
                ", registryType='" + registryType + '\'' +
                ", proxyType='" + proxyType + '\'' +
                ", loadBalancer='" + loadBalancer + '\'' +
                ", transport='" + transport + '\'' +
                ", maxMessageSize=" + maxMessageSize +
                ", requestTimeoutMillis=" + requestTimeoutMillis +
                ", businessThreads=" + businessThreads +
                ", businessQueueCapacity=" + businessQueueCapacity +
                '}';
    }
}
