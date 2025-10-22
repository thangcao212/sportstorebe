package com.sprotshop.sportstore.controller;

import com.sprotshop.sportstore.request.MessageDto;
import com.sprotshop.sportstore.entity.Conversation;
import com.sprotshop.sportstore.entity.Message;
import com.sprotshop.sportstore.entity.User;
import com.sprotshop.sportstore.response.ApiResponse;
import com.sprotshop.sportstore.service.MessagingService;
import com.sprotshop.sportstore.service.impl.MessagingServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/messaging")
@RequiredArgsConstructor
public class MessagingController {

    private final MessagingService messagingService;

    private User getCurrentUser() {
        return ((MessagingServiceImpl) messagingService).getCurrentUser(); // Cast để dùng helper
    }

    @GetMapping("/conversations")
    public ResponseEntity<ApiResponse<List<Conversation>>> getConversations() {
        User user = getCurrentUser();
        List<Conversation> conversations = messagingService.getConversationsOfUser(user);
        return ResponseEntity.ok(ApiResponse.<List<Conversation>>builder()
                .message("Conversations fetched successfully")
                .data(conversations)
                .status(HttpStatus.OK.value())
                .build());
    }

    @GetMapping("/conversations/{conversationId}")
    public ResponseEntity<ApiResponse<Conversation>> getConversation(@PathVariable Long conversationId) {
        User user = getCurrentUser();
        Conversation conversation = messagingService.getConversation(user, conversationId);
        return ResponseEntity.ok(ApiResponse.<Conversation>builder()
                .message("Conversation fetched successfully")
                .data(conversation)
                .status(HttpStatus.OK.value())
                .build());
    }

    @PostMapping("/conversations")
    public ResponseEntity<ApiResponse<Conversation>> createConversationAndAddMessage(@RequestBody MessageDto messageDto) {
        User sender = getCurrentUser();
        Conversation conversation = messagingService.createConversationAndAddMessage(sender, messageDto.getReceiverId(), messageDto.getContent());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<Conversation>builder()
                .message("Conversation created successfully")
                .data(conversation)
                .status(HttpStatus.CREATED.value())
                .build());
    }

    @PostMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<ApiResponse<Message>> addMessageToConversation(@PathVariable Long conversationId, @RequestBody MessageDto messageDto) {
        User sender = getCurrentUser();
        Message message = messagingService.addMessageToConversation(conversationId, sender, messageDto.getReceiverId(), messageDto.getContent());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<Message>builder()
                .message("Message sent successfully")
                .data(message)
                .status(HttpStatus.CREATED.value())
                .build());
    }

    @PutMapping("/messages/{messageId}/read")
    public ResponseEntity<ApiResponse<String>> markMessageAsRead(@PathVariable Long messageId) {
        User user = getCurrentUser();
        messagingService.markMessageAsRead(user, messageId);
        return ResponseEntity.ok(ApiResponse.<String>builder()
                .message("Message marked as read")
                .status(HttpStatus.OK.value())
                .build());
    }
}