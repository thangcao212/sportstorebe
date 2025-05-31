package com.sprotshop.sportstore.service;

import com.sprotshop.sportstore.response.DistrictDTO;
import com.sprotshop.sportstore.response.ProvinceDTO;
import com.sprotshop.sportstore.response.WardDTO;

import java.util.List;

public interface LocationService {
    List<ProvinceDTO> getAllProvinces();
    List<DistrictDTO> getDistrictsByProvinceCode(Integer provinceCode);
    List<WardDTO> getWardsByDistrictCode(Integer districtCode);

    ProvinceDTO getProvinceByCode(Integer code);
    DistrictDTO getDistrictByCode(Integer code);
    WardDTO getWardByCode(Integer code);

}