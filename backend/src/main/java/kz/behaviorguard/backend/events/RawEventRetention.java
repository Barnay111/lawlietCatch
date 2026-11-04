package kz.behaviorguard.backend.events;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;

/**
 * Срок хранения сырых событий (раздел 8 ТЗ). Агрегаты в профилях не удаляются:
 * после истечения срока остаются только статистики, сами события — нет.
 */
@Component
public class RawEventRetention {

    static final int BATCH = 5_000;

    private final BehavioralEventRepository events;
    private final TransactionTemplate tx;
    private final Duration retention;

    public RawEventRetention(BehavioralEventRepository events,
                             PlatformTransactionManager txManager,
                             @Value("${behaviorguard.retention.raw-events-days:90}") int days) {
        this.events = events;
        this.tx = new TransactionTemplate(txManager);
        this.retention = Duration.ofDays(days);
    }

    /** Ежедневно в 03:30. Удаление батчами, чтобы не держать длинную транзакцию. */
    @Scheduled(cron = "0 30 3 * * *")
    public void scheduledPurge() {
        purge(Instant.now());
    }

    /** Удаляет события старше срока хранения. Возвращает число удалённых строк. */
    public long purge(Instant now) {
        Instant cutoff = now.minus(retention);
        long total = 0;
        while (true) {
            Integer n = tx.execute(s -> events.deleteReceivedBefore(cutoff, BATCH));
            if (n == null || n == 0) break;
            total += n;
        }
        return total;
    }
}
