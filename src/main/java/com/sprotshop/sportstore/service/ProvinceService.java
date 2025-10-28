package com.sprotshop.sportstore.service;

import com.sprotshop.sportstore.dto.api.ApiProvince;
import com.sprotshop.sportstore.dto.api.ApiWard;
import com.sprotshop.sportstore.entity.Province;
import com.sprotshop.sportstore.entity.Ward;
import com.sprotshop.sportstore.repository.ProvinceRepository;
import com.sprotshop.sportstore.repository.WardRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;  // 👈 REMOVED: Bỏ @PostConstruct để tránh lazy init lúc startup
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class ProvinceService {

    private static final Logger log = LoggerFactory.getLogger(ProvinceService.class);
    private final RestTemplate restTemplate;
    private final ProvinceRepository provinceRepository;
    private final WardRepository wardRepository;
    private final String BASE_URL = "https://provinces.open-api.vn/api/v2/";

    // 👈 REMOVED: @PostConstruct initData() - Ko auto-sync lúc startup (data đã load sẵn)
    // Nếu muốn optional check + sync: Uncomment và add @Transactional
    /*
    @PostConstruct
    @Transactional  // 👈 ADD: Wrap để có session
    public void initData() {
        if (provinceRepository.count() == 0) {  // 👈 ADD: Chỉ sync nếu DB empty
            syncAllData().join();  // Wait Future để complete
        }
    }
    */

    @Async
    @Transactional
    public CompletableFuture<Void> syncAllData() {
        log.info("Starting data synchronization from provinces API v2 (depth=2)");
        String url = BASE_URL + "?depth=2";
        ApiProvince[] apiProvinces = restTemplate.getForObject(url, ApiProvince[].class);

        if (apiProvinces == null || apiProvinces.length == 0) {
            log.warn("No province data received from API");
            return CompletableFuture.completedFuture(null);
        }

        List<Province> provincesToSave = new ArrayList<>();
        List<Ward> wardsToSave = new ArrayList<>();

        for (ApiProvince apiProvince : apiProvinces) {
            Province province = provinceRepository.findById(apiProvince.getCode()).orElse(null);
            if (province == null) {
                log.info("Inserting new province: {}", apiProvince.getName());
                province = Province.builder()
                        .code(apiProvince.getCode())
                        .name(apiProvince.getName())
                        .codename(apiProvince.getCodename())
                        .divisionType(apiProvince.getDivisionType())
                        .phoneCode(apiProvince.getPhoneCode())
                        .wards(new ArrayList<>())
                        .build();
                provincesToSave.add(province);
            } else if (!province.getName().equals(apiProvince.getName()) ||
                    !province.getCodename().equals(apiProvince.getCodename()) ||
                    !province.getDivisionType().equals(apiProvince.getDivisionType()) ||
                    province.getPhoneCode() != apiProvince.getPhoneCode()) {
                log.info("Updating province: {}", apiProvince.getName());
                province.setName(apiProvince.getName());
                province.setCodename(apiProvince.getCodename());
                province.setDivisionType(apiProvince.getDivisionType());
                province.setPhoneCode(apiProvince.getPhoneCode());
                provincesToSave.add(province);
            }

            if (apiProvince.getWards() != null) {
                // 👈 FIXED: Load wards collection explicitly trong transaction để avoid lazy init
                Hibernate.initialize(province.getWards());
                for (ApiWard apiWard : apiProvince.getWards()) {
                    Ward ward = wardRepository.findById(apiWard.getCode()).orElse(null);
                    if (ward == null) {
                        log.info("Inserting new ward: {} for province: {}", apiWard.getName(), apiProvince.getName());
                        ward = Ward.builder()
                                .code(apiWard.getCode())
                                .name(apiWard.getName())
                                .codename(apiWard.getCodename())
                                .divisionType(apiWard.getDivisionType())
                                .shortCodename(apiWard.getShortCodename())
                                .province(province)
                                .build();
                        wardsToSave.add(ward);
                        province.getWards().add(ward);  // Now safe, collection loaded
                    } else if (!ward.getName().equals(apiWard.getName()) ||
                            !ward.getCodename().equals(apiWard.getCodename()) ||
                            !ward.getDivisionType().equals(apiWard.getDivisionType()) ||
                            !Objects.equals(ward.getShortCodename(), apiWard.getShortCodename())) {
                        log.info("Updating ward: {} for province: {}", apiWard.getName(), apiProvince.getName());
                        ward.setName(apiWard.getName());
                        ward.setCodename(apiWard.getCodename());
                        ward.setDivisionType(apiWard.getDivisionType());
                        ward.setShortCodename(apiWard.getShortCodename());
                        wardsToSave.add(ward);
                    }
                }
            }
        }

        if (!provincesToSave.isEmpty()) provinceRepository.saveAll(provincesToSave);
        if (!wardsToSave.isEmpty()) wardRepository.saveAll(wardsToSave);

        log.info("Data synchronization completed: {} provinces, {} wards", apiProvinces.length, wardsToSave.size());
        return CompletableFuture.completedFuture(null);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "provinces")
    public List<Province> getAllProvinces() {
        log.info("Fetching all provinces from database");
        return provinceRepository.findAll();
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "wards", key = "#provinceCode")
    public List<Ward> getWardsByProvince(int provinceCode) {
        log.info("Fetching wards for province code: {}", provinceCode);
        return wardRepository.findByProvinceCode(provinceCode);
    }
}