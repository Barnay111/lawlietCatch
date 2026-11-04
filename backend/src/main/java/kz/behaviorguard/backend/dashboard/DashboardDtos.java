package kz.behaviorguard.backend.dashboard;

import java.util.List;
import java.util.Map;

/** DTO дашборда. Сгруппированы в одном файле, чтобы контракт было видно целиком. */
public final class DashboardDtos {

    private DashboardDtos() {
    }

    public record Overview(
            String from,
            String to,
            long sessionsStarted,
            long assessedSessions,
            /** Число сессий по последней оценке каждого уровня. */
            Map<String, Long> levels,
            long anomalies,
            long highRisk,
            long unknown,
            List<TimelineBucket> timeline,
            List<AssessmentRow> recent
    ) {
    }

    public record TimelineBucket(String hour, Map<String, Long> counts) {
    }

    public record AssessmentRow(
            String createdAt,
            String sessionId,
            String userId,
            Integer riskScore,
            String riskLevel,
            String recommendation,
            List<String> reasons,
            String modelVersion
    ) {
    }

    public record SessionDetail(
            String sessionId,
            String userId,
            String startedAt,
            String endedAt,
            String status,
            String deviceType,
            String sdkVersion,
            int eventCount,
            /** Сколько событий каждого типа. Значения полей не отдаём. */
            Map<String, Long> eventTypes,
            /** Признаки сессии (null — нет данных). */
            Map<String, Double> features,
            AssessmentRow latest,
            List<AssessmentRow> history
    ) {
    }

    public record Page<T>(List<T> content, int page, int size, long totalElements) {
    }
}
