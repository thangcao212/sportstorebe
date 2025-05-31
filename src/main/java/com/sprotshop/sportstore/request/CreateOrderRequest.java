
package com.sprotshop.sportstore.request;

import com.sprotshop.sportstore.Enum.PaymentMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderRequest {
    @NotBlank(message = "Recipient name is required")
    private String shippingRecipientName;

    @NotBlank(message = "Phone number is required")
    private String shippingPhone;

    @NotBlank(message = "Street address is required")
    private String shippingStreet;

    @NotNull(message = "Province code is required")
    private Integer provinceCode;

    @NotNull(message = "District code is required")
    private Integer districtCode;

    @NotNull(message = "Ward code is required")
    private Integer wardCode;

    private String notes;

    @NotNull(message = "Payment method is required")
    private PaymentMethod paymentMethod;
}
