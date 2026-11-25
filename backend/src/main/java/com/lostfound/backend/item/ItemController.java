package com.lostfound.backend.item;

import com.lostfound.backend.common.PageResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
public class ItemController {

    private final ItemService itemService;

    public ItemController(ItemService itemService) {
        this.itemService = itemService;
    }

    /**
     * Public: browse and search items. Every filter is optional. Examples:
     * <ul>
     *   <li>{@code /api/items?q=錢包&type=FOUND}</li>
     *   <li>{@code /api/items?lat=25.017&lng=121.539&radiusKm=2} (within 2 km of a point)</li>
     *   <li>{@code /api/items?from=2026-11-01&to=2026-11-30&categoryId=1&page=0&size=20}</li>
     * </ul>
     */
    @GetMapping("/api/items")
    public PageResponse<ItemResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) ItemType type,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) ItemStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            @RequestParam(required = false) Double radiusKm,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        if (q != null && q.length() > 100) {
            throw badRequest("Search text is too long");
        }
        boolean anyLocation = lat != null || lng != null || radiusKm != null;
        boolean fullLocation = lat != null && lng != null && radiusKm != null;
        if (anyLocation && !fullLocation) {
            throw badRequest("Location search needs lat, lng and radiusKm");
        }
        if (fullLocation && (Math.abs(lat) > 90 || Math.abs(lng) > 180 || radiusKm <= 0 || radiusKm > 50)) {
            throw badRequest("Invalid location: radiusKm must be between 0 and 50");
        }
        if (pageable.getPageSize() > 50) {
            throw badRequest("Page size must be 50 or less");
        }
        var criteria = new ItemSearchCriteria(q, type, categoryId, status, from, to, lat, lng, radiusKm);
        return itemService.search(criteria, pageable);
    }

    private static ResponseStatusException badRequest(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }

    /** Public: one item's details. */
    @GetMapping("/api/items/{id}")
    public ItemResponse get(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        ItemResponse item = itemService.get(id);
        // A hidden post looks like it doesn't exist, except to its author and to admins.
        boolean canSeeHidden = jwt != null
                && (item.ownerId().equals(userId(jwt)) || "ADMIN".equals(jwt.getClaimAsString("role")));
        if (item.hidden() && !canSeeHidden) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found");
        }
        return item;
    }

    @PostMapping("/api/items")
    @ResponseStatus(HttpStatus.CREATED)
    public ItemResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ItemRequest request) {
        return itemService.create(userId(jwt), request);
    }

    @PutMapping("/api/items/{id}")
    public ItemResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                               @Valid @RequestBody ItemRequest request) {
        return itemService.update(userId(jwt), id, request);
    }

    @DeleteMapping("/api/items/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        itemService.delete(userId(jwt), id);
    }

    /** The logged-in user's own posts. */
    @GetMapping("/api/me/items")
    public PageResponse<ItemResponse> myItems(
            @AuthenticationPrincipal Jwt jwt,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return itemService.listByOwner(userId(jwt), pageable);
    }

    private static Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
