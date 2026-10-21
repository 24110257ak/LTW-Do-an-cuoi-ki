package vn.iotstar.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.iotstar.entity.Banner;
import vn.iotstar.repository.BannerRepository;
import vn.iotstar.service.BannerService;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BannerServiceImpl implements BannerService {

    private final BannerRepository bannerRepository;

    @Override
    public List<Banner> getActiveBanners() {
        return bannerRepository.findAllByIsActiveTrueOrderByDisplayOrderAsc();
    }
}
