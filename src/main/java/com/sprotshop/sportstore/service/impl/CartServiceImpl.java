package com.sprotshop.sportstore.service.impl;

import com.sprotshop.sportstore.entity.*;
import com.sprotshop.sportstore.exception.NotFoundException;
import com.sprotshop.sportstore.repository.CartItemRepository;
import com.sprotshop.sportstore.repository.CartRepository;
import com.sprotshop.sportstore.repository.ProductRepository;
import com.sprotshop.sportstore.repository.UserRepository;
import com.sprotshop.sportstore.request.AddToCartRequest;
import com.sprotshop.sportstore.request.UpdateCartItemRequest;
import com.sprotshop.sportstore.response.CartItemResponse;
import com.sprotshop.sportstore.response.CartResponse;
import com.sprotshop.sportstore.service.CartService;
import com.sprotshop.sportstore.service.UserService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.*;

@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private static final Logger log = LoggerFactory.getLogger(CartServiceImpl.class);

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final UserService userService;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    public CartResponse getCart() {
        User currentUser = userService.getCurrentLoggedInUser();
        Cart cart = findOrCreateCartByUser(currentUser);
        return mapCartToResponse(cart);
    }

    @Override
    @Transactional
    public CartResponse addItemToCart(AddToCartRequest request) {
        User user = userService.getCurrentLoggedInUser();
        Cart cart = findOrCreateCartByUser(user);

        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy sản phẩm với ID: " + request.getProductId()));

        if (product.getStockQuantity() < request.getQuantity()) {
            throw new IllegalArgumentException("Số lượng tồn kho không đủ cho sản phẩm: " + product.getName());
        }

        Optional<CartItem> existingItemOpt = cartItemRepository.findByCartAndProduct(cart, product);

        if (existingItemOpt.isPresent()) {
            CartItem existingItem = existingItemOpt.get();
            int newQuantity = existingItem.getQuantity() + request.getQuantity();
            if (product.getStockQuantity() < newQuantity) {
                throw new IllegalArgumentException("Số lượng tồn kho không đủ để thêm vào giỏ hàng.");
            }
            existingItem.setQuantity(newQuantity);
            cartItemRepository.save(existingItem);
        } else {
            CartItem newItem = CartItem.builder()
                    .cart(cart)
                    .product(product)
                    .quantity(request.getQuantity())
                    .build();
            cartItemRepository.save(newItem);
        }

        Cart updatedCart = cartRepository.fetchCartWithItemsByUserId(user.getId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy giỏ hàng."));
        return mapCartToResponse(updatedCart);
    }

    @Override
    @Transactional
    public CartResponse updateCartItemQuantity(Long cartItemId, UpdateCartItemRequest request) {
        User user = userService.getCurrentLoggedInUser();
        CartItem cartItem = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy món hàng với ID: " + cartItemId));

        if (!cartItem.getCart().getUser().getId().equals(user.getId())) {
            throw new SecurityException("Không có quyền cập nhật món hàng này.");
        }

        Product product = cartItem.getProduct();
        if (product.getStockQuantity() < request.getQuantity()) {
            throw new IllegalArgumentException("Số lượng tồn kho không đủ cho sản phẩm: " + product.getName());
        }

        cartItem.setQuantity(request.getQuantity());
        cartItemRepository.save(cartItem);

        Cart updatedCart = cartRepository.fetchCartWithItemsByUserId(user.getId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy giỏ hàng."));
        return mapCartToResponse(updatedCart);
    }

    @Override
    @Transactional
    public CartResponse removeItemFromCartByProductId(Long productId) { // << Sửa tên và tham số
        User currentUser = userService.getCurrentLoggedInUser();
        Long userId = currentUser.getId();
        log.warn("Attempting to remove product ID: {} from cart for userId: {}", productId, userId);

        // 1. Tìm giỏ hàng của user (cần load sẵn cartItems và product trong đó)
        // Dùng findCartByUserWithItems để đảm bảo đã load bằng @EntityGraph
        Cart cart = findCartByUserWithItems(userId);

        // 2. Tìm CartItem tương ứng với productId trong danh sách items của Cart đã load
        CartItem itemToRemove = cart.getCartItems().stream()
                .filter(item -> item.getProduct() != null && item.getProduct().getId().equals(productId))
                .findFirst()
                .orElseThrow(() -> {
                    // Ghi log rõ hơn
                    log.warn("Product ID: {} not found in cart ID: {} for user ID: {}", productId, cart.getId(), userId);
                    // Ném lỗi để báo cho client
                    return new NotFoundException("Sản phẩm với ID: " + productId + " không tìm thấy trong giỏ hàng.");
                });

        log.debug("Found CartItem ID: {} corresponding to Product ID: {} for removal.", itemToRemove.getId(), productId);

        // 3. Sử dụng helper method trong Cart entity để xóa item khỏi collection
        //    và xóa liên kết 2 chiều (item.setCart(null))
        cart.removeCartItem(itemToRemove);
        log.debug("Removed CartItem from Cart's collection in memory (cartId={}, itemId={}).", cart.getId(), itemToRemove.getId());

        // 4. Lưu lại entity Cart cha.
        //    Do cấu hình orphanRemoval=true trên cart.cartItems,
        //    JPA/Hibernate sẽ tự động phát hiện CartItem đã bị xóa khỏi collection
        //    và tạo lệnh SQL DELETE tương ứng cho CartItem đó khi Cart được lưu.
        cartRepository.save(cart);
        log.info("Saved Cart ID: {}. Expecting orphanRemoval to delete CartItem ID: {}", cart.getId(), itemToRemove.getId());

        // 5. (Vẫn khuyến nghị) Load lại trạng thái cuối cùng của cart từ DB để trả về response chính xác nhất
        Cart updatedCart = findCartByUserWithItems(userId);
        log.debug("Refetched cart state after removal for response.");
        return mapCartToResponse(updatedCart);

        /*
        // --- Cách thay thế (Xóa trực tiếp, không dựa vào orphanRemoval) ---
        // Bỏ bước 3 và 4 ở trên, thay bằng:
        log.debug("Attempting direct deletion of CartItem ID: {}", itemToRemove.getId());
        cartItemRepository.delete(itemToRemove);
        log.info("Deleted CartItem ID: {} directly via repository.", itemToRemove.getId());
        // Sau đó vẫn thực hiện bước 5 để trả về response
        Cart updatedCart = findCartByUserWithItems(userId);
        return mapCartToResponse(updatedCart);
        // --- Hết cách thay thế ---
        */
    }


    @Override
    @Transactional
    public CartResponse clearCart() {
        User user = userService.getCurrentLoggedInUser();
        Cart cart = cartRepository.fetchCartWithItemsByUserId(user.getId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy giỏ hàng."));

        if (!cart.getCartItems().isEmpty()) {
            cartItemRepository.deleteAllByCart(cart);
            cart.getCartItems().clear();
        }

        return mapCartToResponse(cart);
    }

    private Cart findOrCreateCartByUser(User user) {
        return cartRepository.findByUserId(user.getId()).orElseGet(() -> {
            Cart newCart = Cart.builder().user(user).cartItems(new HashSet<>()).build();
            return cartRepository.save(newCart);
        });
    }

    private CartResponse mapCartToResponse(Cart cart) {
        if (cart == null) return null;

        List<CartItemResponse> itemResponses = new ArrayList<>();
        double totalPrice = 0.0;
        int totalItems = 0;

        if (!CollectionUtils.isEmpty(cart.getCartItems())) {
            for (CartItem item : cart.getCartItems()) {
                Product product = item.getProduct();
                if (product != null) {
                    String imageUrl = (!CollectionUtils.isEmpty(product.getImages())) ?
                            product.getImages().get(0).getImageUrl() : null;
                    double itemPrice = Optional.ofNullable(product.getPrice()).orElse(0.0);
                    int quantity = Optional.ofNullable(item.getQuantity()).orElse(0);
                    double itemTotalPrice = itemPrice * quantity;
                    totalPrice += itemTotalPrice;
                    totalItems += quantity;
                    itemResponses.add(CartItemResponse.builder()
                            .cartItemId(item.getId())
                            .productId(product.getId())
                            .productName(product.getName())
                            .productImageUrl(imageUrl)
                            .quantity(quantity)
                            .price(itemPrice)
                            .itemTotalPrice(itemTotalPrice)
                            .build());
                }
            }
            itemResponses.sort(Comparator.comparing(CartItemResponse::getCartItemId));
        }

        return CartResponse.builder()
                .cartId(cart.getId())
                .userId(cart.getUser().getId())
                .items(itemResponses)
                .totalItems(totalItems)
                .totalPrice(totalPrice)
                .build();
    }

    private Cart findCartByUserWithItems(Long userId) {
        return cartRepository.fetchCartWithItemsByUserId(userId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy giỏ hàng cho user ID: " + userId));
    }
}
