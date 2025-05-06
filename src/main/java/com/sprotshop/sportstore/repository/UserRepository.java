package com.sprotshop.sportstore.repository;

import com.sprotshop.sportstore.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository interface for User entity
 * Provides CRUD operations and custom query methods for User entities
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    

    Optional<User> findByEmail(String email);
    
    /**
     * Check if a user with the given email exists
     * @param email the email to check
     * @return true if a user with the email exists, false otherwise
     */
    boolean existsByEmail(String email);
    
    /**
     * Find a user by username
     * @param username the username to search for
     * @return the user with the given username, or null if not found
     */
    User findByUsername(String username);
    
    /**
     * Check if a user with the given username exists
     * @param username the username to check
     * @return true if a user with the username exists, false otherwise
     */
    boolean existsByUsername(String username);
}