package com.xiaoyu.rpc.core.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * 配置文件监听器，支持热加载
 */
public class ConfigWatcher {
    private static final Logger log = LoggerFactory.getLogger(ConfigWatcher.class);
    private final WatchService watchService;
    private final ExecutorService executor;
    private volatile boolean running = false;

    public ConfigWatcher() throws IOException {
        this.watchService = FileSystems.getDefault().newWatchService();
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "config-watcher");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * 监听配置文件变化
     *
     * @param configPath 配置文件路径
     * @param onUpdate   配置更新回调
     */
    public void watch(Path configPath, Consumer<RpcConfig> onUpdate) {
        if (running) {
            log.warn("ConfigWatcher is already running");
            return;
        }

        try {
            Path directory = configPath.getParent();
            if (directory == null) {
                directory = Paths.get(".");
            }

            directory.register(watchService,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_CREATE);

            running = true;
            executor.submit(() -> watchLoop(configPath, onUpdate));
            log.info("Started watching config file: {}", configPath);
        } catch (IOException e) {
            log.error("Failed to start config watcher", e);
        }
    }

    private void watchLoop(Path configPath, Consumer<RpcConfig> onUpdate) {
        while (running) {
            try {
                WatchKey key = watchService.take();

                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();

                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        continue;
                    }

                    @SuppressWarnings("unchecked")
                    WatchEvent<Path> ev = (WatchEvent<Path>) event;
                    Path filename = ev.context();

                    if (filename.toString().equals(configPath.getFileName().toString())) {
                        log.info("Config file changed, reloading: {}", filename);
                        try {
                            // 等待文件写入完成
                            Thread.sleep(100);
                            RpcConfig newConfig = RpcConfig.getInstance();
                            onUpdate.accept(newConfig);
                            log.info("Config reloaded successfully");
                        } catch (Exception e) {
                            log.error("Failed to reload config", e);
                        }
                    }
                }

                boolean valid = key.reset();
                if (!valid) {
                    break;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * 停止监听
     */
    public void stop() {
        running = false;
        executor.shutdown();
        try {
            watchService.close();
        } catch (IOException e) {
            log.error("Failed to close watch service", e);
        }
    }
}
