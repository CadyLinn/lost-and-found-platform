package com.lostfound.backend.claim;

import com.lostfound.backend.item.Item;
import com.lostfound.backend.item.ItemRepository;
import com.lostfound.backend.item.ItemResponse;
import com.lostfound.backend.item.ItemStatus;
import com.lostfound.backend.notification.NotificationService;
import com.lostfound.backend.notification.NotificationType;
import com.lostfound.backend.user.UserRepository;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * The claim lifecycle:
 * <pre>
 *   someone submits a claim (PENDING) → poster approves → item CLAIMED, other pending claims REJECTED
 *                                     → poster rejects  → claim REJECTED, item stays OPEN
 *   item CLAIMED → poster marks it returned → item RETURNED
 * </pre>
 */
@Service
public class ClaimService {

    private final ClaimRepository claimRepository;
    private final ItemRepository itemRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public ClaimService(ClaimRepository claimRepository,
                        ItemRepository itemRepository,
                        UserRepository userRepository,
                        NotificationService notificationService) {
        this.claimRepository = claimRepository;
        this.itemRepository = itemRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    public ClaimResponse submit(Long userId, Long itemId, ClaimRequest request) {
        Item item = itemRepository.findWithDetailsById(itemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found"));

        if (item.isOwnedBy(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot claim your own item");
        }
        if (item.getStatus() != ItemStatus.OPEN) {
            throw conflict("This item is no longer open");
        }
        if (claimRepository.existsByItemIdAndClaimantId(itemId, userId)) {
            throw conflict("You have already responded to this item");
        }
        String answer = trimToNull(request.answer());
        if (item.getVerificationQuestion() != null && answer == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please answer the verification question");
        }

        Claim claim = new Claim(item, userRepository.getReferenceById(userId), answer, request.message().trim());
        try {
            claimRepository.saveAndFlush(claim);
        } catch (DataIntegrityViolationException e) {
            // Two requests from the same user at the same moment: the unique constraint caught the second one.
            throw conflict("You have already responded to this item");
        }
        notificationService.notify(item.getOwner(), NotificationType.CLAIM_RECEIVED, item);
        return ClaimResponse.forClaimant(claimRepository.findWithDetailsById(claim.getId()).orElseThrow());
    }

    @Transactional(readOnly = true)
    public List<ClaimResponse> listForItem(Long userId, Long itemId) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found"));
        requirePoster(item, userId);
        return claimRepository.findByItemIdOrderByCreatedAtAsc(itemId).stream()
                .map(ClaimResponse::forPoster)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ClaimResponse> listMine(Long userId) {
        return claimRepository.findByClaimantIdOrderByCreatedAtDesc(userId).stream()
                .map(ClaimResponse::forClaimant)
                .toList();
    }

    @Transactional
    public ClaimResponse approve(Long userId, Long claimId) {
        // Lock the item *before* reading anything else, so a parallel approve waits and then sees fresh data.
        Item item = lockItemOfClaim(claimId);
        Claim claim = findClaim(claimId);
        requirePoster(item, userId);
        requirePending(claim);
        if (item.getStatus() != ItemStatus.OPEN) {
            throw conflict("This item is no longer open");
        }

        claim.approve();
        item.markClaimed();
        notificationService.notify(claim.getClaimant(), NotificationType.CLAIM_APPROVED, item);

        // Only one person can get the item back, so everyone else still waiting is declined.
        for (Claim other : claimRepository.findByItemIdAndStatus(item.getId(), ClaimStatus.PENDING)) {
            other.reject();
            notificationService.notify(other.getClaimant(), NotificationType.CLAIM_REJECTED, item);
        }
        return ClaimResponse.forPoster(claim);
    }

    @Transactional
    public ClaimResponse reject(Long userId, Long claimId) {
        Item item = lockItemOfClaim(claimId);
        Claim claim = findClaim(claimId);
        requirePoster(item, userId);
        requirePending(claim);

        claim.reject();
        notificationService.notify(claim.getClaimant(), NotificationType.CLAIM_REJECTED, item);
        return ClaimResponse.forPoster(claim);
    }

    @Transactional
    public ItemResponse markReturned(Long userId, Long itemId) {
        Item item = lockItem(itemId);
        requirePoster(item, userId);
        if (item.getStatus() != ItemStatus.CLAIMED) {
            throw conflict("Only a claimed item can be marked as returned");
        }

        item.markReturned();
        for (Claim approved : claimRepository.findByItemIdAndStatus(itemId, ClaimStatus.APPROVED)) {
            notificationService.notify(approved.getClaimant(), NotificationType.ITEM_RETURNED, item);
        }
        itemRepository.flush();
        return ItemResponse.from(item);
    }

    private Claim findClaim(Long claimId) {
        return claimRepository.findWithDetailsById(claimId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Claim not found"));
    }

    private Item lockItemOfClaim(Long claimId) {
        Long itemId = claimRepository.findItemIdById(claimId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Claim not found"));
        return lockItem(itemId);
    }

    private Item lockItem(Long itemId) {
        return itemRepository.findForUpdate(itemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found"));
    }

    private static void requirePoster(Item item, Long userId) {
        if (!item.isOwnedBy(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the poster can manage claims");
        }
    }

    private static void requirePending(Claim claim) {
        if (claim.getStatus() != ClaimStatus.PENDING) {
            throw conflict("This claim has already been decided");
        }
    }

    private static ResponseStatusException conflict(String reason) {
        return new ResponseStatusException(HttpStatus.CONFLICT, reason);
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
