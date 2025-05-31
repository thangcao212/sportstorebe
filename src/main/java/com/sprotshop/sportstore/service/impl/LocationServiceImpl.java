
package com.sprotshop.sportstore.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sprotshop.sportstore.response.DistrictDTO;
import com.sprotshop.sportstore.response.ProvinceDTO;
import com.sprotshop.sportstore.response.WardDTO;
import com.sprotshop.sportstore.service.LocationService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LocationServiceImpl implements LocationService {

    private static final Logger log = LoggerFactory.getLogger(LocationServiceImpl.class);
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private static final String BASE_URL = "https://provinces.open-api.vn/api/";

    @Override
    @Cacheable(value = "provinces")
    public List<ProvinceDTO> getAllProvinces() {
        try {
            String url = BASE_URL + "p/?depth=1";
            String response = restTemplate.getForObject(url, String.class);
            return objectMapper.readValue(response, new TypeReference<List<ProvinceDTO>>() {});
        } catch (Exception e) {
            log.error("Error fetching provinces: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    @Cacheable(value = "districts", key = "#provinceCode")
    public List<DistrictDTO> getDistrictsByProvinceCode(Integer provinceCode) {
        if (provinceCode == null) return Collections.emptyList();
        try {
            String url = BASE_URL + "p/" + provinceCode + "?depth=2";
            ProvinceDTO province = restTemplate.getForObject(url, ProvinceDTO.class);
            return province != null && province.getDistricts() != null ? province.getDistricts() : Collections.emptyList();
        } catch (Exception e) {
            log.error("Error fetching districts for provinceCode {}: {}", provinceCode, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    @Cacheable(value = "wards", key = "#districtCode")
    public List<WardDTO> getWardsByDistrictCode(Integer districtCode) {
        if (districtCode == null) return Collections.emptyList();
        try {
            String url = BASE_URL + "d/" + districtCode + "?depth=2";
            DistrictDTO district = restTemplate.getForObject(url, DistrictDTO.class);
            return district != null && district.getWards() != null ? district.getWards() : Collections.emptyList();
        } catch (Exception e) {
            log.error("Error fetching wards for districtCode {}: {}", districtCode, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public ProvinceDTO getProvinceByCode(Integer code) {
        try {
            String url = BASE_URL + "p/" + code;
            return restTemplate.getForObject(url, ProvinceDTO.class);
        } catch (Exception e) {
            log.error("Error fetching province by code {}: {}", code, e.getMessage());
            return null;
        }
    }

    @Override
    public DistrictDTO getDistrictByCode(Integer code) {
        try {
            String url = BASE_URL + "d/" + code;
            return restTemplate.getForObject(url, DistrictDTO.class);
        } catch (Exception e) {
            log.error("Error fetching district by code {}: {}", code, e.getMessage());
            return null;
        }
    }

    @Override
    public WardDTO getWardByCode(Integer code) {
        try {
            String url = BASE_URL + "w/" + code;
            return restTemplate.getForObject(url, WardDTO.class);
        } catch (Exception e) {
            log.error("Error fetching ward by code {}: {}", code, e.getMessage());
            return null;
        }
    }

}
