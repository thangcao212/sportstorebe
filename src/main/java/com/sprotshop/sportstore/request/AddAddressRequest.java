package com.sprotshop.sportstore.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AddAddressRequest {
    @NotBlank private String street;
    @NotNull private Integer provinceCode;
//    @NotNull private Integer districtCode;
    @NotNull private Integer wardCode;
    private String label;
    private Boolean isDefault = false;
}