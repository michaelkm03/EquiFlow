package com.equiflow.order.matching;

import com.equiflow.order.model.Order;
import com.equiflow.order.model.enums.OrderSide;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.*;

/**
 * In-memory order book for a single ticker.
 * Bids sorted descending (highest price first).
 * Asks sorted ascending (lowest price first).
 */
@Slf4j
public class OrderBook {

    private final String ticker;

    // bids: price -> list of orders (highest price first for matching)
    private final TreeMap<BigDecimal, List<Order>> bids =
            new TreeMap<>(Comparator.reverseOrder());

    // asks: price -> list of orders (lowest price first for matching)
    private final TreeMap<BigDecimal, List<Order>> asks =
            new TreeMap<>();

    public OrderBook(String ticker) {
        this.ticker = ticker;
    }

    public void addBid(Order order) {
        bids.computeIfAbsent(order.getLimitPrice(), k -> new ArrayList<>()).add(order);
    }

    public void addAsk(Order order) {
        asks.computeIfAbsent(order.getLimitPrice(), k -> new ArrayList<>()).add(order);
    }

    /**
     * Attempt to match a limit order against the opposite side.
     * Returns list of matched orders.
     */
    public List<MatchResult> matchLimitOrder(Order incoming) {
        List<MatchResult> results = new ArrayList<>();
        BigDecimal remainingQty = incoming.getQuantity();

        if (incoming.getSide() == OrderSide.BUY) {
            // Match against asks (ascending price)
            for (Map.Entry<BigDecimal, List<Order>> entry : asks.entrySet()) {
                if (entry.getKey().compareTo(incoming.getLimitPrice()) > 0) break;
                if (remainingQty.compareTo(BigDecimal.ZERO) <= 0) break;
                remainingQty = processMatches(entry, remainingQty, results, incoming);
            }
        } else {
            // Match against bids (descending price)
            for (Map.Entry<BigDecimal, List<Order>> entry : bids.entrySet()) {
                if (entry.getKey().compareTo(incoming.getLimitPrice()) < 0) break;
                if (remainingQty.compareTo(BigDecimal.ZERO) <= 0) break;
                remainingQty = processMatches(entry, remainingQty, results, incoming);
            }
        }

        return results;
    }

    /**
     * Match a market order at best available price.
     */
    public List<MatchResult> matchMarketOrder(Order incoming) {
        List<MatchResult> results = new ArrayList<>();
        BigDecimal remainingQty = incoming.getQuantity();

        TreeMap<BigDecimal, List<Order>> oppositeSide =
                incoming.getSide() == OrderSide.BUY ? asks : bids;

        for (Map.Entry<BigDecimal, List<Order>> entry : oppositeSide.entrySet()) {
            if (remainingQty.compareTo(BigDecimal.ZERO) <= 0) break;
            remainingQty = processMatches(entry, remainingQty, results, incoming);
        }

        return results;
    }

    private BigDecimal processMatches(Map.Entry<BigDecimal, List<Order>> entry,
                                      BigDecimal remainingQty,
                                      List<MatchResult> results,
                                      Order incoming) {
        List<Order> ordersAtPrice = entry.getValue();
        Iterator<Order> it = ordersAtPrice.iterator();

        while (it.hasNext() && remainingQty.compareTo(BigDecimal.ZERO) > 0) {
            Order resting = it.next();
            BigDecimal restingRemaining = resting.getQuantity()
                    .subtract(resting.getFilledQty() == null ? BigDecimal.ZERO : resting.getFilledQty());

            BigDecimal matchedQty = remainingQty.min(restingRemaining);
            results.add(new MatchResult(incoming, resting, matchedQty, entry.getKey()));
            remainingQty = remainingQty.subtract(matchedQty);

            if (matchedQty.compareTo(restingRemaining) >= 0) {
                it.remove();
            }
        }

        return remainingQty;
    }

    public void removeOrder(Order order) {
        TreeMap<BigDecimal, List<Order>> book =
                order.getSide() == OrderSide.BUY ? bids : asks;
        if (order.getLimitPrice() != null) {
            List<Order> level = book.get(order.getLimitPrice());
            if (level != null) {
                level.removeIf(o -> o.getId().equals(order.getId()));
            }
        }
    }

    public Map<String, Object> getSnapshot() {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("ticker", ticker);

        List<Map<String, Object>> bidList = new ArrayList<>();
        bids.forEach((price, orders) -> {
            BigDecimal totalQty = orders.stream()
                    .map(Order::getQuantity)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            bidList.add(Map.of("price", price, "quantity", totalQty, "orders", orders.size()));
        });

        List<Map<String, Object>> askList = new ArrayList<>();
        asks.forEach((price, orders) -> {
            BigDecimal totalQty = orders.stream()
                    .map(Order::getQuantity)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            askList.add(Map.of("price", price, "quantity", totalQty, "orders", orders.size()));
        });

        snapshot.put("bids", bidList);
        snapshot.put("asks", askList);
        return snapshot;
    }

    public record MatchResult(Order incoming, Order resting, BigDecimal matchedQty, BigDecimal price) {}
}
