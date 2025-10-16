// Updated WishlistServiceImpl.java - Use currentUser instead of userId param
package com.sprotshop.sportstore.service.impl;

import com.sprotshop.sportstore.entity.Product;
import com.sprotshop.sportstore.entity.User;
import com.sprotshop.sportstore.entity.Wishlist;
import com.sprotshop.sportstore.entity.WishlistItem;
import com.sprotshop.sportstore.exception.NotFoundException;
import com.sprotshop.sportstore.request.WishlistItemRequest;
import com.sprotshop.sportstore.response.WishlistResponse;
import com.sprotshop.sportstore.repository.ProductRepository;
import com.sprotshop.sportstore.repository.WishlistItemRepository;
import com.sprotshop.sportstore.repository.WishlistRepository;
import com.sprotshop.sportstore.service.UserService;
import com.sprotshop.sportstore.service.WishlistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class WishlistServiceImpl implements WishlistService {

    private final WishlistRepository wishlistRepository;
    private final WishlistItemRepository wishlistItemRepository;
    private final ProductRepository productRepository;
    private final UserService userService;

    @Override
    @Transactional
    public WishlistResponse createOrGetWishlist() {
        try {
            User currentUser = userService.getCurrentLoggedInUser();
            Long userId = currentUser.getId();
            log.info("Creating or getting wishlist for userId: {}", userId);
            Optional<Wishlist> optionalWishlist = wishlistRepository.findByUserId(userId);
            Wishlist wishlist = optionalWishlist.orElseGet(() -> {
                Wishlist newWishlist = Wishlist.builder().user(currentUser).build();
                return wishlistRepository.save(newWishlist);
            });
            log.info("Wishlist ready: {}", wishlist.getId());
            return WishlistResponse.fromEntity(wishlist);
        } catch (Exception e) {
            log.error("Create/get wishlist failed: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Override
    @Transactional
    @CacheEvict(value = "userWishlist", key = "#root.target.userService.getCurrentLoggedInUser().id")
    public WishlistResponse addToWishlist(WishlistItemRequest request) {
        try {
            User currentUser = userService.getCurrentLoggedInUser();
            Long userId = currentUser.getId();
            log.info("Adding to wishlist for userId: {}, productId: {}", userId, request.getProductId());
            Product product = productRepository.findById(request.getProductId())
                    .orElseThrow(() -> new NotFoundException("Không tìm thấy sản phẩm: " + request.getProductId()));

            Wishlist wishlist = createOrGetWishlistInternal(currentUser);  // Internal call

            // Check duplicate (giả sử repo có method findByWishlistIdAndProductIdAndSize)
            Optional<WishlistItem> existing = wishlistItemRepository.findByWishlistIdAndProductId(
                    wishlist.getId(), product.getId());
            if (existing.isPresent()) {
                throw new IllegalStateException("Sản phẩm đã có trong wishlist");
            }

            WishlistItem item = WishlistItem.builder()
                    .wishlist(wishlist)
                    .product(product)
                    .size(request.getSize())
                    .build();
            wishlistItemRepository.save(item);
            wishlist.addWishlistItem(item);
            wishlistRepository.save(wishlist);
            log.info("Item added to wishlist successfully");
            return WishlistResponse.fromEntity(wishlist);
        } catch (Exception e) {
            log.error("Add to wishlist failed: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Override
    @Transactional
    @CacheEvict(value = "userWishlist", key = "#root.target.userService.getCurrentLoggedInUser().id")
    public WishlistResponse removeFromWishlist(Long productId) {
        try {
            User currentUser = userService.getCurrentLoggedInUser();
            Long userId = currentUser.getId();
            log.info("Removing from wishlist for userId: {}, productId: {}", userId, productId);
            Wishlist wishlist = wishlistRepository.findByUserId(userId)
                    .orElseThrow(() -> new NotFoundException("Không tìm thấy wishlist cho người dùng hiện tại"));

            boolean removed = wishlist.getWishlistItems().removeIf(item ->
                    item.getProduct().getId().equals(productId) );
            if (!removed) {
                throw new NotFoundException("Không tìm thấy item trong wishlist");
            }
            wishlistRepository.save(wishlist);
            log.info("Item removed from wishlist successfully");
            return WishlistResponse.fromEntity(wishlist);
        } catch (Exception e) {
            log.error("Remove from wishlist failed: {}", e.getMessage(), e);
            throw e;
        }
    }


    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "userWishlist", key = "#root.target.userService.getCurrentLoggedInUser().id")
    public WishlistResponse getWishlistByCurrentUser() {
        try {
            User currentUser = userService.getCurrentLoggedInUser();
            Long userId = currentUser.getId();
            log.info("Fetching wishlist for userId: {}", userId);
            Wishlist wishlist = wishlistRepository.findByUserId(userId)
                    .orElseThrow(() -> new NotFoundException("Không tìm thấy wishlist cho người dùng hiện tại"));
            return WishlistResponse.fromEntity(wishlist);
        } catch (Exception e) {
            log.error("Get wishlist failed: {}", e.getMessage(), e);
            throw e;
        }
    }

    // Private helper to avoid duplication
    private Wishlist createOrGetWishlistInternal(User currentUser) {
        Long userId = currentUser.getId();
        return wishlistRepository.findByUserId(userId)
                .orElseGet(() -> {
                    Wishlist newWishlist = Wishlist.builder().user(currentUser).build();
                    return wishlistRepository.save(newWishlist);
                });
    }
}