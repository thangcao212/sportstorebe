package com.sprotshop.sportstore.repository;

import com.sprotshop.sportstore.entity.Image;
import com.sprotshop.sportstore.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Image entity
 * Provides CRUD operations and custom query methods for Image entities
 */
@Repository
public interface ImageRepository extends JpaRepository<Image, Long> {

    /**
     * Find all images for a specific product
     * @param product the product to find images for
     * @return list of images for the product
     */
    List<Image> findByProduct(Product product);

    /**
     * Find an image by its Cloudinary image ID
     * @param imageId the Cloudinary image ID
     * @return the image with the given ID, or null if not found
     */

    Optional<Image> findByImageId(String imageId);
//    Image findByImageId(String imageId);

    /**
     * Delete all images for a specific product
     * @param product the product to delete images for
     */
    void deleteByProduct(Product product);

    /**
     * Find an image by name
     * @param name the name to search for
     * @return the image with the given name, or null if not found
     */
    Image findByName(String name);

    /**
     * Count the number of images for a specific product
     * @param product the product to count images for
     * @return the number of images for the product
     */
    long countByProduct(Product product);
}