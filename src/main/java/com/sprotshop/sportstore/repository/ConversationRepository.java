package com.sprotshop.sportstore.repository;

import com.sprotshop.sportstore.entity.Conversation;
import com.sprotshop.sportstore.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    Optional<Conversation> findByAuthorAndRecipient(User author, User recipient);
    List<Conversation> findByAuthorOrRecipient(User userOne, User userTwo);
}