package com.paybridge.Configs;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@Aspect
public class LoggingConfiguration {

    private final Logger log= LoggerFactory.getLogger(LoggingConfiguration.class);

    @Around("execution(* com.paybridge.Services.*Service.*(..))")
    public Object logServiceMethods(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().getName();
        String className = joinPoint.getTarget().getClass().getSimpleName();
        Object[] args = joinPoint.getArgs();

        // Avoid logging raw args because service methods may receive secrets.
        log.debug("Entering {}.{} (argCount={})", className, methodName, args.length);

        long start = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            long duration = System.currentTimeMillis() - start;

            log.info("{}.{} executed in {} ms", className, methodName, duration);
            return result;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            log.error("Error in {}.{} after {} ms (type={})",
                    className, methodName, duration, e.getClass().getSimpleName());
            log.debug("Stack trace for {}.{}", className, methodName, e);
            throw e;
        }
    }
}
