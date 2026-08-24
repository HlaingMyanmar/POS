package org.sspd.servicemgmt.quotationoptions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.sspd.servicemgmt.quotationoptions.model.Quotation;

public interface QuotationRepository extends JpaRepository<Quotation, Integer> { }
