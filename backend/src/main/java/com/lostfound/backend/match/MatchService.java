package com.lostfound.backend.match;

import com.lostfound.backend.item.Item;
import com.lostfound.backend.item.ItemRepository;
import com.lostfound.backend.item.ItemResponse;
import com.lostfound.backend.item.ItemStatus;
import com.lostfound.backend.item.ItemType;
import com.lostfound.backend.notification.NotificationService;
import com.lostfound.backend.notification.NotificationType;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Suggests posts that might be about the same object: for a LOST post, FOUND posts, and vice versa.
 *
 * <ol>
 *   <li>The database narrows it down: opposite type, still open, someone else's, within 30 days.</li>
 *   <li>{@link MatchScorer} scores each candidate.</li>
 *   <li>{@link TopK} keeps the best few.</li>
 * </ol>
 */
@Service
public class MatchService {

    /** Suggestions below this score are too weak to show. */
    static final double MIN_SCORE = 0.35;
    /** A new post this similar to an existing one triggers a notification to that post's author. */
    static final double NOTIFY_SCORE = 0.60;
    /** Upper bound on candidates scored per request, so one request can't scan the whole table. */
    private static final int MAX_CANDIDATES = 500;

    record Scored(Item item, MatchScorer.Result result) {
    }

    private final ItemRepository itemRepository;
    private final NotificationService notificationService;

    public MatchService(ItemRepository itemRepository, NotificationService notificationService) {
        this.itemRepository = itemRepository;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public List<MatchResponse> matchesFor(Long itemId, int limit) {
        Item item = itemRepository.findWithDetailsById(itemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found"));
        if (item.getStatus() != ItemStatus.OPEN) {
            return List.of(); // already claimed or closed: nothing left to match
        }
        return findBest(item, limit).stream()
                .map(s -> new MatchResponse(
                        ItemResponse.from(s.item()),
                        (int) Math.round(s.result().score() * 100),
                        s.result().reasons(),
                        s.result().distanceKm()))
                .toList();
    }

    /** Called right after a post is created, inside the same transaction. */
    public void notifyStrongMatches(Item newItem) {
        for (Scored match : findBest(newItem, 5)) {
            if (match.result().score() >= NOTIFY_SCORE) {
                notificationService.notify(match.item().getOwner(), NotificationType.MATCH_FOUND, newItem);
            }
        }
    }

    List<Scored> findBest(Item item, int limit) {
        TopK<Scored> best = new TopK<>(limit, Comparator.comparingDouble(s -> s.result().score()));
        for (Item candidate : candidatesFor(item)) {
            MatchScorer.Result result = MatchScorer.score(item, candidate);
            if (result.score() >= MIN_SCORE) {
                best.offer(new Scored(candidate, result));
            }
        }
        return best.best();
    }

    private List<Item> candidatesFor(Item item) {
        ItemType opposite = item.getType() == ItemType.LOST ? ItemType.FOUND : ItemType.LOST;
        LocalDate from = item.getEventDate().minusDays(MatchScorer.MAX_DAYS_APART);
        LocalDate to = item.getEventDate().plusDays(MatchScorer.MAX_DAYS_APART);
        Long ownerId = item.getOwner().getId();

        Specification<Item> spec = (root, query, cb) -> cb.and(
                cb.equal(root.get("type"), opposite),
                cb.equal(root.get("status"), ItemStatus.OPEN),
                cb.notEqual(root.get("owner").get("id"), ownerId),
                cb.between(root.<LocalDate>get("eventDate"), from, to));

        var newestFirst = PageRequest.of(0, MAX_CANDIDATES, Sort.by(Sort.Direction.DESC, "createdAt"));
        return itemRepository.findAll(spec, newestFirst).getContent();
    }
}
