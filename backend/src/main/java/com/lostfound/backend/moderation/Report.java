package com.lostfound.backend.moderation;

import com.lostfound.backend.item.Item;
import com.lostfound.backend.user.User;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "reports")
public class Report {

    public enum Reason { SPAM, SCAM, INAPPROPRIATE, OTHER }

    public enum Status { OPEN, RESOLVED, DISMISSED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id")
    private Item item;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id")
    private User reporter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Reason reason;

    private String details;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.OPEN;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant handledAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "handled_by")
    private User handledBy;

    protected Report() {
    }

    public Report(Item item, User reporter, Reason reason, String details) {
        this.item = item;
        this.reporter = reporter;
        this.reason = reason;
        this.details = details;
        this.createdAt = Instant.now();
    }

    /** RESOLVED = the admin acted on it (hid the post); DISMISSED = nothing wrong found. */
    public void close(Status outcome, User admin) {
        this.status = outcome;
        this.handledAt = Instant.now();
        this.handledBy = admin;
    }

    public Long getId() { return id; }
    public Item getItem() { return item; }
    public User getReporter() { return reporter; }
    public Reason getReason() { return reason; }
    public String getDetails() { return details; }
    public Status getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getHandledAt() { return handledAt; }
    public User getHandledBy() { return handledBy; }
}
