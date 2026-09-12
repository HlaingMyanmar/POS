package org.sspd.servicemgmt.stockoptions.productoptions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.sspd.servicemgmt.stockoptions.productoptions.model.ProductPhoto;

import java.util.List;

public interface ProductPhotoRepository extends JpaRepository<ProductPhoto, Integer> {
    List<ProductPhoto> findByProductIdOrderBySlotAsc(Integer productId);
}
