package org.sspd.servicemgmt.customerportaloptions.dto;
import java.util.List;
public record CustomerCatalogPageDTO(List<CustomerCatalogProductDTO> content, int page, int size,
                                     long totalElements, boolean hasNext) {}
