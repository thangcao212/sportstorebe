
        package com.sprotshop.sportstore.controller;

import com.sprotshop.sportstore.response.ApiResponse;
import com.sprotshop.sportstore.response.DistrictDTO;
import com.sprotshop.sportstore.response.ProvinceDTO;
import com.sprotshop.sportstore.response.WardDTO;
import com.sprotshop.sportstore.service.LocationService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/locations")
@RequiredArgsConstructor
public class LocationController {

    private static final Logger log = LoggerFactory.getLogger(LocationController.class);
    private final LocationService locationService;

    @GetMapping("/provinces")
    public ResponseEntity<ApiResponse<List<ProvinceDTO>>> getAllProvinces() {
        List<ProvinceDTO> provinces = locationService.getAllProvinces();
        return ResponseEntity.ok(ApiResponse.<List<ProvinceDTO>>builder()
                .message("Provinces fetched successfully")
                .data(provinces)
                .status(HttpStatus.OK.value())
                .build());
    }

    @GetMapping("/districts/{provinceCode}")
    public ResponseEntity<ApiResponse<List<DistrictDTO>>> getDistrictsByProvince(@PathVariable Integer provinceCode) {
        List<DistrictDTO> districts = locationService.getDistrictsByProvinceCode(provinceCode);
        return ResponseEntity.ok(ApiResponse.<List<DistrictDTO>>builder()
                .message("Districts fetched successfully")
                .data(districts)
                .status(HttpStatus.OK.value())
                .build());
    }

    @GetMapping("/wards/{districtCode}")
    public ResponseEntity<ApiResponse<List<WardDTO>>> getWardsByDistrict(@PathVariable Integer districtCode) {
        List<WardDTO> wards = locationService.getWardsByDistrictCode(districtCode);
        return ResponseEntity.ok(ApiResponse.<List<WardDTO>>builder()
                .message("Wards fetched successfully")
                .data(wards)
                .status(HttpStatus.OK.value())
                .build());
    }
}
