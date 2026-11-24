package com.lostfound.backend.photo;

import com.lostfound.backend.item.Item;
import com.lostfound.backend.item.ItemCacheEvictor;
import com.lostfound.backend.item.ItemRepository;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PhotoService {

    private static final Logger log = LoggerFactory.getLogger(PhotoService.class);

    public static final int MAX_PHOTOS_PER_ITEM = 5;
    public static final long MAX_UPLOAD_BYTES = 5 * 1024 * 1024;

    private final ItemPhotoRepository photoRepository;
    private final ItemRepository itemRepository;
    private final PhotoStorage storage;
    private final ItemCacheEvictor cacheEvictor;

    public PhotoService(ItemPhotoRepository photoRepository, ItemRepository itemRepository,
                        PhotoStorage storage, ItemCacheEvictor cacheEvictor) {
        this.photoRepository = photoRepository;
        this.itemRepository = itemRepository;
        this.storage = storage;
        this.cacheEvictor = cacheEvictor;
    }

    @Transactional
    public PhotoResponse add(Long userId, Long itemId, MultipartFile file) {
        Item item = itemRepository.findForUpdate(itemId) // lock: two uploads at once can't both be photo #5
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found"));
        if (!item.isOwnedBy(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only change your own items");
        }
        int existing = photoRepository.countByItemId(itemId);
        if (existing >= MAX_PHOTOS_PER_ITEM) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A post can have at most 5 photos");
        }
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No file was uploaded");
        }
        if (file.getSize() > MAX_UPLOAD_BYTES) {
            throw new ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE, "Photos must be 5 MB or smaller");
        }

        ImageProcessor.Processed processed;
        try {
            processed = ImageProcessor.process(file.getBytes());
        } catch (ImageProcessor.InvalidImageException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This file is not a readable image");
        }

        UUID key = UUID.randomUUID();
        List<String> files = fileNames(key);
        storage.save(files.get(0), processed.full());
        storage.save(files.get(1), processed.thumbnail());
        // If the database part fails after this point, don't leave the files behind.
        onRollback(() -> files.forEach(this::deleteQuietly));

        ItemPhoto photo = photoRepository.save(new ItemPhoto(item, key, existing));
        cacheEvictor.itemChanged(itemId);
        return PhotoResponse.from(photo);
    }

    @Transactional
    public void delete(Long userId, Long itemId, Long photoId) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found"));
        if (!item.isOwnedBy(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only change your own items");
        }
        ItemPhoto photo = photoRepository.findByIdAndItemId(photoId, itemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Photo not found"));
        photoRepository.delete(photo);
        deleteFilesAfterCommit(List.of(photo.getKey()));
        cacheEvictor.itemChanged(itemId);
    }

    public Optional<byte[]> load(String fileName) {
        return storage.load(fileName);
    }

    /** Removes the files of these photos once the surrounding transaction has committed. */
    public void deleteFilesAfterCommit(Collection<UUID> keys) {
        if (keys.isEmpty()) return;
        Runnable deleteAll = () -> keys.forEach(key -> fileNames(key).forEach(this::deleteQuietly));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deleteAll.run();
                }
            });
        } else {
            deleteAll.run();
        }
    }

    private static void onRollback(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) action.run();
            }
        });
    }

    private void deleteQuietly(String fileName) {
        try {
            storage.delete(fileName);
        } catch (RuntimeException e) {
            log.warn("Could not delete photo file {}: {}", fileName, e.getMessage());
        }
    }

    static List<String> fileNames(UUID key) {
        return List.of(key + ".jpg", key + "_thumb.jpg");
    }
}
