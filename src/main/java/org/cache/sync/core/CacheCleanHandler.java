package org.cache.sync.core;

import java.util.Map;

public interface CacheCleanHandler {

    default String supportType(){
        return "default";
    }

    default String supportSubType(){
        return "default";
    }

    void cacheSync(String type, String subType, String cacheKey, Map<String, String> metadata);

}
