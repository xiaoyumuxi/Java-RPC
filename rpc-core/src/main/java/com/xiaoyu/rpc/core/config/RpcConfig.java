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
     * 从YAML配置文件加载配置
     */
    private void loadConfig() {
        Yaml yaml = new Yaml();
        try (InputStream inputStream = RpcConfig.class.getClassLoader()
                .getResourceAsStream("rpc-config.yaml")) {

            if (inputStream != null) {
                Map<String, Object> config = yaml.load(inputStream);

                // 读取 rpc 配置节点
                if (config != null && config.containsKey("rpc")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> rpcConfig = (Map<String, Object>) config.get("rpc");

                    this.serializerType = (String) rpcConfig.getOrDefault("serializer", "PROTOBUF");
                    this.serverPort = (Integer) rpcConfig.getOrDefault("server-port", 8080);
                    this.serverHost = (String) rpcConfig.getOrDefault("server-host", "127.0.0.1");
                    this.protocol = (String) rpcConfig.getOrDefault("protocol", "netty");
                    this.registryAddress = (String) rpcConfig.getOrDefault("registry-address", "127.0.0.1:8848");
                    this.registryType = (String) rpcConfig.getOrDefault("registry", "nacos");
                    this.proxyType = (String) rpcConfig.getOrDefault("proxy", "jdk");
                    this.loadBalancer = (String) rpcConfig.getOrDefault("load-balancer", "roundrobin");
                    this.transport = (String) rpcConfig.getOrDefault("transport", "netty");
                    this.maxMessageSize = (Integer) rpcConfig.getOrDefault("max-message-size", 8 * 1024 * 1024);
                    this.workerThreads = (Integer) rpcConfig.getOrDefault("worker-threads", 0);
                    this.bossThreads = (Integer) rpcConfig.getOrDefault("boss-threads", 1);
                    this.maxConnections = (Integer) rpcConfig.getOrDefault("max-connections", 100);

                    log.info("配置加载成功: 序列化方式={}, 服务器={}:{},使用的协议={}, 注册中心={}, 代理方式={}, 负载均衡={}, 传输层={}, 最大报文={}",
                            serializerType, serverHost, serverPort, protocol, registryAddress, proxyType, loadBalancer,
                            transport, maxMessageSize);
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

        String portStr = System.getProperty("rpc.server-port");
        if (portStr != null) {
            this.serverPort = Integer.parseInt(portStr);
            log.info("检测到 System Property 覆盖端口: {}", this.serverPort);
        }

        String registryTypeStr = System.getProperty("rpc.registry");
        if (registryTypeStr != null) {
            this.registryType = registryTypeStr;
            log.info("检测到 System Property 覆盖注册中心类型: {}", this.registryType);
        }

        String serializerStr = System.getProperty("rpc.serializer");
        if (serializerStr != null) {
            this.serializerType = serializerStr;
            log.info("检测到 System Property 覆盖序列化方式: {}", this.serializerType);
        }

        String transportStr = System.getProperty("rpc.transport");
        if (transportStr != null) {
            this.transport = transportStr;
            log.info("检测到 System Property 覆盖传输层: {}", this.transport);
        }

        String protocolStr = System.getProperty("rpc.protocol");
        if (protocolStr != null) {
            this.protocol = protocolStr;
            log.info("检测到 System Property 覆盖协议: {}", this.protocol);
        }

        // 加载Nacos配置并注册监听
        if ("nacos".equalsIgnoreCase(this.registryType)) {
            loadNacosConfig();
        }
    }

    /**
     * 从Nacos配置中心加载配置，并注册监听以实现热切换
     */
    private void loadNacosConfig() {
        try {
            // Nacos 配置参数
            String serverAddr = this.registryAddress != null && !this.registryAddress.isEmpty() ? this.registryAddress
                    : "127.0.0.1:8848";
            String dataId = "rpc-config.yaml";
            String group = "DEFAULT_GROUP";

            Properties properties = new Properties();
            properties.put("serverAddr", serverAddr);

            ConfigService configService = NacosFactory.createConfigService(properties);

            // 首次获取配置
            String configInfo = configService.getConfig(dataId, group, 5000);
            if (configInfo != null && !configInfo.isEmpty()) {
                log.info("从Nacos加载配置文件成功！\n{}", configInfo);
                parseYamlConfigString(configInfo);
            } else {
                log.info("Nacos中不存在配置 dataId={}, 将使用本地配置", dataId);
            }

            // 添加监听器，实现热切换
            configService.addListener(dataId, group, new Listener() {
                @Override
                public void receiveConfigInfo(String configInfo) {
                    log.info("检测到Nacos配置更新！\n{}", configInfo);
                    if (configInfo != null && !configInfo.isEmpty()) {
                        parseYamlConfigString(configInfo);
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

        log.info("配置更新完毕: 序列化方式={}, 服务器={}:{},使用的协议={}, 注册中心={}, 代理方式={}, 负载均衡={}, 传输层={}, 最大报文={}",
                serializerType, serverHost, serverPort, protocol, registryAddress, proxyType, loadBalancer,
                transport, maxMessageSize);
    }

    /**
     * 设置默认配置
     */
    private void setDefaultConfig() {
        this.serializerType = "PROTOBUF";
        this.serverPort = 8080;
        this.serverHost = "127.0.0.1";
        this.protocol = "netty";
        this.transport = "netty";
        this.maxMessageSize = 8 * 1024 * 1024;
    }

    /**
     * 获取序列化器的字节码
     */
    public byte getSerializerCode() {
        return com.xiaoyu.rpc.common.serialization.SerializerCode.getSerializerByName(serializerType.toLowerCase())
                .getCode();
    }

    // Getters
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
                '}';
    }
}
