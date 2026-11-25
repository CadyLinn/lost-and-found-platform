package com.lostfound.backend.moderation;

import com.lostfound.backend.common.AppMetrics;
import com.lostfound.backend.item.Item;
import com.lostfound.backend.item.ItemCacheEvictor;
import com.lostfound.backend.item.ItemRepository;
import com.lostfound.backend.item.ItemResponse;
import com.lostfound.backend.moderation.ModerationDtos.ReportRequest;
import com.lostfound.backend.moderation.ModerationDtos.ReportResponse;
import com.lostfound.backend.moderation.ModerationDtos.Stats;
import com.lostfound.backend.notification.NotificationService;
import com.lostfound.backend.notification.NotificationType;
import com.lostfound.backend.user.User;
import com.lostfound.backend.user.UserRepository;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Reports from users, and what admins do about them.
 * Access control (admins only) is enforced in SecurityConfig for every /api/admin/** URL.
 */
@Service
public class ModerationService {

    private final ReportRepository reportRepository;
    private final ItemRepository itemRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final ItemCacheEvictor cacheEvictor;
    private final AppMetrics metrics;

    public ModerationService(ReportRepository reportRepository, ItemRepository itemRepository,
                             UserRepository userRepository, NotificationService notificationService,
                             ItemCacheEvictor cacheEvictor, AppMetrics metrics) {
        this.reportRepository = reportRepository;
        this.itemRepository = itemRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.cacheEvictor = cacheEvictor;
        this.metrics = metrics;
    }

    // ---------- Users ----------

    @Transactional
    public void report(Long userId, Long itemId, ReportRequest request) {
        Item item = itemRepository.findById(itemId)
                .filter(i -> !i.isHidden())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found"));
        if (item.isOwnedBy(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot report your own item");
        }
        if (reportRepository.existsByItemIdAndReporterId(itemId, userId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You have already reported this item");
        }
        String details = request.details() == null || request.details().isBlank() ? null : request.details().trim();
        try {
            reportRepository.saveAndFlush(new Report(item, userRepository.getReferenceById(userId), request.reason(), details));
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You have already reported this item");
        }
        metrics.moderation("reported");
    }

    // ---------- Admins ----------

    @Transactional(readOnly = true)
    public Stats stats() {
        return new Stats(userRepository.count(), itemRepository.count(),
                reportRepository.countByStatus(Report.Status.OPEN), itemRepository.countByHiddenAtIsNotNull());
    }

    @Transactional(readOnly = true)
    public List<ReportResponse> reports(Report.Status status) {
        return reportRepository.findByStatusOrderByCreatedAtDesc(status, PageRequest.of(0, 200)).stream()
                .map(ReportResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ItemResponse> hiddenItems() {
        return itemRepository.findByHiddenAtIsNotNullOrderByHiddenAtDesc().stream().map(ItemResponse::from).toList();
    }

    /** Hides the post, marks its open reports as resolved, and tells the author. */
    @Transactional
    public ItemResponse hide(Long adminId, Long itemId, String reason) {
        Item item = lock(itemId);
        if (item.isHidden()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This item is already hidden");
        }
        User admin = userRepository.getReferenceById(adminId);
        item.hide(admin, reason == null || reason.isBlank() ? null : reason.trim());
        closeOpenReports(itemId, Report.Status.RESOLVED, admin);
        notificationService.notify(item.getOwner(), NotificationType.ITEM_HIDDEN, item);
        cacheEvictor.itemChanged(itemId);
        metrics.moderation("hidden");
        itemRepository.flush();
        return ItemResponse.from(item);
    }

    @Transactional
    public ItemResponse unhide(Long itemId) {
        Item item = lock(itemId);
        if (!item.isHidden()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This item is not hidden");
        }
        item.unhide();
        cacheEvictor.itemChanged(itemId);
        metrics.moderation("unhidden");
        itemRepository.flush();
        return ItemResponse.from(item);
    }

    /** "Nothing wrong here": closes the open reports and leaves the post as it is. Returns how many were closed. */
    @Transactional
    public int dismissReports(Long adminId, Long itemId) {
        lock(itemId);
        int closed = closeOpenReports(itemId, Report.Status.DISMISSED, userRepository.getReferenceById(adminId));
        metrics.moderation("dismissed");
        return closed;
    }

    private int closeOpenReports(Long itemId, Report.Status outcome, User admin) {
        List<Report> open = reportRepository.findByItemIdAndStatus(itemId, Report.Status.OPEN);
        open.forEach(report -> report.close(outcome, admin));
        return open.size();
    }

    private Item lock(Long itemId) {
        return itemRepository.findForUpdate(itemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found"));
    }
}
