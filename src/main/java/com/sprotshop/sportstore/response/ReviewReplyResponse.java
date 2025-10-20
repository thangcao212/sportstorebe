// File: com/sprotshop/sportstore/response/ReviewReplyResponse.java
package com.sprotshop.sportstore.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.sprotshop.sportstore.entity.ReviewReply;
import lombok.*;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor  // FIXED: Thêm NoArgsConstructor để Jackson deserialize được
@AllArgsConstructor
public class ReviewReplyResponse {
    private Long id;
    private String content;
    private Long userId;
    private String userName;  // e.g. admin username
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    // FIXED: Static method để map từ entity (gọi từ service)
    public static ReviewReplyResponse fromEntity(ReviewReply reply) {
        return ReviewReplyResponse.builder()
                .id(reply.getId())
                .content(reply.getContent())
                .userId(reply.getUser().getId())
                .userName(reply.getUser().getUsername())  // Giả sử User có getUsername()
                .createdAt(reply.getCreatedAt())
                .build();
    }
}