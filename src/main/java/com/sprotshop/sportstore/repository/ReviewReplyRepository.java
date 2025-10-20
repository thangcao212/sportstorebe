package com.sprotshop.sportstore.repository;

import com.sprotshop.sportstore.entity.ReviewReply;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReviewReplyRepository extends JpaRepository<ReviewReply, Long> {
    List<ReviewReply> findByReviewId(Long reviewId);
}