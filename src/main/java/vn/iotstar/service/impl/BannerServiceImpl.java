package vn.iotstar.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.iotstar.entity.Banner;
import vn.iotstar.exception.ResourceNotFoundException;
import vn.iotstar.repository.BannerRepository;
import vn.iotstar.service.BannerService;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BannerServiceImpl implements BannerService {

    private final BannerRepository bannerRepository;

    @Override
    public List<Banner> getActiveBanners() {
        return bannerRepository.findAllByIsActiveTrueOrderByDisplayOrderAsc();
    }

    @Override
    public List<Banner> getAllBanners() {
        return bannerRepository.findAll();
    }

    @Override
    public Optional<Banner> getBannerById(Integer id) {
        return bannerRepository.findById(id);
    }

    @Override
    @Transactional
    public Banner saveBanner(Banner banner) {
        return bannerRepository.save(banner);
    }

    @Override
    @Transactional
    public void toggleBannerStatus(Integer id) {
        Banner banner = bannerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Banner", "id", id));
        banner.setIsActive(banner.getIsActive() == null || !banner.getIsActive());
        bannerRepository.save(banner);
    }

    @Override
    @Transactional
    public void deleteBanner(Integer id) {
        bannerRepository.deleteById(id);
    }
}
