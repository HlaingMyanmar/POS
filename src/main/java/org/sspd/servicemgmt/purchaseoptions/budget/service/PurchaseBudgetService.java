package org.sspd.servicemgmt.purchaseoptions.budget.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.categoryoptions.repository.CategoryRepository;
import org.sspd.servicemgmt.purchaseoptions.budget.dto.PurchaseBudgetCheckDTO;
import org.sspd.servicemgmt.purchaseoptions.budget.dto.PurchaseBudgetDTO;
import org.sspd.servicemgmt.purchaseoptions.budget.model.PurchaseBudget;
import org.sspd.servicemgmt.purchaseoptions.budget.repository.PurchaseBudgetRepository;
import org.sspd.servicemgmt.purchaseoptions.purchasedetails.dto.PurchaseDetailDTO;
import org.sspd.servicemgmt.purchaseoptions.purchasedetails.repository.PurchaseDetailRepository;
import org.sspd.servicemgmt.stockoptions.productoptions.repository.ProductRepository;
import org.sspd.servicemgmt.supplieroptions.repository.SupplierRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PurchaseBudgetService {
    private final PurchaseBudgetRepository repository;
    private final PurchaseDetailRepository detailRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final SupplierRepository supplierRepository;

    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_BUDGET')")
    public List<PurchaseBudgetDTO> list() {
        return repository.findAllByOrderByDateFromDesc().stream().map(this::dto).toList();
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_BUDGET')")
    @Transactional
    public PurchaseBudgetDTO save(PurchaseBudgetDTO d) {
        if (d.getDateFrom() == null || d.getDateTo() == null || d.getDateTo().isBefore(d.getDateFrom()))
            throw new RuntimeException("Valid budget period is required.");
        if (d.getLimitAmount() == null || d.getLimitAmount().signum() <= 0)
            throw new RuntimeException("Budget limit must be greater than zero.");
        String mode = d.getEnforcement() == null ? "BLOCK" : d.getEnforcement().trim().toUpperCase();
        if (!Set.of("WARN", "BLOCK").contains(mode))
            throw new RuntimeException("Enforcement must be WARN or BLOCK.");

        PurchaseBudget b = d.getId() == null ? new PurchaseBudget()
                : repository.findById(d.getId()).orElseThrow(() -> new RuntimeException("Budget not found"));
        b.setName(d.getName() == null || d.getName().isBlank() ? "Purchase Budget" : d.getName().trim());
        b.setDateFrom(d.getDateFrom());
        b.setDateTo(d.getDateTo());
        b.setLimitAmount(d.getLimitAmount());
        b.setEnforcement(mode);
        b.setActive(d.getActive() == null || d.getActive());
        b.setCategory(d.getCategoryId() == null ? null
                : categoryRepository.findById(d.getCategoryId().longValue())
                .orElseThrow(() -> new RuntimeException("Category not found")));
        b.setSupplier(d.getSupplierId() == null ? null
                : supplierRepository.findById(d.getSupplierId())
                .orElseThrow(() -> new RuntimeException("Supplier not found")));
        return dto(repository.save(b));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_BUDGET')")
    @Transactional
    public PurchaseBudgetDTO toggle(Integer id, boolean active) {
        var b = repository.findById(id).orElseThrow(() -> new RuntimeException("Budget not found"));
        b.setActive(active);
        return dto(repository.save(b));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_BUDGET')")
    @Transactional
    public void delete(Integer id) {
        if (!repository.existsById(id)) throw new RuntimeException("Budget not found");
        repository.deleteById(id);
    }

    public PurchaseBudgetCheckDTO evaluate(LocalDate date, List<PurchaseDetailDTO> lines, Integer supplierId) {
        LocalDate budgetDate = date == null ? LocalDate.now() : date;
        Map<Integer, BigDecimal> proposed = new HashMap<>();
        BigDecimal overall = BigDecimal.ZERO;
        if (lines != null) {
            for (var line : lines) {
                if (line.getProductId() == null || line.getQty() == null || line.getUnitCost() == null) continue;
                BigDecimal amount = line.getUnitCost().multiply(BigDecimal.valueOf(line.getQty()));
                overall = overall.add(amount);
                var p = productRepository.findById(line.getProductId())
                        .orElseThrow(() -> new IllegalArgumentException("Product not found"));
                if (p.getCategory() != null) proposed.merge(p.getCategory().getId(), amount, BigDecimal::add);
            }
        }
        List<String> warnings = new ArrayList<>();
        List<String> blocks = new ArrayList<>();
        for (var b : repository.findActiveForDate(budgetDate)) {
            if (b.getSupplier() != null) {
                if (supplierId == null || !Objects.equals(b.getSupplier().getId(), supplierId)) continue;
            }
            Integer cat = b.getCategory() == null ? null : b.getCategory().getId();
            BigDecimal add = cat == null ? overall : proposed.getOrDefault(cat, BigDecimal.ZERO);
            if (add.signum() == 0) continue;
            BigDecimal used = spent(b);
            BigDecimal remaining = b.getLimitAmount().subtract(used);
            BigDecimal projected = used.add(add);
            if (projected.compareTo(b.getLimitAmount()) > 0) {
                String scope = b.getSupplier() == null ? "" : (" [" + b.getSupplier().getName() + "]");
                String msg = "ဝယ်ယူမှု ဘတ်ဂျက် ကျော်လွန်နေသည် — " + b.getName() + scope
                        + "\nသုံးပြီး: " + money(used)
                        + "\nကျန်ငွေ: " + money(remaining.max(BigDecimal.ZERO))
                        + "\nဤဝယ်ယူမှု: " + money(add)
                        + "\nခန့်မှန်းစုစုပေါင်း: " + money(projected)
                        + "\nကန့်သတ်: " + money(b.getLimitAmount());
                if ("BLOCK".equals(b.getEnforcement())) blocks.add(msg);
                else warnings.add(msg);
            }
        }
        return PurchaseBudgetCheckDTO.builder().warnings(warnings).blocks(blocks).blocked(!blocks.isEmpty()).build();
    }

    public List<String> validate(LocalDate date, List<PurchaseDetailDTO> lines, Integer supplierId) {
        PurchaseBudgetCheckDTO result = evaluate(date, lines, supplierId);
        if (result.isBlocked()) throw new IllegalStateException(String.join("\n", result.getBlocks()));
        return result.getWarnings() == null ? List.of() : result.getWarnings();
    }

    private String money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private BigDecimal spent(PurchaseBudget b) {
        return detailRepository.sumConfirmedSpend(
                b.getDateFrom().atStartOfDay(),
                b.getDateTo().plusDays(1).atStartOfDay(),
                b.getCategory() == null ? null : b.getCategory().getId(),
                b.getSupplier() == null ? null : b.getSupplier().getId());
    }

    private PurchaseBudgetDTO dto(PurchaseBudget b) {
        BigDecimal s = spent(b);
        BigDecimal r = b.getLimitAmount().subtract(s);
        BigDecimal p = b.getLimitAmount().signum() == 0 ? BigDecimal.ZERO
                : s.multiply(BigDecimal.valueOf(100)).divide(b.getLimitAmount(), 2, RoundingMode.HALF_UP);
        return PurchaseBudgetDTO.builder()
                .id(b.getId()).name(b.getName()).dateFrom(b.getDateFrom()).dateTo(b.getDateTo())
                .categoryId(b.getCategory() == null ? null : b.getCategory().getId())
                .categoryName(b.getCategory() == null ? "All Categories" : b.getCategory().getName())
                .supplierId(b.getSupplier() == null ? null : b.getSupplier().getId())
                .supplierName(b.getSupplier() == null ? "All Suppliers" : b.getSupplier().getName())
                .limitAmount(b.getLimitAmount()).enforcement(b.getEnforcement()).active(b.getActive())
                .spentAmount(s).remainingAmount(r).usagePercent(p).build();
    }
}
