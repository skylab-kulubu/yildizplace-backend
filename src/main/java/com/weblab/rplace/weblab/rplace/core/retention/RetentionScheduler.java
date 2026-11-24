package com.weblab.rplace.weblab.rplace.core.retention;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Looks once an hour whether the day's periodic destruction run is due (RetentionSweep)
 * and runs it. Its own thread, not @Scheduled: @EnableScheduling lives on the
 * maintenance job's switch (PLACE_MAINTENANCE_ENABLED), and the two must not turn each
 * other on. Nothing starts while RETENTION_SWEEP_MODE is off.
 */
@Component
public class RetentionScheduler {

    private static final Logger log = LoggerFactory.getLogger(RetentionScheduler.class);

    static final long FIRST_CHECK_MINUTES = 1;

    static final long CHECK_EVERY_MINUTES = 60;

    private final RetentionSweep sweep;

    private final RetentionSettings settings;

    private ScheduledExecutorService executor;

    public RetentionScheduler(RetentionSweep sweep, RetentionSettings settings) {
        this.sweep = sweep;
        this.settings = settings;
    }

    @EventListener(ApplicationReadyEvent.class)
    public synchronized void start() {
        log.info("retention_sweep mode={} event_ended_at={} max_rows={}", settings.mode().value(),
                settings.eventEndedAt() == null ? "unset" : settings.eventEndedAt(), settings.maxRows());
        if (settings.mode() == RetentionMode.OFF || executor != null) {
            return;
        }
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "retention-sweep");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(this::check, FIRST_CHECK_MINUTES, CHECK_EVERY_MINUTES, TimeUnit.MINUTES);
    }

    public synchronized boolean isRunning() {
        return executor != null && !executor.isShutdown();
    }

    void check() {
        try {
            sweep.runIfDue();
        } catch (RuntimeException e) {
            // Tried again in an hour.
            log.warn("retention_sweep check failed code={}", RetentionSweep.errorCode(e));
        }
    }

    @PreDestroy
    public synchronized void stop() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }
}
