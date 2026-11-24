package com.lostfound.backend.item;

import com.lostfound.backend.common.PageResponse;
import com.lostfound.backend.config.CacheConfig;
import com.lostfound.backend.match.MatchService;
import com.lostfound.backend.user.UserRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ItemService {

    private final ItemRepository itemRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final ItemCacheEvictor cacheEvictor;
    private final MatchService matchService;

    public ItemService(ItemRepository itemRepository,
                       CategoryRepository categoryRepository,
                       UserRepository userRepository,
                       ItemCacheEvictor cacheEvictor,
                       MatchService matchService) {
        this.itemRepository = itemRepository;
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
        this.cacheEvictor = cacheEvictor;
        this.matchService = matchService;
    }

    @Transactional
    public ItemResponse create(Long userId, ItemRequest request) {
        Category category = findCategory(request.categoryId());
        Item item = new Item(userRepository.getReferenceById(userId), request.type(), category);
        applyRequest(item, request, category);
        Item saved = itemRepository.save(item);
        cacheEvictor.searchResultsChanged();
        Item loaded = findItem(saved.getId());
        matchService.notifyStrongMatches(loaded); // tell people whose posts look like this new one
        return ItemResponse.from(loaded);
    }

    @Cacheable(cacheNames = CacheConfig.ITEMS, key = "#id")
    @Transactional(readOnly = true)
    public ItemResponse get(Long id) {
        return ItemResponse.from(findItem(id));
    }

    /** Cached briefly: the same popular searches (e.g. "LOST, Phone, page 0") are asked for again and again. */
    @Cacheable(cacheNames = CacheConfig.ITEM_SEARCH, key = "#criteria.toString() + '|' + #pageable.toString()")
    @Transactional(readOnly = true)
    public PageResponse<ItemResponse> search(ItemSearchCriteria criteria, Pageable pageable) {
        return PageResponse.from(
                itemRepository.findAll(ItemSpecifications.matching(criteria), pageable),
                item -> ItemResponse.from(item, criteria));
    }

    @Transactional(readOnly = true)
    public PageResponse<ItemResponse> listByOwner(Long userId, Pageable pageable) {
        return PageResponse.from(itemRepository.findByOwnerId(userId, pageable), ItemResponse::from);
    }

    @Transactional
    public ItemResponse update(Long userId, Long id, ItemRequest request) {
        Item item = findOwnedItem(userId, id);
        Category category = findCategory(request.categoryId());
        applyRequest(item, request, category);
        itemRepository.flush();
        cacheEvictor.itemChanged(id);
        return ItemResponse.from(item);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        itemRepository.delete(findOwnedItem(userId, id));
        cacheEvictor.itemChanged(id);
    }

    private void applyRequest(Item item, ItemRequest request, Category category) {
        item.applyDetails(
                request.type(),
                request.title().trim(),
                request.description(),
                category,
                request.locationText().trim(),
                request.lat(),
                request.lng(),
                request.eventDate(),
                request.verificationQuestion()
        );
    }

    private Item findItem(Long id) {
        return itemRepository.findWithDetailsById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found"));
    }

    private Item findOwnedItem(Long userId, Long id) {
        Item item = findItem(id);
        if (!item.isOwnedBy(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only change your own items");
        }
        return item;
    }

    private Category findCategory(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown category"));
    }
}
