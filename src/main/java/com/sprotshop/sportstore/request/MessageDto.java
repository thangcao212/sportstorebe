package com.sprotshop.sportstore.request;

import lombok.Data;

@Data
public class MessageDto {
    private Long receiverId; // Optional: null để auto query admin
    private String content;
}