package com.sprotshop.sportstore.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WardDTO {
    private String name; // Tên phường/xã, ví dụ: "Phường Láng Thượng"
    private Integer code; // Mã phường/xã
    private String codename; // Tên chuẩn hóa
    private String divisionType; // Loại: "phường", "xã"
    private Integer districtCode; // Mã quận/huyện liên kết
}