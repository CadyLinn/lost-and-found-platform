package com.lostfound.backend.claim;

import com.lostfound.backend.item.Item;
import com.lostfound.backend.user.User;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "claims")
public class Claim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id")
    private Item item;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claimant_id")
    private User claimant;

    private String answer;

    @Column(nullable = false)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClaimStatus status = ClaimStatus.PENDING;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant decidedAt;

    protected Claim() {
    }

    public Claim(Item item, User claimant, String answer, String message) {
        this.item = item;
        this.claimant = claimant;
        this.answer = answer;
        this.message = message;
        this.createdAt = Instant.now();
    }

    public void approve() {
        decide(ClaimStatus.APPROVED);
    }

    public void reject() {
        decide(ClaimStatus.REJECTED);
    }

    private void decide(ClaimStatus decision) {
        if (status != ClaimStatus.PENDING) {
            throw new IllegalStateException("Claim has already been decided");
        }
        this.status = decision;
        this.decidedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Item getItem() { return item; }
    public User getClaimant() { return claimant; }
    public String getAnswer() { return answer; }
    public String getMessage() { return message; }
    public ClaimStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getDecidedAt() { return decidedAt; }
}
