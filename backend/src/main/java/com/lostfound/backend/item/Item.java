package com.lostfound.backend.item;

import com.lostfound.backend.user.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

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
}
