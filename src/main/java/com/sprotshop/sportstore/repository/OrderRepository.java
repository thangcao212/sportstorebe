package com.sprotshop.sportstore.repository;

import com.sprotshop.sportstore.Enum.OrderStatus;
import com.sprotshop.sportstore.Enum.PaymentMethod;
import com.sprotshop.sportstore.Enum.PaymentStatus;
import com.sprotshop.sportstore.entity.Order;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {
    Page<Order> findByUserId(Long userId, Pageable pageable);
    Optional<Order> findByIdAndUserId(Long id, Long userId);

    boolean existsByUser_Id(Long id);

    List<Order> findByAddressProvinceCode(int provinceCode);
    //    List<Order> findByAddressDistrictCode(int districtCode);
    List<Order> findByAddressWardCode(int wardCode);

    @Query("SELECT a.provinceCode, COUNT(o) FROM Order o JOIN o.address a GROUP BY a.provinceCode")
    List<Object[]> countOrdersByProvince();

//    @Query("SELECT a.districtCode, COUNT(o) FROM Order o JOIN o.address a GROUP BY a.districtCode")
//    List<Object[]> countOrdersByDistrict();

    @Query("SELECT a.wardCode, COUNT(o) FROM Order o JOIN o.address a GROUP BY a.wardCode")
    List<Object[]> countOrdersByWard();

    // Daily transactions trong 1 tháng/năm
    @Query("SELECT DAY(o.createdAt) as day, COUNT(o) as cnt " +
            "FROM Order o " +
            "WHERE MONTH(o.createdAt) = :month AND YEAR(o.createdAt) = :year " +
            "GROUP BY DAY(o.createdAt) ORDER BY day")
    List<Object[]> countOrdersByDay(@Param("month") int month, @Param("year") int year);

    // Weekly transactions trong khoảng ngày (dùng MySQL DAYOFWEEK)
    @Query(value = "SELECT DAYOFWEEK(o.created_at) as dow, COUNT(*) as cnt " +
            "FROM orders o " +
            "WHERE DATE(o.created_at) BETWEEN :start AND :end " +
            "GROUP BY DAYOFWEEK(o.created_at) ORDER BY dow",
            nativeQuery = true)
    List<Object[]> countOrdersByDateRange(@Param("start") LocalDate start, @Param("end") LocalDate end);  // 👈 FIXED: LocalDate

    // KPI
    @Query("SELECT COALESCE(SUM(o.totalAmount),0) FROM Order o")
    BigDecimal sumTotalAmount();

    Long countByStatus(OrderStatus status);

    //
    // Đếm theo trạng thái
    @Query("SELECT o.status, COUNT(o) FROM Order o GROUP BY o.status")
    List<Object[]> countOrdersByStatus();

    // Doanh thu theo tháng
    @Query("SELECT MONTH(o.createdAt), SUM(o.totalAmount) " +
            "FROM Order o WHERE YEAR(o.createdAt) = :year " +
            "GROUP BY MONTH(o.createdAt)")
    List<Object[]> sumRevenueByMonth(@Param("year") int year);

    @Query("""
    SELECT p.id, p.name,
           (SELECT pi.imageUrl FROM Image pi 
            WHERE pi.product.id = p.id 
            ORDER BY pi.id ASC LIMIT 1),
           COALESCE(SUM(od.quantity), 0) AS total
    FROM Product p
    LEFT JOIN OrderItem od ON od.product.id = p.id
    LEFT JOIN od.order o
    WHERE o.status = com.sprotshop.sportstore.Enum.OrderStatus.COMPLETED
        OR o.id IS NULL
    GROUP BY p.id, p.name
    ORDER BY total DESC
""")
    List<Object[]> findTopProducts(Pageable pageable);

    @Query("""
    SELECT p.id, p.name,
           (SELECT pi.imageUrl FROM Image pi 
            WHERE pi.product.id = p.id 
            ORDER BY pi.id ASC LIMIT 1),
           COALESCE(SUM(od.quantity), 0) AS total
    FROM Product p
    LEFT JOIN OrderItem od ON od.product.id = p.id
    LEFT JOIN od.order o
    WHERE o.status = com.sprotshop.sportstore.Enum.OrderStatus.COMPLETED
        OR o.id IS NULL        
    GROUP BY p.id, p.name
    ORDER BY total ASC
""")
    List<Object[]> findWorstProducts(Pageable pageable);



    // Trong com.sprotshop.sportstore.repository.OrderRepository extends JpaRepository<Order, Long>
    Optional<Order> findByIdAndTotalAmountAndPaymentStatus(Long id, BigDecimal totalAmount, PaymentStatus paymentStatus);

    @Query("SELECT o FROM Order o WHERE o.status = :status AND o.createdAt < :threshold AND o.paymentMethod = :method")
    List<Order> findByStatusAndCreatedAtBeforeAndPaymentMethod(@Param("status") OrderStatus status,
                                                               @Param("threshold") LocalDateTime threshold,
                                                               @Param("method") PaymentMethod method);

    List<Order> findByUserId(Long user_id);

    @Query("SELECT DISTINCT u.email FROM User u ORDER BY u.email")
    List<String> findUniqueUserEmails();

    // NEW: For enhanced SEPAY timeout scheduler
    @Query("SELECT o FROM Order o WHERE o.status = :status AND o.createdAt < :threshold AND o.paymentMethod = :method AND o.paymentStatus = :paymentStatus")
    List<Order> findByStatusAndCreatedAtBeforeAndPaymentMethodAndPaymentStatus(
            @Param("status") OrderStatus status,
            @Param("threshold") LocalDateTime threshold,
            @Param("method") PaymentMethod method,
            @Param("paymentStatus") PaymentStatus paymentStatus
    );

    // Existing findByStatusAndCreatedAtBefore for auto-complete
    @Query("SELECT o FROM Order o WHERE o.status = :status AND o.createdAt < :threshold")
    List<Order> findByStatusAndCreatedAtBefore(@Param("status") OrderStatus status, @Param("threshold") LocalDateTime threshold);

    long countByUserIdAndCouponIdAndStatus(Long userId, Long couponId, OrderStatus status);

    long countByAddressId(Long addressId);


    // 👈 FIXED: sumTotalCost - Correlated subquery toàn bộ
    @Query("SELECT SUM(COALESCE((SELECT SUM(oi2.quantity * p2.costPrice) FROM OrderItem oi2 JOIN oi2.product p2 WHERE oi2.order = o), 0)) FROM Order o")
    BigDecimal sumTotalCost();



    // 👈 FIXED: findTopProductsByProfit - No subquery needed, direct calc
    @Query("""
SELECT p.id, p.name,
       (SELECT pi.imageUrl FROM Image pi 
        WHERE pi.product.id = p.id 
        ORDER BY pi.id ASC LIMIT 1),
       SUM((oi.price - p.costPrice) * oi.quantity) as profit
FROM OrderItem oi 
JOIN oi.product p 
JOIN oi.order o
WHERE o.status = com.sprotshop.sportstore.Enum.OrderStatus.COMPLETED
GROUP BY p.id, p.name
ORDER BY profit DESC
""")
    List<Object[]> findTopProductsByProfit(Pageable pageable);

    // OrderRepository.java
    // OrderRepository.java
    // OrderRepository.java
    // OrderRepository.java - sửa method này
    @Query(value = """
    SELECT 
        p.id,
        p.name,
        (SELECT i.image_url FROM image i WHERE i.product_id = p.id ORDER BY i.id ASC LIMIT 1) AS imageUrl,
        p.price,
        SUM(oi.quantity) AS totalSold,
        COALESCE(p.average_rating, 0.0) AS avgRating,
        COALESCE(p.review_count, 0) AS reviewCnt,
        b.name AS brandName
    FROM orders o
    JOIN order_item oi ON o.id = oi.order_id
    JOIN product p ON oi.product_id = p.id
    LEFT JOIN brands b ON p.brand_id = b.id
    WHERE o.status IN ('DELIVERED', 'COMPLETED')
      AND o.created_at >= DATE_SUB(CURDATE(), INTERVAL 30 DAY)
    GROUP BY p.id, p.name, p.price, p.average_rating, p.review_count, b.name
    ORDER BY totalSold DESC
    LIMIT :limit
    """, nativeQuery = true)
    List<Object[]> findBestSellingProductsRaw(@Param("limit") int limit);

    // 1. Lợi nhuận theo ngày
    @Query("""
        SELECT DAY(o.createdAt),
               SUM(oi.price * oi.quantity) 
               - SUM(oi.quantity * p.costPrice) 
               - SUM(COALESCE(o.discountAmount, 0))
        FROM Order o
        JOIN o.orderItems oi
        JOIN oi.product p
        WHERE MONTH(o.createdAt) = :month 
          AND YEAR(o.createdAt) = :year
          AND o.status IN ('COMPLETED', 'DELIVERED')
        GROUP BY DAY(o.createdAt)
        ORDER BY DAY(o.createdAt)
        """)
    List<Object[]> sumProfitByDay(@Param("month") int month, @Param("year") int year);

    // 2. Lợi nhuận theo tháng
    @Query("""
        SELECT MONTH(o.createdAt),
               SUM(oi.price * oi.quantity) 
               - SUM(oi.quantity * p.costPrice) 
               - SUM(COALESCE(o.discountAmount, 0))
        FROM Order o 
        JOIN o.orderItems oi 
        JOIN oi.product p
        WHERE YEAR(o.createdAt) = :year
          AND o.status IN ('COMPLETED', 'DELIVERED')
        GROUP BY MONTH(o.createdAt)
        ORDER BY MONTH(o.createdAt)
        """)
    List<Object[]> sumProfitByMonth(@Param("year") int year);

    // 3. Lợi nhuận theo tuần
    @Query("""
        SELECT DAYOFWEEK(o.createdAt),
               SUM(oi.price * oi.quantity) 
               - SUM(oi.quantity * p.costPrice) 
               - SUM(COALESCE(o.discountAmount, 0))
        FROM Order o 
        JOIN o.orderItems oi 
        JOIN oi.product p
        WHERE DATE(o.createdAt) BETWEEN :start AND :end
          AND o.status IN ('COMPLETED', 'DELIVERED')
        GROUP BY DAYOFWEEK(o.createdAt)
        ORDER BY DAYOFWEEK(o.createdAt)
        """)
    List<Object[]> sumProfitByDateRange(LocalDate start, LocalDate end);

    // 4. Lợi nhuận theo năm (nhiều năm)
    @Query("""
        SELECT YEAR(o.createdAt),
               SUM(oi.price * oi.quantity) 
               - SUM(oi.quantity * p.costPrice) 
               - SUM(COALESCE(o.discountAmount, 0))
        FROM Order o 
        JOIN o.orderItems oi 
        JOIN oi.product p
        WHERE DATE(o.createdAt) BETWEEN :start AND :end
          AND o.status IN ('COMPLETED', 'DELIVERED')
        GROUP BY YEAR(o.createdAt)
        ORDER BY YEAR(o.createdAt) DESC
        """)
    List<Object[]> sumProfitByYearRange(LocalDate start, LocalDate end);

    // 5. Tổng lợi nhuận toàn shop (dùng trong Summary)
    @Query("""
        SELECT SUM(oi.price * oi.quantity) 
               - SUM(oi.quantity * p.costPrice) 
               - SUM(COALESCE(o.discountAmount, 0))
        FROM Order o 
        JOIN o.orderItems oi 
        JOIN oi.product p
        WHERE o.status IN ('COMPLETED', 'DELIVERED')
        """)
    BigDecimal sumTotalProfit();


    // Thêm vào OrderRepository.java

    // 1. Top sản phẩm bán chạy nhất theo tuần
    @Query(value = """
    SELECT p.id, p.name, 
           COALESCE((SELECT i.image_url FROM image i WHERE i.product_id = p.id ORDER BY i.id ASC LIMIT 1), '/images/default-product.jpg') AS imageUrl,
           p.price,
           COALESCE(SUM(oi.quantity), 0) AS totalSold,
           COALESCE(p.average_rating, 0.0) AS avgRating,
           COALESCE(p.review_count, 0) AS reviewCnt,
           COALESCE(b.name, '') AS brandName
    FROM product p
    LEFT JOIN brands b ON p.brand_id = b.id
    LEFT JOIN order_item oi ON p.id = oi.product_id
    LEFT JOIN orders o ON oi.order_id = o.id 
        AND o.status IN ('DELIVERED', 'COMPLETED')
        AND YEARWEEK(o.created_at, 1) = :yearWeek
    GROUP BY p.id, p.name, p.price, p.average_rating, p.review_count, b.name
    ORDER BY totalSold DESC
    LIMIT :limit
    """, nativeQuery = true)
    List<Object[]> findBestSellingProductsByWeek(@Param("yearWeek") Integer yearWeek, @Param("limit") int limit);

    // 2. Top sản phẩm bán ít nhất theo tuần
    @Query(value = """
    SELECT p.id, p.name, 
           COALESCE((SELECT i.image_url FROM image i WHERE i.product_id = p.id ORDER BY i.id ASC LIMIT 1), '/images/default-product.jpg') AS imageUrl,
           p.price,
           COALESCE(SUM(oi.quantity), 0) AS totalSold,
           COALESCE(p.average_rating, 0.0) AS avgRating,
           COALESCE(p.review_count, 0) AS reviewCnt,
           COALESCE(b.name, '') AS brandName
    FROM product p
    LEFT JOIN brands b ON p.brand_id = b.id
    LEFT JOIN order_item oi ON p.id = oi.product_id
    LEFT JOIN orders o ON oi.order_id = o.id 
        AND o.status IN ('DELIVERED', 'COMPLETED')
        AND YEARWEEK(o.created_at, 1) = :yearWeek
    GROUP BY p.id, p.name, p.price, p.average_rating, p.review_count, b.name
    ORDER BY totalSold ASC
    LIMIT :limit
    """, nativeQuery = true)
    List<Object[]> findWorstSellingProductsByWeek(@Param("yearWeek") Integer yearWeek, @Param("limit") int limit);

    // 3. Top sản phẩm bán chạy nhất theo tháng
    @Query(value = """
    SELECT p.id, p.name, 
           COALESCE((SELECT i.image_url FROM image i WHERE i.product_id = p.id ORDER BY i.id ASC LIMIT 1), '/images/default-product.jpg') AS imageUrl,
           p.price,
           COALESCE(SUM(oi.quantity), 0) AS totalSold,
           COALESCE(p.average_rating, 0.0) AS avgRating,
           COALESCE(p.review_count, 0) AS reviewCnt,
           COALESCE(b.name, '') AS brandName
    FROM product p
    LEFT JOIN brands b ON p.brand_id = b.id
    LEFT JOIN order_item oi ON p.id = oi.product_id
    LEFT JOIN orders o ON oi.order_id = o.id 
        AND o.status IN ('DELIVERED', 'COMPLETED')
        AND YEAR(o.created_at) = :year
        AND MONTH(o.created_at) = :month
    GROUP BY p.id, p.name, p.price, p.average_rating, p.review_count, b.name
    ORDER BY totalSold DESC
    LIMIT :limit
    """, nativeQuery = true)
    List<Object[]> findBestSellingProductsByMonth(@Param("year") Integer year, @Param("month") Integer month, @Param("limit") int limit);

    // 4. Top sản phẩm bán ít nhất theo tháng
    @Query(value = """
    SELECT p.id, p.name, 
           COALESCE((SELECT i.image_url FROM image i WHERE i.product_id = p.id ORDER BY i.id ASC LIMIT 1), '/images/default-product.jpg') AS imageUrl,
           p.price,
           COALESCE(SUM(oi.quantity), 0) AS totalSold,
           COALESCE(p.average_rating, 0.0) AS avgRating,
           COALESCE(p.review_count, 0) AS reviewCnt,
           COALESCE(b.name, '') AS brandName
    FROM product p
    LEFT JOIN brands b ON p.brand_id = b.id
    LEFT JOIN order_item oi ON p.id = oi.product_id
    LEFT JOIN orders o ON oi.order_id = o.id 
        AND o.status IN ('DELIVERED', 'COMPLETED')
        AND YEAR(o.created_at) = :year
        AND MONTH(o.created_at) = :month
    GROUP BY p.id, p.name, p.price, p.average_rating, p.review_count, b.name
    ORDER BY totalSold ASC
    LIMIT :limit
    """, nativeQuery = true)
    List<Object[]> findWorstSellingProductsByMonth(@Param("year") Integer year, @Param("month") Integer month, @Param("limit") int limit);

    // 5. Top sản phẩm bán chạy nhất theo năm
    @Query(value = """
    SELECT p.id, p.name, 
           COALESCE((SELECT i.image_url FROM image i WHERE i.product_id = p.id ORDER BY i.id ASC LIMIT 1), '/images/default-product.jpg') AS imageUrl,
           p.price,
           COALESCE(SUM(oi.quantity), 0) AS totalSold,
           COALESCE(p.average_rating, 0.0) AS avgRating,
           COALESCE(p.review_count, 0) AS reviewCnt,
           COALESCE(b.name, '') AS brandName
    FROM product p
    LEFT JOIN brands b ON p.brand_id = b.id
    LEFT JOIN order_item oi ON p.id = oi.product_id
    LEFT JOIN orders o ON oi.order_id = o.id 
        AND o.status IN ('DELIVERED', 'COMPLETED')
        AND YEAR(o.created_at) = :year
    GROUP BY p.id, p.name, p.price, p.average_rating, p.review_count, b.name
    ORDER BY totalSold DESC
    LIMIT :limit
    """, nativeQuery = true)
    List<Object[]> findBestSellingProductsByYear(@Param("year") Integer year, @Param("limit") int limit);

    // 6. Top sản phẩm bán ít nhất theo năm
    @Query(value = """
    SELECT p.id, p.name, 
           COALESCE((SELECT i.image_url FROM image i WHERE i.product_id = p.id ORDER BY i.id ASC LIMIT 1), '/images/default-product.jpg') AS imageUrl,
           p.price,
           COALESCE(SUM(oi.quantity), 0) AS totalSold,
           COALESCE(p.average_rating, 0.0) AS avgRating,
           COALESCE(p.review_count, 0) AS reviewCnt,
           COALESCE(b.name, '') AS brandName
    FROM product p
    LEFT JOIN brands b ON p.brand_id = b.id
    LEFT JOIN order_item oi ON p.id = oi.product_id
    LEFT JOIN orders o ON oi.order_id = o.id 
        AND o.status IN ('DELIVERED', 'COMPLETED')
        AND YEAR(o.created_at) = :year
    GROUP BY p.id, p.name, p.price, p.average_rating, p.review_count, b.name
    ORDER BY totalSold ASC
    LIMIT :limit
    """, nativeQuery = true)
    List<Object[]> findWorstSellingProductsByYear(@Param("year") Integer year, @Param("limit") int limit);

    // Helper: Lấy năm-tuần hiện tại
    @Query(value = "SELECT YEARWEEK(CURDATE(), 1)", nativeQuery = true)
    Integer getCurrentYearWeek();

    // Helper: Lấy năm hiện tại
    @Query(value = "SELECT YEAR(CURDATE())", nativeQuery = true)
    Integer getCurrentYear();

    // Helper: Lấy tháng hiện tại
    @Query(value = "SELECT MONTH(CURDATE())", nativeQuery = true)
    Integer getCurrentMonth();

    List<Order> findByStatusAndPaymentMethodAndPaymentStatusAndCreatedAtBefore(
            OrderStatus status,
            PaymentMethod paymentMethod,
            PaymentStatus paymentStatus,
            LocalDateTime createdAtBefore
    );
}