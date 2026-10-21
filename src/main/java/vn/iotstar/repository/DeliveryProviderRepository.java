package vn.iotstar.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.iotstar.entity.DeliveryProvider;

import java.util.List;

@Repository
public interface DeliveryProviderRepository extends JpaRepository<DeliveryProvider, Integer> {
    List<DeliveryProvider> findAllByIsActiveTrue();
}
