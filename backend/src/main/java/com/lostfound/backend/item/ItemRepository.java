package com.lostfound.backend.item;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface ItemRepository extends JpaRepository<Item, Long> {

    /** Loads owner and category in the same query, so building responses does not trigger extra queries. */
    @EntityGraph(attributePaths = {"owner", "category"})
    Optional<Item> findWithDetailsById(Long id);

    @EntityGraph(attributePaths = {"owner", "category"})
    @Query("""
            select i from Item i
            where (:type is null or i.type = :type)
              and (:categoryId is null or i.category.id = :categoryId)
              and (:status is null or i.status = :status)
            """)
    Page<Item> search(ItemType type, Long categoryId, ItemStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"owner", "category"})
    Page<Item> findByOwnerId(Long ownerId, Pageable pageable);

    /** Locks the row until the transaction ends, so two people can't change its status at the same time. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Item i join fetch i.owner join fetch i.category where i.id = :id")
    Optional<Item> findForUpdate(Long id);
}
