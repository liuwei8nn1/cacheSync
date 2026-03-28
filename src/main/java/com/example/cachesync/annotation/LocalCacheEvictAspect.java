package com.example.cachesync.annotation;

import com.example.cachesync.core.CacheSyncPublisher;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;

@Aspect
public class LocalCacheEvictAspect {

    private final CacheSyncPublisher cacheSyncPublisher;
    private final ExpressionParser expressionParser = new SpelExpressionParser();

    @Autowired
    public LocalCacheEvictAspect(CacheSyncPublisher cacheSyncPublisher) {
        this.cacheSyncPublisher = cacheSyncPublisher;
    }

    @Pointcut("@annotation(com.example.cachesync.annotation.LocalCacheEvict)")
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

        cacheSyncPublisher.publishCacheClean(type, subType, cacheKey);
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
