package com.sprotshop.sportstore.repository;

import com.sprotshop.sportstore.entity.Address;
import com.sprotshop.sportstore.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Address entity
 * Provides CRUD operations and custom query methods for Address entities
 */
@Repository
public interface AddressRepository extends JpaRepository<Address, Long> {
    
    /**
     * Find all addresses for a specific user
     * @param user the user to find addresses for
     * @return list of addresses for the user
     */
    List<Address> findByUser(User user);
    
    /**
     * Find the default address for a specific user
     * @param user the user to find the default address for
     * @param isDefault true to find the default address
     * @return the default address for the user, or null if not found
     */
    Address findByUserAndIsDefault(User user, Boolean isDefault);
    
    /**
     * Find addresses by city
     * @param city the city to search for
     * @return list of addresses in the given city
     */
    List<Address> findByCity(String city);
    
    /**
     * Find addresses by recipient name containing the given string (case-insensitive)
     * @param recipientName the recipient name to search for
     * @return list of addresses with recipient names containing the given string
     */
    List<Address> findByRecipientNameContainingIgnoreCase(String recipientName);
    
    /**
     * Count the number of addresses for a specific user
     * @param user the user to count addresses for
     * @return the number of addresses for the user
     */
    long countByUser(User user);
    
    /**
     * Delete all addresses for a specific user
     * @param user the user to delete addresses for
     */
    void deleteByUser(User user);
}