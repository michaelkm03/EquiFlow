package com.equiflow.chaos.aop;

import com.equiflow.chaos.service.ChaosService;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
public class KafkaLatencyAspect {

    @Around("execution(* org.springframework.kafka.core.KafkaTemplate.send(..))")
    public Object injectKafkaLatency(ProceedingJoinPoint joinPoint) throws Throwable {
        if (ChaosService.chaosActive &&
                (ChaosService.chaosMode.contains("NETWORK_LATENCY") ||
                 ChaosService.chaosMode.contains("BOTH"))) {
            int latency = ChaosService.chaosLatencyMs;
            if (latency > 0) {
                log.debug("Chaos: injecting {}ms latency on Kafka send", latency);
                Thread.sleep(latency);
            }
        }
        return joinPoint.proceed();
    }
}
