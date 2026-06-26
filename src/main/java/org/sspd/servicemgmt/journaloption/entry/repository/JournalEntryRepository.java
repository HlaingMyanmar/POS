package org.sspd.servicemgmt.journaloption.entry.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.sspd.servicemgmt.journaloption.entry.model.JournalEntry;


import java.util.Optional;
import java.util.List;
import java.time.LocalDateTime;

@Repository
public interface JournalEntryRepository extends JpaRepository<JournalEntry, Integer> {

    // Reference No (ဥပမာ - Purchase Code) နဲ့ Journal ကို ပြန်ရှာဖို့
    Optional<JournalEntry> findByReferenceNo(String referenceNo);

    // နေ့စွဲအလိုက် Journal များကို ရှာဖွေရန်
    List<JournalEntry> findByEntryDateBetween(LocalDateTime start, LocalDateTime end);
}