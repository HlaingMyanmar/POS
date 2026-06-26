package org.sspd.servicemgmt.stockoptions.productserialoptions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.sspd.servicemgmt.stockoptions.productserialoptions.model.ProductSerial;
import org.sspd.servicemgmt.stockoptions.productserialoptions.enums.SerialStatus;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductSerialRepository extends JpaRepository<ProductSerial,Integer> {


    Optional<ProductSerial> findBySerialNumber(String serialNumber);


    List<ProductSerial> findByProductIdAndStatus(Integer productId, SerialStatus status);


    Long countByProductIdAndStatus(Integer productId, SerialStatus status);


    List<ProductSerial> findByStatus(SerialStatus status);

    List<ProductSerial> findByProductId(Integer productId);

    boolean existsBySerialNumber(String serialNumber);

    List<ProductSerial> findBySerialNumberIn(java.util.Collection<String> serialNumbers);

    void deleteBySerialNumber(String serialNumber);

    long countByProductId(Integer productId);

}
