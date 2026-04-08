package org.cache.sync.core;

import java.util.HashMap;
import java.util.Map;

public interface CacheSyncPublisher {

    default void publishCacheClean(String type, String subType, String cacheKey, int delayedMillis){
        this.publishCacheClean(type, subType, cacheKey, delayedMillis,  new HashMap<>(), true);
    }

    default void publishCacheClean(String type, String subType, String cacheKey){
        this.publishCacheClean(type, subType, cacheKey,0,  new HashMap<>(), true);
    }

    default void publishCacheClean(String type, String subType,String cacheKey,boolean afterTransaction){
        this.publishCacheClean(type, subType, cacheKey, 0,  new HashMap<>(), afterTransaction);
    }

    default void publishCacheClean(String type, String subType,String cacheKey,int delayedMillis, boolean afterTransaction){
        this.publishCacheClean(type, subType, cacheKey, delayedMillis,  new HashMap<>(), afterTransaction);
    }

    default void publishCacheClean(String type, String subType,String cacheKey, Map<String, String> metadata){
        this.publishCacheClean(type, subType, cacheKey, 0, metadata, true);
    }

    default void publishCacheClean(String type, String subType,String cacheKey, int delayedMillis,  Map<String, String> metadata){
        this.publishCacheClean(type, subType, cacheKey, delayedMillis, metadata, true);
    }

    default void publishCacheClean(String type, String subType,String cacheKey, Map<String, String> metadata, boolean afterTransaction){
        this.publishCacheClean(type, subType, cacheKey, 0,  metadata, afterTransaction);
    }

    void publishCacheClean(String type, String subType,String cacheKey, int delayedMillis, Map<String, String> metadata, boolean afterTransaction);

}
