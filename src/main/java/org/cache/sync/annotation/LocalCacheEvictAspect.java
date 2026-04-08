package org.cache.sync.annotation;

import org.cache.sync.config.CacheSyncProperties;
import org.cache.sync.core.CacheSyncPublisher;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.cache.sync.core.InternalMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

@Aspect
public class LocalCacheEvictAspect {

    private final CacheSyncPublisher cacheSyncPublisher;
    private final ExpressionParser expressionParser = new SpelExpressionParser();

    @Autowired
    public LocalCacheEvictAspect(CacheSyncPublisher cacheSyncPublisher) {
        this.cacheSyncPublisher = cacheSyncPublisher;
    }

    @Pointcut("@annotation(org.cache.sync.annotation.LocalCacheEvict)")
    public void localCacheEvictPointcut() {}

    @After("localCacheEvictPointcut()")
    public void afterMethodExecution(JoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        LocalCacheEvict annotation = method.getAnnotation(LocalCacheEvict.class);

        String cacheKeyExpression = annotation.cacheKey();
        String type = annotation.type();
        String subType = annotation.subType();
        String cacheKey = evaluateExpression(cacheKeyExpression, joinPoint);
        boolean afterTransaction = annotation.afterTransaction();
        long delay = annotation.delay();
        // 将 delay 参数添加到 metadata 中
        Map<String, String> metadata = new HashMap<>();
        metadata.put(InternalMessage.DELAY, String.valueOf(delay));
        cacheSyncPublisher.publishCacheClean(type, subType, cacheKey, metadata, afterTransaction);
    }

    private String evaluateExpression(String expression, JoinPoint joinPoint) {
        StandardEvaluationContext context = new StandardEvaluationContext();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] parameterNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();

        for (int i = 0; i < parameterNames.length; i++) {
            context.setVariable(parameterNames[i], args[i]);
        }

        return expressionParser.parseExpression(expression).getValue(context, String.class);
    }

}
