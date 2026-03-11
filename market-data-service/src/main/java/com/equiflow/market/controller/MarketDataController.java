package com.equiflow.market.controller;

import com.equiflow.market.model.TickerPrice;
import com.equiflow.market.service.MarketDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/market")
@RequiredArgsConstructor
@Tag(name = "Market Data", description = "Real-time simulated market price data")
public class MarketDataController {

    private final MarketDataService marketDataService;

    @GetMapping("/prices")
    @Operation(summary = "Get all ticker prices")
    public ResponseEntity<List<TickerPrice>> getAllPrices() {
        return ResponseEntity.ok(marketDataService.getAllPrices());
    }

    @GetMapping("/prices/{ticker}")
    @Operation(summary = "Get price for a specific ticker")
    public ResponseEntity<TickerPrice> getPrice(@PathVariable String ticker) {
        return ResponseEntity.ok(marketDataService.getPrice(ticker));
    }

    @PostMapping("/prices/{ticker}/tick")
    @Operation(summary = "Simulate a price tick for a ticker")
    public ResponseEntity<TickerPrice> simulateTick(@PathVariable String ticker) {
        return ResponseEntity.ok(marketDataService.simulateTick(ticker));
    }

    @GetMapping("/status")
    @Operation(summary = "Get market data service status")
    public ResponseEntity<Object> getStatus() {
        return ResponseEntity.ok(marketDataService.getScenarioStatus());
    }
}
