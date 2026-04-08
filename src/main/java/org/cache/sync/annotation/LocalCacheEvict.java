package org.cache.sync.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface LocalCacheEvict {

    String type() default "default";

    String subType() default "default";

    String cacheKey();
    /**
     * 是否是事务提交之后执行
     */
    boolean afterTransaction() default true;
    
    /**
     * 延时删除时间（毫秒），默认0表示立即删除
     */
    long delay() default 0;

}
