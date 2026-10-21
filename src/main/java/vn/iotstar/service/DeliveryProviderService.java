package vn.iotstar.service;

import vn.iotstar.entity.DeliveryProvider;

import java.util.List;
import java.util.Optional;

public interface DeliveryProviderService {
    List<DeliveryProvider> getAllActiveProviders();
    List<DeliveryProvider> getAllProviders();
    Optional<DeliveryProvider> getProviderById(Integer id);
    DeliveryProvider saveProvider(DeliveryProvider provider);
    void toggleProviderStatus(Integer id);
}
