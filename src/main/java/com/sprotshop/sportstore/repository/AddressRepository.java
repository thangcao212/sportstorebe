package com.sprotshop.sportstore.repository;

import com.sprotshop.sportstore.entity.Address;
import com.sprotshop.sportstore.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Address entity
 * Provides CRUD operations and custom query methods for Address entities
 */
@Repository
public interface AddressRepository extends JpaRepository<Address, Long> {

    @Query("SELECT a FROM Address a WHERE a.id = :id AND a.user.id = :userId")
    Optional<Address> findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    Optional<Address> findByUserIdAndIsDefaultTrue(Long userId);
    

}