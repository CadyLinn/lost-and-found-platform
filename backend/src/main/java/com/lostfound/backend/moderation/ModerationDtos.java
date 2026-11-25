package com.lostfound.backend.moderation;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** Request and response shapes for reporting and moderation. */
public final class ModerationDtos {

    private ModerationDtos() {
    }

    public record ReportRequest(@NotNull Report.Reason reason, @Size(max = 500) String details) {
    }

    public record HideRequest(@Size(max = 200) String reason) {
    }

    /** One report, as an admin sees it. */
    public record ReportResponse(
            Long id,
            Long itemId,
            String itemTitle,
            boolean itemHidden,
            String itemOwnerDisplayName,
            String reporterDisplayName,
            Report.Reason reason,
            String details,
            Report.Status status,
            Instant createdAt,
            Instant handledAt,
            String handledByDisplayName
    ) {
        static ReportResponse from(Report r) {
            return new ReportResponse(
                    r.getId(),
                    r.getItem().getId(),
                    r.getItem().getTitle(),
                    r.getItem().isHidden(),
                    r.getItem().getOwner().getDisplayName(),
                    r.getReporter().getDisplayName(),
                    r.getReason(),
                    r.getDetails(),
                    r.getStatus(),
                    r.getCreatedAt(),
                    r.getHandledAt(),
                    r.getHandledBy() == null ? null : r.getHandledBy().getDisplayName());
        }
    }

    public record Stats(long users, long items, long openReports, long hiddenItems) {
    }
}
