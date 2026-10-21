package vn.iotstar.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.iotstar.entity.DeliveryProvider;
import vn.iotstar.exception.ResourceNotFoundException;
import vn.iotstar.repository.DeliveryProviderRepository;
import vn.iotstar.service.DeliveryProviderService;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DeliveryProviderServiceImpl implements DeliveryProviderService {

    private final DeliveryProviderRepository providerRepository;

    @Override
    public List<DeliveryProvider> getAllActiveProviders() {
        return providerRepository.findAllByIsActiveTrue();
    }

    @Override
    public List<DeliveryProvider> getAllProviders() {
        return providerRepository.findAll();
    }

    @Override
    public Optional<DeliveryProvider> getProviderById(Integer id) {
        return providerRepository.findById(id);
    }

    @Override
    @Transactional
    public DeliveryProvider saveProvider(DeliveryProvider provider) {
        return providerRepository.save(provider);
    }

    @Override
    @Transactional
    public void toggleProviderStatus(Integer id) {
        DeliveryProvider provider = providerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Đơn vị vận chuyển", "id", id));
        provider.setIsActive(provider.getIsActive() == null || !provider.getIsActive());
        providerRepository.save(provider);
    }
}
