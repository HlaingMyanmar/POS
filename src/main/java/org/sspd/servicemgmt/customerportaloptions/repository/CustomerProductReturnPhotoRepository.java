package org.sspd.servicemgmt.customerportaloptions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.sspd.servicemgmt.customerportaloptions.model.CustomerProductReturnPhoto;

import java.util.List;

public interface CustomerProductReturnPhotoRepository extends JpaRepository<CustomerProductReturnPhoto, Integer> {
    List<CustomerProductReturnPhoto> findByProductReturn_IdOrderByIdAsc(Integer returnId);
}
