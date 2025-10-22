package com.sprotshop.sportstore.service.impl;

import com.sprotshop.sportstore.entity.*;
import com.sprotshop.sportstore.Enum.UserRole;
import com.sprotshop.sportstore.exception.DuplicateConversationException;
import com.sprotshop.sportstore.exception.NotFoundException;
import com.sprotshop.sportstore.repository.ConversationRepository;
import com.sprotshop.sportstore.repository.MessageRepository;
import com.sprotshop.sportstore.repository.UserRepository;
import com.sprotshop.sportstore.service.MessagingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessagingServiceImpl implements MessagingService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate; // NEW: Để push real-time

    @Override
    public List<Conversation> getConversationsOfUser(User user) {
        return conversationRepository.findByAuthorOrRecipient(user, user);
    }

    @Override
    public Conversation getConversation(User user, Long conversationId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new NotFoundException("Conversation not found"));
        if (!conversation.getAuthor().getId().equals(user.getId())
                && !conversation.getRecipient().getId().equals(user.getId())) {
            throw new NotFoundException("User not authorized to view conversation");
        }
        return conversation;
    }

    @Override
    @Transactional
    public Conversation createConversationAndAddMessage(User sender, Long receiverId, String content) {
        // Auto query admin nếu receiverId null và sender là CUSTOMER
        User receiver;
        if (receiverId == null) {
            if (sender.getRole() != UserRole.CUSTOMER) {
                throw new IllegalArgumentException("Only customers can auto-chat with admin");
            }
            receiver = userRepository.findFirstByRoleOrderByIdAsc(UserRole.ADMIN)
                    .orElseThrow(() -> new NotFoundException("No admin available"));
            log.info("Auto-assigned admin {} for customer {}", receiver.getId(), sender.getEmail());
        } else {
            receiver = userRepository.findById(receiverId)
                    .orElseThrow(() -> new NotFoundException("Receiver not found"));
        }

        // Ensure ADMIN-CUSTOMER pair
        if (!(sender.getRole() == UserRole.ADMIN && receiver.getRole() == UserRole.CUSTOMER) &&
                !(sender.getRole() == UserRole.CUSTOMER && receiver.getRole() == UserRole.ADMIN)) {
            throw new IllegalArgumentException("Chat only allowed between admin and customer");
        }

        // Check existing conversation (two-way)
        // Trong createConversationAndAddMessage method, thay throw IllegalArgumentException:
        Optional<Conversation> existing = conversationRepository.findByAuthorAndRecipient(sender, receiver);
        if (existing.isPresent()) {
            throw new DuplicateConversationException("Conversation already exists, use the conversation id to send messages.");
        }
        existing = conversationRepository.findByAuthorAndRecipient(receiver, sender);
        if (existing.isPresent()) {
            throw new DuplicateConversationException("Conversation already exists, use the conversation id to send messages.");
        }
        Conversation conversation = conversationRepository.save(Conversation.builder()
                .author(sender)
                .recipient(receiver)
                .build());

        Message message = Message.builder()
                .sender(sender)
                .receiver(receiver)
                .conversation(conversation)
                .content(content)
                .build();
        messageRepository.save(message);
        conversation.getMessages().add(message);

        // NEW: Push real-time
        pushConversationUpdate(conversation, sender.getId(), receiver.getId());
        pushMessageToConversation(conversation.getId(), message);

        log.info("Created new conversation {} between {} and {}", conversation.getId(), sender.getEmail(), receiver.getEmail());
        return conversation;
    }

    @Override
    @Transactional
    public Message addMessageToConversation(Long conversationId, User sender, Long receiverId, String content) {
        // Fallback: Lấy opposite user từ conversation nếu receiverId null
        User receiver;
        if (receiverId == null) {
            Conversation conv = conversationRepository.findById(conversationId)
                    .orElseThrow(() -> new NotFoundException("Conversation not found"));
            receiver = (conv.getAuthor().getId().equals(sender.getId())) ? conv.getRecipient() : conv.getAuthor();
        } else {
            receiver = userRepository.findById(receiverId)
                    .orElseThrow(() -> new NotFoundException("Receiver not found"));
        }

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new NotFoundException("Conversation not found"));

        if (!conversation.getAuthor().getId().equals(sender.getId())
                && !conversation.getRecipient().getId().equals(sender.getId())) {
            throw new NotFoundException("User not authorized to send message to this conversation");
        }

        if (!conversation.getAuthor().getId().equals(receiver.getId())
                && !conversation.getRecipient().getId().equals(receiver.getId())) {
            throw new NotFoundException("Receiver is not part of this conversation");
        }

        Message message = Message.builder()
                .sender(sender)
                .receiver(receiver)
                .conversation(conversation)
                .content(content)
                .build();
        messageRepository.save(message);
        conversation.getMessages().add(message);

        // NEW: Push real-time
        pushMessageToConversation(conversationId, message);

        log.info("Added message to conversation {} from {} to {}", conversationId, sender.getEmail(), receiver.getEmail());
        return message;
    }

    // Trong MessagingServiceImpl.java, cập nhật method markMessageAsRead:
    @Override
    @Transactional
    public void markMessageAsRead(User user, Long messageId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new NotFoundException("Message not found"));

        if (!message.getReceiver().getId().equals(user.getId())) {
            throw new NotFoundException("User not authorized to mark message as read");
        }

        if (message.getIsRead() == null || !message.getIsRead()) { // 👈 FIX: Handle null safely
            message.setIsRead(true);
            messageRepository.save(message);
            pushMessageToConversation(message.getConversation().getId(), message);
            log.info("Marked message {} as read for user {}", messageId, user.getEmail());
        }
    }

    // Helper: Push conversation update to users
    private void pushConversationUpdate(Conversation conversation, Long userId1, Long userId2) {
        messagingTemplate.convertAndSend("/topic/conversations/" + userId1, conversation);
        messagingTemplate.convertAndSend("/topic/conversations/" + userId2, conversation);
    }

    // Helper: Push message to conversation topic
    private void pushMessageToConversation(Long convId, Message message) {
        messagingTemplate.convertAndSend("/topic/conversations/" + convId, message);
    }

    // Helper: Get current user
    public User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }
}