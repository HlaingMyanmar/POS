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
    public static final String SALE_TYPE = "SALE";
    public static final String REFUND_TYPE = "REFUND";
    public static final String IN_TYPE = "IN";
    public static final String OUT_TYPE = "OUT";

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
        recordCashSale(amount, null, null);
    }

    @Transactional
    public void recordCashSale(BigDecimal amount, String referenceType, Integer referenceId) {
        recordOnOpenSession(SALE_TYPE, amount, referenceType, referenceId,
                movementReason("Cash sale", referenceType, referenceId), false);
    }

    @Transactional
    public void recordCashRefund(BigDecimal amount) {
        recordCashRefund(amount, null, null);
    }

    @Transactional
    public void recordCashRefund(BigDecimal amount, String referenceType, Integer referenceId) {
        recordOnOpenSession(REFUND_TYPE, amount, referenceType, referenceId,
                movementReason("Cash refund", referenceType, referenceId), true);
    }

    @Transactional
    public void recordCompensatingCashIn(BigDecimal amount, String referenceType, Integer referenceId, String reason) {
        recordOnOpenSession(IN_TYPE, amount, referenceType, referenceId,
                reason == null || reason.isBlank() ? movementReason("Cash in", referenceType, referenceId) : reason.trim(),
                true);
    }

    @Transactional
    public void reverseCashRefund(String referenceType, Integer referenceId, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) return;
        if (referenceType == null || referenceType.isBlank() || referenceId == null) {
            throw new IllegalStateException("Cash refund reversal requires the original drawer reference");
        }
        List<CashDrawerMovement> refunds = movementRepository
                .findByTypeAndReferenceTypeAndReferenceIdAndReversedFalseOrderByIdAsc(
                        REFUND_TYPE, referenceType, referenceId);
        if (refunds.isEmpty()) {
            throw new IllegalStateException(
                    "Original cash drawer refund was not found; will not reverse against another cashier session");
        }
        BigDecimal recorded = refunds.stream()
                .map(CashDrawerMovement::getAmount)
                .filter(value -> value != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (recorded.compareTo(amount) != 0) {
            throw new IllegalStateException(
                    "Original cash drawer refund " + recorded + " does not match reversal amount " + amount);
        }
        for (CashDrawerMovement refund : refunds) {
            if (refund.getSession() == null || refund.getSession().getId() == null) {
                throw new IllegalStateException("Original cash drawer refund is missing its session");
            }
            CashDrawerSession session = sessionRepository.findByIdForUpdate(refund.getSession().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Cash drawer session not found"));
            if (!"OPEN".equals(session.getStatus())) {
                throw new IllegalStateException(
                        "Closed cash drawer sessions cannot be restated. Record a compensating movement on the current session instead.");
            }
            BigDecimal reverseAmount = refund.getAmount() != null ? refund.getAmount() : BigDecimal.ZERO;
            BigDecimal current = safe(session.getCashRefunds());
            if (current.compareTo(reverseAmount) < 0) {
                throw new IllegalStateException(
                        "Original cash drawer session does not have enough recorded refunds to reverse");
            }
            session.setCashRefunds(current.subtract(reverseAmount));
            sessionRepository.save(session);
            refund.setReversed(Boolean.TRUE);
            movementRepository.save(refund);
        }
    }

    @Transactional
    public void recordPurchaseCashOut(BigDecimal amount, String reason) {
        recordPurchaseCashOut(amount, reason, null, null);
    }

    @Transactional
    public void recordPurchaseCashOut(BigDecimal amount, String reason, String referenceType, Integer referenceId) {
        recordOnOpenSession(OUT_TYPE, amount, referenceType, referenceId,
                reason == null || reason.isBlank() ? "Purchase cash movement" : reason.trim(), false);
    }

    @Transactional
    public void recordPurchaseCashIn(BigDecimal amount, String reason) {
        recordPurchaseCashIn(amount, reason, null, null);
    }

    @Transactional
    public void recordPurchaseCashIn(BigDecimal amount, String reason, String referenceType, Integer referenceId) {
        recordOnOpenSession(IN_TYPE, amount, referenceType, referenceId,
                reason == null || reason.isBlank() ? "Purchase cash movement" : reason.trim(), true);
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

    private void recordOnOpenSession(String type, BigDecimal amount, String referenceType, Integer referenceId,
            String reason, boolean uniqueByReference) {
        if (amount == null || amount.signum() <= 0) return;
        if (uniqueByReference && referenceType != null && !referenceType.isBlank() && referenceId != null
                && movementRepository.existsByTypeAndReferenceTypeAndReferenceIdAndReversedFalse(
                        type, referenceType, referenceId)) {
            throw new IllegalStateException(
                    "Cash drawer already has a " + type + " movement for " + referenceType + " #" + referenceId);
        }
        String movementActor = actor();
        sessionRepository.findFirstByOpenedByAndStatusOrderByOpenedAtDesc(movementActor, "OPEN").ifPresent(session -> {
            if (SALE_TYPE.equals(type)) session.setCashSales(safe(session.getCashSales()).add(amount));
            else if (REFUND_TYPE.equals(type)) session.setCashRefunds(safe(session.getCashRefunds()).add(amount));
            else if (IN_TYPE.equals(type)) session.setCashIn(safe(session.getCashIn()).add(amount));
            else session.setCashOut(safe(session.getCashOut()).add(amount));
            movementRepository.save(CashDrawerMovement.builder()
                    .session(session)
                    .type(type)
                    .amount(amount)
                    .actor(movementActor)
                    .createdAt(LocalDateTime.now())
                    .reason(reason)
                    .referenceType(referenceType)
                    .referenceId(referenceId)
                    .reversed(Boolean.FALSE)
                    .build());
            sessionRepository.save(session);
        });
    }

    private CashDrawerSession openSession(Integer id) {
        CashDrawerSession session = sessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cash drawer session not found"));
        if (!"OPEN".equals(session.getStatus())) throw new IllegalStateException("Cash drawer session is closed");
        return session;
    }

    private String movementReason(String prefix, String referenceType, Integer referenceId) {
        if (referenceType == null || referenceId == null) return prefix;
        return prefix + " " + referenceType + " #" + referenceId;
    }

    private BigDecimal safe(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
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
