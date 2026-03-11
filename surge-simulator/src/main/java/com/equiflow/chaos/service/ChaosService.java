package com.equiflow.chaos.service;

import com.equiflow.chaos.dto.ChaosRequest;
import com.equiflow.chaos.dto.ChaosStatus;
import com.equiflow.chaos.model.ChaosSession;
import com.equiflow.chaos.repository.ChaosSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChaosService {

    private final ChaosSessionRepository sessionRepository;

    // Shared volatile state for aspects to read
    public static volatile boolean chaosActive = false;
    public static volatile String chaosMode = "";
    public static volatile int chaosLatencyMs = 0;
    public static volatile int chaosFailureRatePercent = 0;
    private static volatile ChaosSession activeSession = null;

    @Transactional
    public ChaosStatus start(ChaosRequest request) {
        // Stop any existing session
        if (chaosActive) {
            stop();
        }

        ChaosSession session = ChaosSession.builder()
                .mode(request.getMode())
                .latencyMs(request.getLatencyMs())
                .failureRate((double) request.getFailureRatePercent() / 100.0)
                .status("ACTIVE")
                .triggeredBy(request.getTriggeredBy() != null ? request.getTriggeredBy() : "anonymous")
                .build();
        session = sessionRepository.save(session);

        chaosActive = true;
        chaosMode = request.getMode();
        chaosLatencyMs = request.getLatencyMs();
        chaosFailureRatePercent = request.getFailureRatePercent();
        activeSession = session;

        log.info("Chaos session started: mode={}, latency={}ms, failureRate={}%, triggeredBy={}",
                request.getMode(), request.getLatencyMs(), request.getFailureRatePercent(),
                request.getTriggeredBy());

        return buildStatus(session);
    }

    @Transactional
    public ChaosStatus stop() {
        if (activeSession != null) {
            ChaosSession session = sessionRepository.findById(activeSession.getId()).orElse(null);
            if (session != null) {
                session.setStatus("STOPPED");
                session.setStoppedAt(Instant.now());
                sessionRepository.save(session);
            }
        }

        chaosActive = false;
        chaosMode = "";
        chaosLatencyMs = 0;
        chaosFailureRatePercent = 0;
        ChaosSession stopped = activeSession;
        activeSession = null;

        log.info("Chaos session stopped");
        return buildStatus(stopped);
    }

    public ChaosStatus getStatus() {
        return buildStatus(activeSession);
    }

    private ChaosStatus buildStatus(ChaosSession session) {
        return ChaosStatus.builder()
                .sessionId(session != null ? session.getId() : null)
                .active(chaosActive)
                .mode(chaosMode)
                .latencyMs(chaosLatencyMs)
                .failureRatePercent(chaosFailureRatePercent)
                .startedAt(session != null ? session.getStartedAt() : null)
                .triggeredBy(session != null ? session.getTriggeredBy() : null)
                .build();
    }
}
