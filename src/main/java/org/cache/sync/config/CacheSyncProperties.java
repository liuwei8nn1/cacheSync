package org.cache.sync.config;

import org.springframework.beans.BeansException;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.UUID;

@ConfigurationProperties(prefix = "cache.sync")
public class CacheSyncProperties implements ApplicationContextAware {

    private boolean enabled = false;
    private String streamKey = "cache:sync:stream";
    private String consumerGroupPrefix = "";
    private long messageTimeoutMs = 300000;
    private int maxRetry = 3;
    private int batchSize = 10;
    private long blockMs = 5000;
    private long maxLen = 10000;
    private int threadPoolSize = 1;
    private long gracefulShutdownTimeoutMs = 30000;
    private boolean enableMetrics = true;
    private boolean autoCleanOfflineConsumers = false;
    private long offlineConsumerTimeoutMinutes = 60 * 8;
    private transient String instanceId = null;
    private transient ApplicationContext applicationContext;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getStreamKey() {
        return streamKey;
    }

    public void setStreamKey(String streamKey) {
        this.streamKey = streamKey;
    }

    public String getConsumerGroup() {
        if(!StringUtils.hasLength(consumerGroupPrefix)){
           return getInstanceId();
        }else{
	        return consumerGroupPrefix + "-" + getInstanceId();
        }
    }

    public void setConsumerGroupPrefix(String consumerGroupPrefix) {
        this.consumerGroupPrefix = consumerGroupPrefix;
    }

    public String getInstanceId() {
        if(instanceId == null){
            instanceId = genInstanceId();
        }
        return instanceId;
    }

    public synchronized String genInstanceId() {
        if(instanceId != null){
            return instanceId;
        }
        // 生成默认实例 ID，确保唯一性
        StringBuilder sb = new StringBuilder();
        // 尝试多种方式获取应用名
        String appName = null;
        // 从 Spring Environment 获取
        if (applicationContext != null) {
            Environment env = applicationContext.getEnvironment();
            appName = env.getProperty("spring.application.name");
        }
        if (StringUtils.hasLength(appName)) {
            sb.append(appName).append("-");
        }
        try {
            // 添加 IP 地址
            sb.append(InetAddress.getLocalHost().getHostAddress());
            // 添加进程 ID（兼容 Java 8+）
            // long pid;
            // try {
            //     // 尝试使用 Java 9+ 的 ProcessHandle
            //     pid = ProcessHandle.current().pid();
            // } catch (NoClassDefFoundError e) {
            //     // Java 8 兼容方案 - 使用 ManagementFactory
            //     try {
            //         String runtimeName = java.lang.management.ManagementFactory.getRuntimeMXBean().getName();
            //         pid = Long.parseLong(runtimeName.split("@")[0]);
            //     } catch (Exception ex) {
            //         // 最后的备选方案
            //         pid = Thread.currentThread().getId();
            //     }
            // }
            // sb.append("-").append(pid);
        } catch (UnknownHostException e) {
            // 如果无法获取 IP 地址，使用随机值
            sb.append("unknown-").append(UUID.randomUUID().toString());
        }
        instanceId = sb.toString();
        return instanceId;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    public long getMessageTimeoutMs() {
        return messageTimeoutMs;
    }

    public void setMessageTimeoutMs(long messageTimeoutMs) {
        this.messageTimeoutMs = messageTimeoutMs;
    }

    public int getMaxRetry() {
        return maxRetry;
    }

    public void setMaxRetry(int maxRetry) {
        this.maxRetry = maxRetry;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public long getBlockMs() {
        return blockMs;
    }

    public void setBlockMs(long blockMs) {
        this.blockMs = blockMs;
    }

    public long getMaxLen() {
        return maxLen;
    }

    public void setMaxLen(long maxLen) {
        this.maxLen = maxLen;
    }

    public int getThreadPoolSize() {
        return threadPoolSize;
    }

    public void setThreadPoolSize(int threadPoolSize) {
        this.threadPoolSize = threadPoolSize;
    }

    public long getGracefulShutdownTimeoutMs() {
        return gracefulShutdownTimeoutMs;
    }

    public void setGracefulShutdownTimeoutMs(long gracefulShutdownTimeoutMs) {
        this.gracefulShutdownTimeoutMs = gracefulShutdownTimeoutMs;
    }

    public boolean isEnableMetrics() {
        return enableMetrics;
    }

    public void setEnableMetrics(boolean enableMetrics) {
        this.enableMetrics = enableMetrics;
    }

    public boolean isAutoCleanOfflineConsumers() {
        return autoCleanOfflineConsumers;
    }

    public void setAutoCleanOfflineConsumers(boolean autoCleanOfflineConsumers) {
        this.autoCleanOfflineConsumers = autoCleanOfflineConsumers;
    }

    public long getOfflineConsumerTimeoutMinutes() {
        return offlineConsumerTimeoutMinutes;
    }

    public void setOfflineConsumerTimeoutMinutes(long offlineConsumerTimeoutMinutes) {
        this.offlineConsumerTimeoutMinutes = offlineConsumerTimeoutMinutes;
    }
}
