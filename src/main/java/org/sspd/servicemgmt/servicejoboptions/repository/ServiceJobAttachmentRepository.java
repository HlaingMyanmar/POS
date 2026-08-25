package org.sspd.servicemgmt.servicejoboptions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.sspd.servicemgmt.servicejoboptions.model.ServiceJobAttachment;

import java.util.List;

public interface ServiceJobAttachmentRepository extends JpaRepository<ServiceJobAttachment, Integer> {
    List<ServiceJobAttachment> findByServiceJobIdOrderByUploadedAtDesc(Integer serviceJobId);
}
