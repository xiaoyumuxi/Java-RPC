package com.xiaoyu.rpc.common.extension;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Enumeration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 自定义 SPI 加载器
 * 类似于 Dubbo 的 ExtensionLoader，支持 key-value 形式的 SPI 配置
 * 配置文件路径: META-INF/rpc/接口全限定名
 * 内容格式: key=implementation_class
 */
@Slf4j
public class ExtensionLoader<T> {

    // 扩展点加载路径
    private static final String EXTENSION_DIR = "META-INF/rpc/";
    // 缓存 ExtensionLoader 实例，每个接口对应一个 Loader
    private static final Map<Class<?>, ExtensionLoader<?>> EXTENSION_LOADERS = new ConcurrentHashMap<>();

    // 扩展点接口
    private final Class<T> type;
    // 缓存扩展点实例: key名称 -> 实例
    private final Map<String, Holder<Object>> cachedInstances = new ConcurrentHashMap<>();
    // 缓存扩展点类: key名称 -> 类
    private final Holder<Map<String, Class<?>>> cachedClasses = new Holder<>();

    private ExtensionLoader(Class<T> type) {
        this.type = type;
    }

    /**
     * 获取接口对应的 ExtensionLoader
     */
    @SuppressWarnings("unchecked")
    public static <T> ExtensionLoader<T> getExtensionLoader(Class<T> type) {
        if (type == null) {
            throw new IllegalArgumentException("Extension type should not be null.");
        }
        if (!type.isInterface()) {
            throw new IllegalArgumentException("Extension type must be an interface.");
        }
        if (type.getAnnotation(SPI.class) == null) {
            throw new IllegalArgumentException("Extension type must be annotated by @SPI");
        }

        ExtensionLoader<T> loader = (ExtensionLoader<T>) EXTENSION_LOADERS.get(type);
        if (loader == null) {
            EXTENSION_LOADERS.putIfAbsent(type, new ExtensionLoader<>(type));
            loader = (ExtensionLoader<T>) EXTENSION_LOADERS.get(type);
        }
        return loader;
    }

    /**
     * 根据名称获取扩展点实例
     */
    @SuppressWarnings("unchecked")
    public T getExtension(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Extension name should not be null or empty.");
        }

        // 先拿到对应名称的缓存槽位，不存在就补一个
        Holder<Object> holder = cachedInstances.get(name);
        if (holder == null) {
            cachedInstances.putIfAbsent(name, new Holder<>());
            holder = cachedInstances.get(name);
        }

        // 实例按需创建，使用双重检查避免重复初始化
        Object instance = holder.get();
        if (instance == null) {
            synchronized (holder) {
                instance = holder.get();
                if (instance == null) {
                    instance = createExtension(name);
                    holder.set(instance);
                }
            }
        }
        return (T) instance;
    }

    /**
     * 获取所有支持的扩展点名称
     */
    public java.util.Set<String> getSupportedExtensions() {
        return getExtensionClasses().keySet();
    }

    /**
     * 创建扩展点实例
     */
    @SuppressWarnings("unchecked")
    private T createExtension(String name) {
        // 加载所有扩展类
        Class<?> clazz = getExtensionClasses().get(name);
        if (clazz == null) {
            throw new RuntimeException("No such extension of name " + name);
        }
        try {
            return (T) clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Create extension instance failed: " + name, e);
        }
    }

    /**
     * 加载并缓存所有扩展类
     */
    private Map<String, Class<?>> getExtensionClasses() {
        Map<String, Class<?>> classes = cachedClasses.get();
        if (classes == null) {
            synchronized (cachedClasses) {
                classes = cachedClasses.get();
                if (classes == null) {
                    classes = loadExtensionClasses();
                    cachedClasses.set(classes);
                }
            }
        }
        return classes;
    }

    /**
     * 从文件中读取配置并加载类
     */
    private Map<String, Class<?>> loadExtensionClasses() {
        Map<String, Class<?>> extensionClasses = new ConcurrentHashMap<>();
        String fileName = EXTENSION_DIR + type.getName();
        try {
            Enumeration<URL> urls;
            ClassLoader classLoader = ExtensionLoader.class.getClassLoader();
            urls = classLoader.getResources(fileName);
            if (urls != null) {
                while (urls.hasMoreElements()) {
                    URL resourceUrl = urls.nextElement();
                    loadResource(extensionClasses, classLoader, resourceUrl);
                }
            }
        } catch (IOException e) {
            log.error("Exception when load extension class file: " + fileName, e);
        }
        return extensionClasses;
    }

    private void loadResource(Map<String, Class<?>> extensionClasses, ClassLoader classLoader, URL resourceUrl) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resourceUrl.openStream(), "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // 处理注释
                final int ci = line.indexOf('#');
                if (ci >= 0)
                    line = line.substring(0, ci);
                line = line.trim();
                if (line.length() > 0) {
                    try {
                        String name = null;
                        String className = null;

                        // 解析 key=value
                        int i = line.indexOf('=');
                        if (i > 0) {
                            name = line.substring(0, i).trim();
                            className = line.substring(i + 1).trim();
                        } else {
                            // 如果没有=，则按照类名加载，可以暂不支持或抛错
                            className = line;
                        }

                        if (name != null && name.length() > 0 && className != null && className.length() > 0) {
                            Class<?> clazz = classLoader.loadClass(className);
                            extensionClasses.put(name, clazz);
                        }
                    } catch (Throwable t) {
                        log.error("Failed to load extension class (interface: " + type + ", class line: " + line
                                + ") in " + resourceUrl, t);
                    }
                }
            }
        } catch (IOException e) {
            log.error("Exception when load extension class file: " + resourceUrl, e);
        }
    }

    /**
     * 简单的 Holder 类，用于缓存
     */
    public static class Holder<T> {
        private volatile T value;

        public T get() {
            return value;
        }

        public void set(T value) {
            this.value = value;
        }
    }
}
