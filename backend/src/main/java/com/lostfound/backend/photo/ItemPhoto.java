package com.lostfound.backend.photo;

import com.lostfound.backend.item.Item;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "item_photos")
public class ItemPhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id")
    private Item item;

    @Column(name = "photo_key", nullable = false, unique = true, updatable = false)
    private UUID key;

    @Column(nullable = false)
    private int sortOrder;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected ItemPhoto() {
    }

    public ItemPhoto(Item item, UUID key, int sortOrder) {
        this.item = item;
        this.key = key;
        this.sortOrder = sortOrder;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Item getItem() { return item; }
    public UUID getKey() { return key; }
    public int getSortOrder() { return sortOrder; }
}
