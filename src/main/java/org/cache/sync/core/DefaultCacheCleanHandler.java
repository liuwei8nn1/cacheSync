package org.cache.sync.core;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 默认的缓存清理处理器
 * 支持所有类型的缓存清理（type="*", subType="*"）
 * 作为兜底处理器，当没有其他更具体的处理器匹配时使用
 * 
 * @since 2026/3/28
 */
public class DefaultCacheCleanHandler implements CacheCleanHandler {

    private static final Logger logger = LoggerFactory.getLogger(DefaultCacheCleanHandler.class);

    @Override
    public String supportType() {
        return "*";
    }

    @Override
    public String supportSubType() {
        return "*";
    }

    @Override
    public void cacheSync(String type, String subType, String cacheKey, Map<String, String> metadata) {
        // 默认实现：仅记录日志，由业务方根据需要自定义具体处理器
        logger.debug("Default cache clean handler executed: type={}, subType={}, cacheKey={}, metadata={}", 
            type, subType, cacheKey, metadata);
    }

}
