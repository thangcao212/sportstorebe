package com.sprotshop.sportstore.controller;



import com.sprotshop.sportstore.entity.User;
import com.sprotshop.sportstore.request.MessageDto;
import com.sprotshop.sportstore.repository.UserRepository;
import com.sprotshop.sportstore.service.MessagingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;

@Controller
@RequiredArgsConstructor
@Slf4j
public class StompMessageController {

//    private final MessagingService messagingService;

//    @MessageMapping("/messages/{conversationId}") // Frontend send to /app/messages/{id}
//    public void sendMessage(@DestinationVariable Long conversationId, @Payload MessageDto messageDto, SimpMessageHeaderAccessor headerAccessor) {
//        // Extract sender từ JWT (cần config JWT cho WebSocket, hoặc dùng principal)
//        // Giả sử bạn có User từ header hoặc session
//        User sender = ((MessagingServiceImpl) messagingService).getCurrentUser(); // Reuse helper
//        messagingService.addMessageToConversation(conversationId, sender, messageDto.getReceiverId(), messageDto.getContent());
//        // Push tự động qua service
//    }

    private final MessagingService messagingService;
    private final UserRepository userRepository;

    @MessageMapping("/messages/{conversationId}")
    public void sendMessage(@DestinationVariable Long conversationId, @Payload MessageDto messageDto, SimpMessageHeaderAccessor headerAccessor) {
        log.info("WS send message to conv {} with payload: {}", conversationId, messageDto.getContent());
        try {
            // 👈 FIX: Retrieve from session attributes (set in interceptor)
            Map<String, Object> sessionAttrs = headerAccessor.getSessionAttributes();
            UserDetails sessionUser = (UserDetails) sessionAttrs.get("userDetails");
            if (sessionUser == null) {
                log.error("WS send fail: No session userDetails");
                throw new RuntimeException("User not authenticated in WS session");
            }
            String email = sessionUser.getUsername();
            log.info("WS send from session user: {}", email);

            User sender = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not found: " + email));
            messagingService.addMessageToConversation(conversationId, sender, messageDto.getReceiverId(), messageDto.getContent());
            log.info("WS message added to conv {} from {}", conversationId, sender.getEmail());
        } catch (Exception e) {
            log.error("WS send message fail for conv {}: {}", conversationId, e.getMessage(), e);
        }
    }
}