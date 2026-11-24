package com.lostfound.backend.photo;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemPhotoRepository extends JpaRepository<ItemPhoto, Long> {

    List<ItemPhoto> findByItemIdOrderBySortOrderAsc(Long itemId);

    int countByItemId(Long itemId);

    Optional<ItemPhoto> findByIdAndItemId(Long id, Long itemId);
}
