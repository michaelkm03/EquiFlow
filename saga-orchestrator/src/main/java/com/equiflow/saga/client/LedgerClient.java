package com.equiflow.saga.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = "ledger-service",
             url = "${feign.ledger-service.url:http://ledger-service:8085}")
public interface LedgerClient {

    @GetMapping("/ledger/accounts/{userId}")
    Map<String, Object> getAccount(@PathVariable("userId") String userId);

    @PostMapping("/ledger/hold")
    Map<String, Object> hold(@RequestBody Map<String, Object> request);

    @PostMapping("/ledger/release")
    Map<String, Object> release(@RequestBody Map<String, Object> request);

    @PostMapping("/ledger/debit")
    Map<String, Object> debit(@RequestBody Map<String, Object> request);
}
