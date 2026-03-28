package com.example.cachesync.core;

import java.util.Map;

public interface CacheSyncPublisher {

    void publishCacheClean(String type, String subType,String cacheKey);

    void publishCacheClean(String type, String subType,String cacheKey, Map<String, String> metadata);

}
