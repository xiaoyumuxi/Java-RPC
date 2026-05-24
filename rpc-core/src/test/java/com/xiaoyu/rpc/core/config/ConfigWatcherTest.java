package com.xiaoyu.rpc.core.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ConfigWatcherTest {

    @Test
    void testConfigFileWatch(@TempDir Path tempDir) throws Exception {
        Path configFile = tempDir.resolve("test-config.yaml");
        Files.writeString(configFile, "rpc:\n  server-port: 8080\n");

        ConfigWatcher watcher = new ConfigWatcher();
        AtomicInteger updateCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(1);

        watcher.watch(configFile, config -> {
            updateCount.incrementAndGet();
            latch.countDown();
        });

        // 等待监听器启动
        Thread.sleep(500);

        // 修改配置文件
        Files.writeString(configFile, "rpc:\n  server-port: 9090\n");

        // 等待回调触发
        boolean triggered = latch.await(5, TimeUnit.SECONDS);
        assertTrue(triggered, "Config update callback should be triggered");
        assertTrue(updateCount.get() > 0, "Update count should be greater than 0");

        watcher.stop();
    }

    @Test
    void testStopWatcher(@TempDir Path tempDir) throws Exception {
        Path configFile = tempDir.resolve("test-config.yaml");
        Files.writeString(configFile, "rpc:\n  server-port: 8080\n");

        ConfigWatcher watcher = new ConfigWatcher();
        AtomicInteger updateCount = new AtomicInteger(0);

        watcher.watch(configFile, config -> updateCount.incrementAndGet());
        Thread.sleep(500);

        watcher.stop();

        // 停止后修改文件不应触发回调
        int countBefore = updateCount.get();
        Files.writeString(configFile, "rpc:\n  server-port: 9090\n");
        Thread.sleep(1000);

        assertEquals(countBefore, updateCount.get(), "No updates should occur after stopping");
    }
}
