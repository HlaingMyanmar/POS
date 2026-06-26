package org.sspd.servicemgmt.printingoptions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.sspd.servicemgmt.printingoptions.dto.PrintRequest.DocumentType;
import org.sspd.servicemgmt.printingoptions.entity.VoucherSetting;

import java.util.Optional;

public interface VoucherSettingRepository extends JpaRepository<VoucherSetting, Long> {

    Optional<VoucherSetting> findByDocumentType(DocumentType documentType);

    boolean existsByDocumentType(DocumentType documentType);

    void deleteByDocumentType(DocumentType documentType);
}
