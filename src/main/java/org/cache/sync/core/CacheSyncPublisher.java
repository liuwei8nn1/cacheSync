package org.cache.sync.core;

import java.util.HashMap;
import java.util.Map;

public interface CacheSyncPublisher {

    default void publishCacheClean(String type, String subType,String cacheKey){
        this.publishCacheClean(type, subType, cacheKey, new HashMap<>(), true);
    }

    default void publishCacheClean(String type, String subType,String cacheKey,boolean afterTransaction){
        this.publishCacheClean(type, subType, cacheKey, new HashMap<>(), afterTransaction);
    }

    default void publishCacheClean(String type, String subType,String cacheKey, Map<String, String> metadata){
        this.publishCacheClean(type, subType, cacheKey, metadata, true);
    }

    void publishCacheClean(String type, String subType,String cacheKey, Map<String, String> metadata, boolean afterTransaction);

}
