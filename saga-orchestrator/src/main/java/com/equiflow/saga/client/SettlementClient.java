package com.equiflow.saga.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = "settlement-service",
             url = "${feign.settlement-service.url:http://settlement-service:8086}")
public interface SettlementClient {

    @PostMapping("/settlement/create")
    Map<String, Object> createSettlement(@RequestBody Map<String, Object> request);
}
