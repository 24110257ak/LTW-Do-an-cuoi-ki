package vn.iotstar.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.iotstar.entity.Brand;
import vn.iotstar.exception.ResourceNotFoundException;
import vn.iotstar.repository.BrandRepository;
import vn.iotstar.service.BrandService;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BrandServiceImpl implements BrandService {

    private final BrandRepository brandRepository;

    @Override
    public List<Brand> getAllActiveBrands() {
        return brandRepository.findAllByIsActiveTrue();
    }

    @Override
    public List<Brand> getAllBrands() {
        return brandRepository.findAll();
    }

    @Override
    public Optional<Brand> getBrandById(Integer id) {
        return brandRepository.findById(id);
    }

    @Override
    @Transactional
    public Brand saveBrand(Brand brand) {
        return brandRepository.save(brand);
    }

    @Override
    @Transactional
    public void toggleBrandStatus(Integer id) {
        Brand brand = brandRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Thương hiệu", "id", id));
        brand.setIsActive(brand.getIsActive() == null || !brand.getIsActive());
        brandRepository.save(brand);
    }
}
