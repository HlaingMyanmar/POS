package org.sspd.servicemgmt.journaloption.entry.model;

import jakarta.persistence.*;
import lombok.*;
import org.sspd.servicemgmt.journaloption.detail.model.JournalDetail;
import org.sspd.servicemgmt.staffoptions.model.Staff;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "journal_entries", indexes = {
    @Index(name = "idx_je_entry_date",   columnList = "entryDate"),
    @Index(name = "idx_je_reference_no", columnList = "referenceNo"),
    @Index(name = "idx_je_staff",        columnList = "staff_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JournalEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private LocalDateTime entryDate = LocalDateTime.now();

    private String referenceNo; // ဥပမာ - Purchase Code သို့မဟုတ် Invoice No

    @Column(columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id")
    private Staff staff;

    @OneToMany(mappedBy = "journalEntry", cascade = CascadeType.ALL)
    private List<JournalDetail> details = new ArrayList<>();
}
