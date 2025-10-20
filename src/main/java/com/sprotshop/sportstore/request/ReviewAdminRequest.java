package com.sprotshop.sportstore.request;

import com.sprotshop.sportstore.Enum.ReviewStatus;
import lombok.Data;

@Data
public class ReviewAdminRequest {
    private ReviewStatus status;
    private String reply;
}