package com.djomkam.catalogsense.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/** Deletes published outbox rows older than the retention period, so the table stays small. */
@Component
@Slf4j
@RequiredArgsConstructor
public class OutboxCleanup {

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxProperties properties;
    private final Clock clock;

    @Scheduled(cron = "${catalogsense.outbox.cleanup-cron:0 0 3 * * *}")
    @Transactional
    public int purge() {
        int deleted = outboxEventRepository.deletePublishedBefore(clock.instant().minus(properties.retention()));
        if (deleted > 0) {
            log.info("Outbox cleanup deleted {} published event(s)", deleted);
        }
        return deleted;
    }
}
