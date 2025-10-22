package com.sprotshop.sportstore.config;

import com.sprotshop.sportstore.security.CustomUserDetailService;
import com.sprotshop.sportstore.security.JwtUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder; // 👈 THÊM NÀY
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class StompAuthInterceptor implements ChannelInterceptor {

    private final JwtUtils jwtUtils;
    private final CustomUserDetailService userDetailsService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) return message;

        Map<String, Object> sessionAttrs = accessor.getSessionAttributes();
        if (sessionAttrs == null) sessionAttrs = Map.of(); // Avoid null

        StompCommand command = accessor.getCommand();
        if (StompCommand.CONNECT.equals(command)) {
            // 👈 FIX: Auth from header in CONNECT, store in session
            List<String> authHeaders = accessor.getNativeHeader("Authorization");
            if (authHeaders != null && !authHeaders.isEmpty()) {
                String token = authHeaders.get(0).replace("Bearer ", "");
                try {
                    String email = jwtUtils.getUsernameFromToken(token);
                    log.info("STOMP CONNECT auth for email: {}", email);
                    if (jwtUtils.isTokenValid(token, null)) {
                        UserDetails userDetails = userDetailsService.loadUserByUsername(email);
                        if (userDetails != null) {
                            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                                    userDetails, null, userDetails.getAuthorities());
                            SecurityContextHolder.getContext().setAuthentication(auth);
                            accessor.setUser(auth);
                            // 👈 FIX: Store userDetails in session for later commands (SEND)
                            sessionAttrs.put("userDetails", userDetails);
                            log.info("STOMP CONNECT auth success, stored in session for email: {}", email);
                            return MessageBuilder.createMessage(message.getPayload(), accessor.getMessageHeaders());
                        } else {
                            log.error("STOMP CONNECT userDetails null for email: {}", email);
                        }
                    } else {
                        log.error("STOMP CONNECT token invalid for email: {}", email);
                    }
                } catch (Exception e) {
                    log.error("STOMP CONNECT auth exception: {}", e.getMessage(), e);
                }
            }
        } else if (StompCommand.SEND.equals(command) || StompCommand.SUBSCRIBE.equals(command)) {
            // 👈 FIX: Retrieve from session for SEND/SUBSCRIBE (no header)
            UserDetails sessionUser = (UserDetails) sessionAttrs.get("userDetails");
            if (sessionUser != null) {
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                        sessionUser, null, sessionUser.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(auth);
                accessor.setUser(auth);
                log.info("STOMP {} auth from session for email: {}", command, sessionUser.getUsername());
                return MessageBuilder.createMessage(message.getPayload(), accessor.getMessageHeaders());
            } else {
                log.error("STOMP {} no session userDetails", command);
            }
        } else if (StompCommand.DISCONNECT.equals(command)) {
            SecurityContextHolder.clearContext();
            sessionAttrs.clear(); // Clear session
            log.info("STOMP DISCONNECT - cleared session");
        }
        return message;
    }
}