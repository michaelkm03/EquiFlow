package com.equiflow.order.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.math.BigDecimal;
import java.util.Map;

@FeignClient(name = "market-data-service", url = "${feign.market-data-service.url:http://market-data-service:8083}")
public interface MarketDataClient {

    @GetMapping("/market/prices/{ticker}")
    Map<String, Object> getPrice(@PathVariable("ticker") String ticker);

    @GetMapping("/market/prices")
    Map<String, Object> getAllPrices();
}
