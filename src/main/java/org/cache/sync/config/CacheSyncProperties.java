package org.cache.sync.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.UUID;

@ConfigurationProperties(prefix = "cache.sync")
public class CacheSyncProperties {

    private boolean enabled = false;
    private String streamKey = "cache:sync:stream";
    private String consumerGroupPrefix = "";
    private long messageTimeoutMs = 300000;
    private int maxRetry = 3;
    private int batchSize = 10;
    private long blockMs = 5000;
    private long maxlen = 10000;
    private int threadPoolSize = 1;
    private long gracefulShutdownTimeoutMs = 30000;
    private boolean enableMetrics = true;
    private transient String instanceId = null;

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
        try {
            // 添加应用名（如果有）
            String appName = System.getProperty("spring.application.name");
            if (StringUtils.hasLength(appName)) {
                sb.append(appName).append("-");
            }
            // 添加 IP 地址
            sb.append(InetAddress.getLocalHost().getHostAddress()).append("-");
            // 添加进程 ID（兼容 Java 8+）
            long pid;
            try {
                // 尝试使用 Java 9+ 的 ProcessHandle
                pid = ProcessHandle.current().pid();
            } catch (NoClassDefFoundError e) {
                // Java 8 兼容方案
                pid = Long.parseLong(System.getProperty("PID", String.valueOf(Thread.currentThread().getId())));
            }
            sb.append(pid).append("-");
            // 添加随机值
            sb.append(UUID.randomUUID().toString(), 0, 8);
        } catch (UnknownHostException e) {
            // 如果无法获取 IP 地址，使用随机值
            sb.append("unknown-").append(UUID.randomUUID().toString());
        }
        instanceId = sb.toString();
        return instanceId;
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

    public long getMaxlen() {
        return maxlen;
    }

    public void setMaxlen(long maxlen) {
        this.maxlen = maxlen;
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
}
