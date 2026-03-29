package com.equiflow.saga.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;
import java.util.UUID;

@FeignClient(name = "order-service",
             url = "${feign.order-service.url:http://order-service:8082}")
public interface OrderClient {

    @PostMapping("/orders/{orderId}/match")
    Map<String, Object> triggerMatch(@PathVariable("orderId") UUID orderId);

    @PostMapping("/orders/{orderId}/system-cancel")
    Map<String, Object> systemCancel(@PathVariable("orderId") UUID orderId,
                                    @RequestBody Map<String, Object> body);
}
