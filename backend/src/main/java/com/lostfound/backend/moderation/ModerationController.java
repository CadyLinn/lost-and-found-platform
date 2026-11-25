package com.lostfound.backend.moderation;

import com.lostfound.backend.item.ItemResponse;
import com.lostfound.backend.moderation.ModerationDtos.HideRequest;
import com.lostfound.backend.moderation.ModerationDtos.ReportRequest;
import com.lostfound.backend.moderation.ModerationDtos.ReportResponse;
import com.lostfound.backend.moderation.ModerationDtos.Stats;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
public class ModerationController {

    private final ModerationService moderationService;

    public ModerationController(ModerationService moderationService) {
        this.moderationService = moderationService;
    }

    /** Any logged-in user: flag someone else's post for an admin to review. */
    @PostMapping("/api/items/{itemId}/reports")
    @ResponseStatus(HttpStatus.CREATED)
    public void report(@AuthenticationPrincipal Jwt jwt, @PathVariable Long itemId,
                       @Valid @RequestBody ReportRequest request) {
        moderationService.report(userId(jwt), itemId, request);
    }

    // ----- Everything below is under /api/admin, which SecurityConfig restricts to ROLE_ADMIN. -----

    @GetMapping("/api/admin/stats")
    public Stats stats() {
        return moderationService.stats();
    }

    @GetMapping("/api/admin/reports")
    public List<ReportResponse> reports(@RequestParam(defaultValue = "OPEN") Report.Status status) {
        return moderationService.reports(status);
    }

    @GetMapping("/api/admin/items/hidden")
    public List<ItemResponse> hiddenItems() {
        return moderationService.hiddenItems();
    }

    @PostMapping("/api/admin/items/{itemId}/hide")
    public ItemResponse hide(@AuthenticationPrincipal Jwt jwt, @PathVariable Long itemId,
                             @Valid @RequestBody(required = false) HideRequest request) {
        return moderationService.hide(userId(jwt), itemId, request == null ? null : request.reason());
    }

    @PostMapping("/api/admin/items/{itemId}/unhide")
    public ItemResponse unhide(@PathVariable Long itemId) {
        return moderationService.unhide(itemId);
    }

    @PostMapping("/api/admin/items/{itemId}/dismiss-reports")
    public Map<String, Integer> dismissReports(@AuthenticationPrincipal Jwt jwt, @PathVariable Long itemId) {
        return Map.of("dismissed", moderationService.dismissReports(userId(jwt), itemId));
    }

    private static Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
