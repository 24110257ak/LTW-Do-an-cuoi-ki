package vn.iotstar.service;

import vn.iotstar.entity.Brand;

import java.util.List;
import java.util.Optional;

public interface BrandService {
    List<Brand> getAllActiveBrands();
    List<Brand> getAllBrands();
    Optional<Brand> getBrandById(Integer id);
    Brand saveBrand(Brand brand);
    void toggleBrandStatus(Integer id);
}
