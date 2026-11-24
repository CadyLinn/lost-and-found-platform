package com.lostfound.backend.claim;

import com.lostfound.backend.item.ItemResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
public class ClaimController {

    private final ClaimService claimService;

    public ClaimController(ClaimService claimService) {
        this.claimService = claimService;
    }

    @PostMapping("/api/items/{itemId}/claims")
    @ResponseStatus(HttpStatus.CREATED)
    public ClaimResponse submit(@AuthenticationPrincipal Jwt jwt, @PathVariable Long itemId,
                                @Valid @RequestBody ClaimRequest request) {
        return claimService.submit(userId(jwt), itemId, request);
    }

    /** Poster only: everyone who responded to this item. */
    @GetMapping("/api/items/{itemId}/claims")
    public List<ClaimResponse> listForItem(@AuthenticationPrincipal Jwt jwt, @PathVariable Long itemId) {
        return claimService.listForItem(userId(jwt), itemId);
    }

    @GetMapping("/api/me/claims")
    public List<ClaimResponse> mine(@AuthenticationPrincipal Jwt jwt) {
        return claimService.listMine(userId(jwt));
    }

    @PostMapping("/api/claims/{claimId}/approve")
    public ClaimResponse approve(@AuthenticationPrincipal Jwt jwt, @PathVariable Long claimId) {
        return claimService.approve(userId(jwt), claimId);
    }

    @PostMapping("/api/claims/{claimId}/reject")
    public ClaimResponse reject(@AuthenticationPrincipal Jwt jwt, @PathVariable Long claimId) {
        return claimService.reject(userId(jwt), claimId);
    }

    @PostMapping("/api/items/{itemId}/returned")
    public ItemResponse markReturned(@AuthenticationPrincipal Jwt jwt, @PathVariable Long itemId) {
        return claimService.markReturned(userId(jwt), itemId);
    }

    private static Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
