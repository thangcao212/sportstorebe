package com.sprotshop.sportstore.controller;

import com.sprotshop.sportstore.entity.Province;
import com.sprotshop.sportstore.entity.District;
import com.sprotshop.sportstore.entity.Ward;
import com.sprotshop.sportstore.response.ApiResponse;
import com.sprotshop.sportstore.service.ProvinceService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ProvinceController {

    private static final Logger log = LoggerFactory.getLogger(ProvinceController.class);
    private final ProvinceService provinceService;

    @GetMapping("/provinces")
    public ResponseEntity<ApiResponse<List<Province>>> getAllProvinces() {
        log.info("Fetching all provinces");
        List<Province> provinces = provinceService.getAllProvinces();
        return ResponseEntity.ok(ApiResponse.<List<Province>>builder()
                .message("Success")
                .data(provinces)
                .status(HttpStatus.OK.value())
                .build());
    }

    @GetMapping("/provinces/{provinceCode}/districts")
    public ResponseEntity<ApiResponse<List<District>>> getDistrictsByProvince(@PathVariable int provinceCode) {
        log.info("Fetching districts for province code: {}", provinceCode);
        List<District> districts = provinceService.getDistrictsByProvince(provinceCode);
        return ResponseEntity.ok(ApiResponse.<List<District>>builder()
                .message("Success")
                .data(districts)
                .status(HttpStatus.OK.value())
                .build());
    }

    @GetMapping("/districts/{districtCode}/wards")
    public ResponseEntity<ApiResponse<List<Ward>>> getWardsByDistrict(@PathVariable int districtCode) {
        log.info("Fetching wards for district code: {}", districtCode);
        List<Ward> wards = provinceService.getWardsByDistrict(districtCode);
        return ResponseEntity.ok(ApiResponse.<List<Ward>>builder()
                .message("Success")
                .data(wards)
                .status(HttpStatus.OK.value())
                .build());
    }
}