package com.sprotshop.sportstore.repository;

import com.sprotshop.sportstore.Enum.UserRole;
import com.sprotshop.sportstore.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for User entity
 * Provides CRUD operations and custom query methods for User entities
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    

    Optional<User> findByEmail(String email);
    

    boolean existsByEmail(String email);
    

    User findByUsername(String username);
    

    boolean existsByUsername(String username);

    // Đếm user theo ngày trong tháng
    @Query("SELECT DAY(u.createdAt), COUNT(u) " +
            "FROM User u " +
            "WHERE MONTH(u.createdAt) = :month AND YEAR(u.createdAt) = :year " +
            "GROUP BY DAY(u.createdAt)")
    List<Object[]> countNewUsersByDay(int month, int year);

    // Đếm user theo tháng trong năm
    @Query("SELECT MONTH(u.createdAt), COUNT(u) " +
            "FROM User u " +
            "WHERE YEAR(u.createdAt) = :year " +
            "GROUP BY MONTH(u.createdAt)")
    List<Object[]> countNewUsersByMonth(int year);

    // Đếm user theo năm (toàn bộ lịch sử)
    @Query("SELECT YEAR(u.createdAt), COUNT(u) " +
            "FROM User u " +
            "GROUP BY YEAR(u.createdAt)")
    List<Object[]> countNewUsersByYear();



    Page<User> findAll(Pageable pageable);

    Optional<User> findFirstByRoleOrderByIdAsc(UserRole role);
    List<User> findAllByRole(UserRole role);
}