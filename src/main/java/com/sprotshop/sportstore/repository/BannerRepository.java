package com.sprotshop.sportstore.repository;

import com.sprotshop.sportstore.entity.Banner;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BannerRepository extends JpaRepository<Banner, Long> {
    // Simple active filter with paging (Spring Data will auto-order if needed, but explicit better)
    Page<Banner> findByActiveTrue(Pageable pageable);

    // Paged active with explicit order (using @Query to avoid naming issues)
    @Query("SELECT b FROM Banner b WHERE b.active = true ORDER BY b.displayOrder ASC")
    Page<Banner> findActiveBannersOrdered(Pageable pageable);

    // Non-paged active ordered
    List<Banner> findByActiveTrueOrderByDisplayOrderAsc();

    // All with paging and order
    @Query("SELECT b FROM Banner b ORDER BY b.displayOrder ASC")
    Page<Banner> findAllOrdered(Pageable pageable);

    // Non-paged all ordered
    List<Banner> findAllByOrderByDisplayOrderAsc();
}