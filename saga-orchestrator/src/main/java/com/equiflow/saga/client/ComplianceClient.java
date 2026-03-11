package com.equiflow.saga.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = "compliance-service",
             url = "${feign.compliance-service.url:http://compliance-service:8084}")
public interface ComplianceClient {

    @PostMapping("/compliance/check")
    Map<String, Object> check(@RequestBody Map<String, Object> request);
}
