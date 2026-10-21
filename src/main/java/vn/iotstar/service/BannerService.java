package vn.iotstar.service;

import vn.iotstar.entity.Banner;

import java.util.List;
import java.util.Optional;

public interface BannerService {
    List<Banner> getActiveBanners();
    List<Banner> getAllBanners();
    Optional<Banner> getBannerById(Integer id);
    Banner saveBanner(Banner banner);
    void toggleBannerStatus(Integer id);
    void deleteBanner(Integer id);
}
