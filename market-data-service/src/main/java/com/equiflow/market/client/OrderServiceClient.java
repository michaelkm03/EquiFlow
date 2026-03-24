package com.equiflow.market.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;

@FeignClient(name = "order-service", url = "${feign.order-service.url:http://order-service:8082}")
public interface OrderServiceClient {

    @PostMapping("/orders/internal/stop-loss/evaluate")
    void evaluateStopLoss(@RequestParam("ticker") String ticker,
                          @RequestParam("currentPrice") BigDecimal currentPrice);
}
