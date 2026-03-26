package com.equiflow.ledger;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Integration tests for LedgerService concurrency behaviour.
 * Uses a real Postgres container so SELECT FOR UPDATE row-locking is enforced —
 * H2 does not provide the same semantics.
 */
public class LedgerServiceConcurrencyTest {

    static {
        // Docker Desktop 29.x requires API 1.44; docker-java defaults to 1.32.
        System.setProperty("api.version", "1.44");
    }

    @SuppressWarnings("resource")
    @Test(description = "Postgres container starts and accepts a JDBC query")
    public void postgres_containerStartsAndAcceptsQuery() throws Exception {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("ledger_test")
                .withUsername("test")
                .withPassword("test")) {

            postgres.start();
            assertTrue(postgres.isRunning(), "Container should be running after start()");

            try (Connection conn = openConnection(postgres);
                 ResultSet pingResult = conn.createStatement().executeQuery("SELECT 1")) {
                pingResult.next();
                assertEquals(pingResult.getInt(1), 1, "SELECT 1 should return 1");
            }
        }
    }

    @SuppressWarnings("resource")
    @Test(description = "Concurrent hold requests on the same account — only one should succeed")
    public void ledgerHold_concurrentRequests_onlyOneSucceeds() throws Exception {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("ledger_test")
                .withUsername("test")
                .withPassword("test")) {

            postgres.start();

            try (Connection schemaSetup = openConnection(postgres)) {
                schemaSetup.createStatement().execute("""
                        CREATE TABLE accounts (
                            user_id      UUID PRIMARY KEY,
                            cash_balance NUMERIC(18,4) NOT NULL DEFAULT 0,
                            cash_on_hold NUMERIC(18,4) NOT NULL DEFAULT 0
                        )
                        """);
                // $100 balance, $0 on hold — both threads attempt a $75 hold,
                // only one can succeed ($100 - $75 = $25 < $75 for the second).
                schemaSetup.createStatement().execute("""
                        INSERT INTO accounts (user_id, cash_balance, cash_on_hold)
                        VALUES ('a1000000-0000-0000-0000-000000000001', 100.0000, 0.0000)
                        """);
            }

            // Gate won't open until exactly 2 threads have checked in,
            // ensuring both are alive simultaneously before touching the DB.
            CountDownLatch startGate = new CountDownLatch(2);

            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount    = new AtomicInteger(0);

            String userId         = "a1000000-0000-0000-0000-000000000001";
            BigDecimal holdAmount = new BigDecimal("75.00");

            // Both threads run the same lambda and share the same startGate, successCount, and failCount.
            // Mirrors LedgerService.hold(): SELECT FOR UPDATE → availability check → UPDATE or rollback.
            Runnable holdAttempt = () -> {
                try (Connection conn = openConnection(postgres)) {
                    // Step 1: open an explicit transaction — required for FOR UPDATE to hold the lock
                    conn.setAutoCommit(false);

                    // Step 2: signal ready and block until both threads are at this point, then race
                    startGate.countDown();
                    startGate.await();

                    // Step 3: acquire a row-level lock — the second thread blocks here
                    // until the first commits or rolls back
                    ResultSet lockedAccountRow = conn.createStatement().executeQuery(
                            "SELECT cash_balance, cash_on_hold FROM accounts " +
                            "WHERE user_id = '" + userId + "' FOR UPDATE");
                    lockedAccountRow.next();

                    // Step 4: availability check — mirrors LedgerService.hold()
                    BigDecimal available = lockedAccountRow.getBigDecimal("cash_balance")
                            .subtract(lockedAccountRow.getBigDecimal("cash_on_hold"));

                    if (available.compareTo(holdAmount) >= 0) {
                        // Step 5a: sufficient funds — place the hold and release the lock
                        conn.createStatement().execute(
                                "UPDATE accounts SET cash_on_hold = cash_on_hold + " + holdAmount +
                                " WHERE user_id = '" + userId + "'");
                        conn.commit();
                        successCount.incrementAndGet();
                    } else {
                        // Step 5b: insufficient funds (second thread sees post-commit balance) — abort
                        conn.rollback();
                        failCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failCount.incrementAndGet();
                }
            };

            Thread holdThread1 = new Thread(holdAttempt);
            Thread holdThread2 = new Thread(holdAttempt);
            holdThread1.start();
            holdThread2.start();
            holdThread1.join();
            holdThread2.join();

            assertEquals(successCount.get(), 1, "Exactly one hold should succeed");
            assertEquals(failCount.get(),    1, "Exactly one hold should fail (insufficient funds after lock)");

            // Verify final DB state: one hold of $75, leaving $25 available
            try (Connection auditConnection = openConnection(postgres);
                 ResultSet finalAccountState = auditConnection.createStatement().executeQuery(
                         "SELECT cash_on_hold FROM accounts WHERE user_id = '" + userId + "'")) {
                finalAccountState.next();
                assertEquals(finalAccountState.getBigDecimal("cash_on_hold").compareTo(holdAmount), 0,
                        "cash_on_hold should be $75 — only one hold committed");
            }
        }
    }

    private Connection openConnection(PostgreSQLContainer<?> postgres) throws Exception {
        return DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }
}
