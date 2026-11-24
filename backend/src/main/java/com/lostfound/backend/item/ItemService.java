package com.lostfound.backend.item;

import com.lostfound.backend.common.PageResponse;
import com.lostfound.backend.user.UserRepository;
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

    public ItemService(ItemRepository itemRepository,
                       CategoryRepository categoryRepository,
                       UserRepository userRepository) {
        this.itemRepository = itemRepository;
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public ItemResponse create(Long userId, ItemRequest request) {
        Category category = findCategory(request.categoryId());
        Item item = new Item(userRepository.getReferenceById(userId), request.type(), category);
        applyRequest(item, request, category);
        Item saved = itemRepository.save(item);
        return ItemResponse.from(findItem(saved.getId()));
    }

    @Transactional(readOnly = true)
    public ItemResponse get(Long id) {
        return ItemResponse.from(findItem(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<ItemResponse> search(ItemType type, Long categoryId, ItemStatus status, Pageable pageable) {
        return PageResponse.from(itemRepository.search(type, categoryId, status, pageable), ItemResponse::from);
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
        return ItemResponse.from(item);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        itemRepository.delete(findOwnedItem(userId, id));
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
