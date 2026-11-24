package com.lostfound.backend.item;

import com.lostfound.backend.common.PageResponse;
import jakarta.validation.Valid;
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

    /** Public: browse items, optionally filtered. Example: /api/items?type=LOST&categoryId=1&page=0&size=20 */
    @GetMapping("/api/items")
    public PageResponse<ItemResponse> search(
            @RequestParam(required = false) ItemType type,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) ItemStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return itemService.search(type, categoryId, status, pageable);
    }

    /** Public: one item's details. */
    @GetMapping("/api/items/{id}")
    public ItemResponse get(@PathVariable Long id) {
        return itemService.get(id);
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
