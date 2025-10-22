package com.sprotshop.sportstore.service;

import com.sprotshop.sportstore.entity.Conversation;
import com.sprotshop.sportstore.entity.Message;
import com.sprotshop.sportstore.entity.User;

import java.util.List;

public interface MessagingService {
    List<Conversation> getConversationsOfUser(User user);
    Conversation getConversation(User user, Long conversationId);
    Conversation createConversationAndAddMessage(User sender, Long receiverId, String content);
    Message addMessageToConversation(Long conversationId, User sender, Long receiverId, String content);
    void markMessageAsRead(User user, Long messageId);
}