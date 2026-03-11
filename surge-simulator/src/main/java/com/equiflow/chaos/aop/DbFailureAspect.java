package com.equiflow.chaos.aop;

import com.equiflow.chaos.service.ChaosService;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;

import java.util.Random;

@Slf4j
@Aspect
@Component
public class DbFailureAspect {

    private final Random random = new Random();

    @Around("@annotation(org.springframework.transaction.annotation.Transactional)")
    public Object injectDbFailure(ProceedingJoinPoint joinPoint) throws Throwable {
        if (ChaosService.chaosActive &&
                (ChaosService.chaosMode.contains("DB_FAILURE") ||
                 ChaosService.chaosMode.contains("BOTH"))) {
            int failureRate = ChaosService.chaosFailureRatePercent;
            if (failureRate > 0 && random.nextInt(100) < failureRate) {
                log.warn("Chaos: injecting DB failure for method: {}",
                        joinPoint.getSignature().toShortString());
                throw new TransientDataAccessException(
                        "Chaos engineering: simulated transient DB failure") {};
            }
        }
        return joinPoint.proceed();
    }
}
