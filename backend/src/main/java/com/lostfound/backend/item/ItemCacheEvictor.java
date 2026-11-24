package com.lostfound.backend.item;

import com.lostfound.backend.config.CacheConfig;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

/** Call this after changing an item, so readers don't see old data from the cache. */
@Component
public class ItemCacheEvictor {

    private final CacheManager cacheManager;

    public ItemCacheEvictor(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    /** An existing item was edited, deleted or changed status. */
    public void itemChanged(Long itemId) {
        Cache items = cacheManager.getCache(CacheConfig.ITEMS);
        if (items != null) items.evict(itemId);
        searchResultsChanged();
    }

    /** Any change that can affect which items a search returns. */
    public void searchResultsChanged() {
        Cache search = cacheManager.getCache(CacheConfig.ITEM_SEARCH);
        if (search != null) search.clear();
    }
}
