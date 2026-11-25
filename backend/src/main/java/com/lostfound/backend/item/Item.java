package com.lostfound.backend.item;

import com.lostfound.backend.photo.ItemPhoto;
import com.lostfound.backend.user.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.BatchSize;

@Entity
@Table(name = "items")
public class Item {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id")
    private User owner;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ItemType type;

    @Column(nullable = false)
    private String title;

    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(nullable = false)
    private String locationText;

    private Double lat;
    private Double lng;

    @Column(nullable = false)
    private LocalDate eventDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ItemStatus status = ItemStatus.OPEN;

    private String verificationQuestion;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    /** Set when an admin hides the post; null while it's visible. */
    private Instant hiddenAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hidden_by")
    private User hiddenBy;

    private String hiddenReason;

    /** Loaded on demand; for a page of items, Hibernate fetches all their photos in one extra query. */
    @OneToMany(mappedBy = "item", cascade = CascadeType.REMOVE)
    @OrderBy("sortOrder ASC")
    @BatchSize(size = 50)
    private List<ItemPhoto> photos = new ArrayList<>();

    protected Item() {
    }

    public Item(User owner, ItemType type, Category category) {
        this.owner = owner;
        this.type = type;
        this.category = category;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    /** Copies the editable fields from a request. Owner, status and timestamps are not editable here. */
    public void applyDetails(ItemType type, String title, String description, Category category,
                             String locationText, Double lat, Double lng, LocalDate eventDate,
                             String verificationQuestion) {
        this.type = type;
        this.title = title;
        this.description = description;
        this.category = category;
        this.locationText = locationText;
        this.lat = lat;
        this.lng = lng;
        this.eventDate = eventDate;
        this.verificationQuestion = verificationQuestion;
    }

    /** OPEN → CLAIMED, when the poster accepts a claim. */
    public void markClaimed() {
        if (status != ItemStatus.OPEN) {
            throw new IllegalStateException("Only an open item can be claimed");
        }
        this.status = ItemStatus.CLAIMED;
    }

    /** CLAIMED → RETURNED, when the poster confirms the item went back to its owner. */
    public void markReturned() {
        if (status != ItemStatus.CLAIMED) {
            throw new IllegalStateException("Only a claimed item can be marked as returned");
        }
        this.status = ItemStatus.RETURNED;
    }

    /** Admin action: take the post out of public view, keeping its data. */
    public void hide(User admin, String reason) {
        this.hiddenAt = Instant.now();
        this.hiddenBy = admin;
        this.hiddenReason = reason;
    }

    public void unhide() {
        this.hiddenAt = null;
        this.hiddenBy = null;
        this.hiddenReason = null;
    }

    public boolean isHidden() {
        return hiddenAt != null;
    }

    public boolean isOwnedBy(Long userId) {
        return owner.getId().equals(userId);
    }

    public Long getId() { return id; }
    public User getOwner() { return owner; }
    public ItemType getType() { return type; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public Category getCategory() { return category; }
    public String getLocationText() { return locationText; }
    public Double getLat() { return lat; }
    public Double getLng() { return lng; }
    public LocalDate getEventDate() { return eventDate; }
    public ItemStatus getStatus() { return status; }
    public String getVerificationQuestion() { return verificationQuestion; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<ItemPhoto> getPhotos() { return photos; }
    public String getHiddenReason() { return hiddenReason; }
}
