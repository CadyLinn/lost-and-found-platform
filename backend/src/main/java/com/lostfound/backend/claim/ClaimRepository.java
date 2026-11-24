package com.lostfound.backend.claim;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ClaimRepository extends JpaRepository<Claim, Long> {

    boolean existsByItemIdAndClaimantId(Long itemId, Long claimantId);

    @Query("select c.item.id from Claim c where c.id = :claimId")
    Optional<Long> findItemIdById(Long claimId);

    @EntityGraph(attributePaths = {"item", "item.owner", "claimant"})
    Optional<Claim> findWithDetailsById(Long id);

    /** All claims on one item, for its poster. */
    @EntityGraph(attributePaths = {"item", "item.owner", "claimant"})
    List<Claim> findByItemIdOrderByCreatedAtAsc(Long itemId);

    /** Everything one user has claimed, newest first. */
    @EntityGraph(attributePaths = {"item", "item.owner", "claimant"})
    List<Claim> findByClaimantIdOrderByCreatedAtDesc(Long claimantId);

    @EntityGraph(attributePaths = {"claimant"})
    List<Claim> findByItemIdAndStatus(Long itemId, ClaimStatus status);
}
