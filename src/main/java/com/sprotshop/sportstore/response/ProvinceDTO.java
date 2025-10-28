
package com.sprotshop.sportstore.response;

import lombok.*;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProvinceDTO {
    private String name;
    private Integer code;
    private String divisionType;
    private String codename;
    private Integer phoneCode;
//    private List<DistrictDTO> districts;

    private List<WardDTO> wards;
}
