package com.sprotshop.sportstore.service;

import com.sprotshop.sportstore.request.WishlistItemRequest;
import com.sprotshop.sportstore.response.WishlistResponse;

public interface WishlistService {

    WishlistResponse createOrGetWishlist();
    WishlistResponse addToWishlist( WishlistItemRequest request);
    public WishlistResponse removeFromWishlist( Long productId) ;
    WishlistResponse getWishlistByCurrentUser();
}
