package org.sspd.servicemgmt.api;

import lombok.Getter;
import org.springframework.data.domain.Page;
import java.util.List;

@Getter
public class PagedResponse<T> {
    private final List<T> content;
    private final int currentPage;
    private final int totalPages;
    private final long totalElements;

    public PagedResponse(Page<T> page) {
        this.content       = page.getContent();
        this.currentPage   = page.getNumber();
        this.totalPages    = page.getTotalPages();
        this.totalElements = page.getTotalElements();
    }
}
