package com.sprotshop.sportstore.dto.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class ApiProvince {
    private int code;
    private String name;
    private String codename;
    private String divisionType;
    @JsonProperty("phone_code")
    private int phoneCode;
    private List<ApiWard> wards;  // Direct từ depth=2
}