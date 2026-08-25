package org.sspd.servicemgmt.cashdraweroptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.cashdraweroptions.model.*;
import org.sspd.servicemgmt.cashdraweroptions.repository.*;
import org.sspd.servicemgmt.exceptionhandler.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CashDrawerService {
    private final CashDrawerSessionRepository sessionRepository;
    private final CashDrawerMovementRepository movementRepository;

    @Transactional
    public CashDrawerSession open(BigDecimal openingCash, String note) {
        String actor = actor();
        sessionRepository.findFirstByOpenedByAndStatusOrderByOpenedAtDesc(actor, "OPEN")
                .ifPresent(s -> { throw new IllegalStateException("User already has an open cash drawer"); });
        BigDecimal opening = nonNegative(openingCash, "Opening cash");
        return sessionRepository.save(CashDrawerSession.builder()
                .openedBy(actor).openedAt(LocalDateTime.now()).openingCash(opening)
                .status("OPEN").note(note).build());
    }

    @Transactional
    public CashDrawerSession cashIn(Integer id, BigDecimal amount, String reason) {
        return move(id, amount, reason, "IN");
    }

    @Transactional
    public CashDrawerSession cashOut(Integer id, BigDecimal amount, String reason) {
        return move(id, amount, reason, "OUT");
    }

    @Transactional
    public CashDrawerSession close(Integer id, BigDecimal countedCash, String note) {
        CashDrawerSession session = openSession(id);
        BigDecimal counted = nonNegative(countedCash, "Counted cash");
        BigDecimal expected = session.getOpeningCash().add(session.getCashSales())
                .subtract(session.getCashRefunds()).add(session.getCashIn()).subtract(session.getCashOut());
        session.setExpectedCash(expected);
        session.setCountedCash(counted);
        session.setDifferenceAmount(counted.subtract(expected));
        session.setClosedBy(actor());
        session.setClosedAt(LocalDateTime.now());
        session.setStatus("CLOSED");
        if (note != null && !note.isBlank()) session.setNote(note);
        return sessionRepository.save(session);
    }

    @Transactional
    public void recordCashSale(BigDecimal amount) {
        updateAutomaticTotal(amount, true);
    }

    @Transactional
    public void recordCashRefund(BigDecimal amount) {
        updateAutomaticTotal(amount, false);
    }

    @Transactional
    public void recordPurchaseCashOut(BigDecimal amount, String reason) {
        recordAutomaticMovement(amount, reason, "OUT");
    }

    @Transactional
    public void recordPurchaseCashIn(BigDecimal amount, String reason) {
        recordAutomaticMovement(amount, reason, "IN");
    }

    @Transactional(readOnly = true)
    public List<CashDrawerSession> findAll() {
        return sessionRepository.findAll(Sort.by(Sort.Direction.DESC, "openedAt"));
    }

    @Transactional(readOnly = true)
    public List<CashDrawerMovement> movements(Integer id) {
        if (!sessionRepository.existsById(id)) throw new ResourceNotFoundException("Cash drawer session not found");
        return movementRepository.findBySessionIdOrderByCreatedAtAsc(id);
    }

    private CashDrawerSession move(Integer id, BigDecimal value, String reason, String type) {
        CashDrawerSession session = openSession(id);
        BigDecimal amount = positive(value);
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Cash movement reason is required");
        if ("IN".equals(type)) session.setCashIn(session.getCashIn().add(amount));
        else session.setCashOut(session.getCashOut().add(amount));
        movementRepository.save(CashDrawerMovement.builder().session(session).type(type).amount(amount)
                .actor(actor()).createdAt(LocalDateTime.now()).reason(reason.trim()).build());
        return sessionRepository.save(session);
    }

    private void updateAutomaticTotal(BigDecimal value, boolean sale) {
        if (value == null || value.signum() <= 0) return;
        sessionRepository.findFirstByOpenedByAndStatusOrderByOpenedAtDesc(actor(), "OPEN").ifPresent(session -> {
            if (sale) session.setCashSales(session.getCashSales().add(value));
            else session.setCashRefunds(session.getCashRefunds().add(value));
            sessionRepository.save(session);
        });
    }

    private CashDrawerSession openSession(Integer id) {
        CashDrawerSession session = sessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cash drawer session not found"));
        if (!"OPEN".equals(session.getStatus())) throw new IllegalStateException("Cash drawer session is closed");
        return session;
    }

    private void recordAutomaticMovement(BigDecimal value, String reason, String type) {
        if (value == null || value.signum() <= 0) return;
        String movementReason = reason == null || reason.isBlank() ? "Purchase cash movement" : reason.trim();
        String movementActor = actor();
        sessionRepository.findFirstByOpenedByAndStatusOrderByOpenedAtDesc(movementActor, "OPEN").ifPresent(session -> {
            if ("IN".equals(type)) session.setCashIn(session.getCashIn().add(value));
            else session.setCashOut(session.getCashOut().add(value));
            movementRepository.save(CashDrawerMovement.builder().session(session).type(type).amount(value)
                    .actor(movementActor).createdAt(LocalDateTime.now()).reason(movementReason).build());
            sessionRepository.save(session);
        });
    }

    private BigDecimal nonNegative(BigDecimal value, String label) {
        BigDecimal amount = value != null ? value : BigDecimal.ZERO;
        if (amount.signum() < 0) throw new IllegalArgumentException(label + " cannot be negative");
        return amount;
    }

    private BigDecimal positive(BigDecimal value) {
        if (value == null || value.signum() <= 0) throw new IllegalArgumentException("Amount must be greater than zero");
        return value;
    }

    private String actor() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "system";
    }
}
