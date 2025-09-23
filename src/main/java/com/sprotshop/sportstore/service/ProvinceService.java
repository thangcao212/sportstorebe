package com.sprotshop.sportstore.service;

import com.sprotshop.sportstore.entity.Province;
import com.sprotshop.sportstore.entity.District;
import com.sprotshop.sportstore.entity.Ward;
import com.sprotshop.sportstore.repository.ProvinceRepository;
import com.sprotshop.sportstore.repository.DistrictRepository;
import com.sprotshop.sportstore.repository.WardRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class ProvinceService {

    private static final Logger log = LoggerFactory.getLogger(ProvinceService.class);
    private final RestTemplate restTemplate;
    private final ProvinceRepository provinceRepository;
    private final DistrictRepository districtRepository;
    private final WardRepository wardRepository;
    private final String BASE_URL = "https://provinces.open-api.vn/api/";

    @Async
    @Transactional
    @Cacheable(value = "provinces", unless = "#result == null")
    public CompletableFuture<Void> syncAllData() {
        log.info("Starting data synchronization from provinces API");
        String url = BASE_URL + "?depth=3"; // Lấy tỉnh, quận, phường trong 1 request
        Province[] provinces = restTemplate.getForObject(url, Province[].class);

        if (provinces == null || provinces.length == 0) {
            log.warn("No province data received from API");
            return CompletableFuture.completedFuture(null);
        }

        List<Province> provincesToSave = new ArrayList<>();
        List<District> districtsToSave = new ArrayList<>();
        List<Ward> wardsToSave = new ArrayList<>();

        for (Province province : provinces) {
            Province existingProvince = provinceRepository.findById(province.getCode()).orElse(null);
            if (existingProvince == null) {
                log.info("Inserting new province: {}", province.getName());
                provincesToSave.add(province);
            } else if (!existingProvince.getName().equals(province.getName()) ||
                    !existingProvince.getCodename().equals(province.getCodename()) ||
                    !existingProvince.getDivisionType().equals(province.getDivisionType()) ||
                    existingProvince.getPhoneCode() != province.getPhoneCode()) {
                log.info("Updating province: {}", province.getName());
                existingProvince.setName(province.getName());
                existingProvince.setCodename(province.getCodename());
                existingProvince.setDivisionType(province.getDivisionType());
                existingProvince.setPhoneCode(province.getPhoneCode());
                provincesToSave.add(existingProvince);
            }

            if (province.getDistricts() != null) {
                for (District district : province.getDistricts()) {
                    district.setProvince(province);
                    District existingDistrict = districtRepository.findById(district.getCode()).orElse(null);
                    if (existingDistrict == null) {
                        log.info("Inserting new district: {} for province: {}", district.getName(), province.getName());
                        districtsToSave.add(district);
                    } else if (!existingDistrict.getName().equals(district.getName()) ||
                            !existingDistrict.getCodename().equals(district.getCodename()) ||
                            !existingDistrict.getDivisionType().equals(district.getDivisionType())) {
                        log.info("Updating district: {} for province: {}", district.getName(), province.getName());
                        existingDistrict.setName(district.getName());
                        existingDistrict.setCodename(district.getCodename());
                        existingDistrict.setDivisionType(district.getDivisionType());
                        districtsToSave.add(existingDistrict);
                    }

                    if (district.getWards() != null) {
                        for (Ward ward : district.getWards()) {
                            ward.setDistrict(district);
                            Ward existingWard = wardRepository.findById(ward.getCode()).orElse(null);
                            if (existingWard == null) {
                                log.info("Inserting new ward: {} for district: {}", ward.getName(), district.getName());
                                wardsToSave.add(ward);
                            } else if (!existingWard.getName().equals(ward.getName()) ||
                                    !existingWard.getCodename().equals(ward.getCodename()) ||
                                    !existingWard.getDivisionType().equals(ward.getDivisionType())) {
                                log.info("Updating ward: {} for district: {}", ward.getName(), district.getName());
                                existingWard.setName(ward.getName());
                                existingWard.setCodename(ward.getCodename());
                                existingWard.setDivisionType(ward.getDivisionType());
                                wardsToSave.add(existingWard);
                            }
                        }
                    }
                }
            }
        }

        // Batch save
        provinceRepository.saveAll(provincesToSave);
        districtRepository.saveAll(districtsToSave);
        wardRepository.saveAll(wardsToSave);

        log.info("Data synchronization completed");
        return CompletableFuture.completedFuture(null);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "provinces")
    public List<Province> getAllProvinces() {
        log.info("Fetching all provinces from database");
        return provinceRepository.findAll();
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "districts", key = "#provinceCode")
    public List<District> getDistrictsByProvince(int provinceCode) {
        log.info("Fetching districts for province code: {}", provinceCode);
        return districtRepository.findByProvinceCode(provinceCode);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "wards", key = "#districtCode")
    public List<Ward> getWardsByDistrict(int districtCode) {
        log.info("Fetching wards for district code: {}", districtCode);
        return wardRepository.findByDistrictCode(districtCode);
    }

}