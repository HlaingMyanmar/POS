package org.sspd.servicemgmt.purchaseoptions.purchasereturnoptions.service;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.sspd.servicemgmt.accountingoptions.coaoptions.AccountResolver;
import org.sspd.servicemgmt.accountingoptions.coaoptions.model.ChartOfAccount;
import org.sspd.servicemgmt.accountingoptions.periodlock.service.AccountingPeriodGuard;
import org.sspd.servicemgmt.companysettingoptions.dto.CompanySettingsDTO;
import org.sspd.servicemgmt.companysettingoptions.service.CompanySettingsService;
import org.sspd.servicemgmt.journaloption.entry.service.JournalWriter;
import org.sspd.servicemgmt.purchaseoptions.model.Purchase;
import org.sspd.servicemgmt.purchaseoptions.model.PurchaseStatus;
import org.sspd.servicemgmt.purchaseoptions.purchasedetails.model.PurchaseDetail;
import org.sspd.servicemgmt.purchaseoptions.purchasereturndetails.dto.PurchaseReturnDetailDTO;
import org.sspd.servicemgmt.purchaseoptions.purchasereturndetails.model.PurchaseReturnDetail;
import org.sspd.servicemgmt.purchaseoptions.purchasereturnoptions.dto.PurchaseReturnDTO;
import org.sspd.servicemgmt.purchaseoptions.purchasereturnoptions.mapper.PurchaseReturnMapper;
import org.sspd.servicemgmt.purchaseoptions.purchasereturnoptions.model.PurchaseReturn;
import org.sspd.servicemgmt.purchaseoptions.purchasereturnoptions.repository.PurchaseReturnActivityRepository;
import org.sspd.servicemgmt.purchaseoptions.purchasereturnoptions.repository.PurchaseReturnAttachmentRepository;
import org.sspd.servicemgmt.purchaseoptions.purchasereturnoptions.repository.PurchaseReturnRepository;
import org.sspd.servicemgmt.purchaseoptions.purchasereturnreasonoptions.model.PurchaseReturnReason;
import org.sspd.servicemgmt.purchaseoptions.purchasereturnreasonoptions.repository.PurchaseReturnReasonRepository;
import org.sspd.servicemgmt.purchaseoptions.repository.PurchaseRepository;
import org.sspd.servicemgmt.stockoptions.productoptions.model.Product;
import org.sspd.servicemgmt.stockoptions.productoptions.repository.ProductRepository;
import org.sspd.servicemgmt.supplieroptions.model.Supplier;
import org.sspd.servicemgmt.supplieroptions.repository.SupplierRepository;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PurchaseReturnWorkflowTest {
    @Test
    void approvalQuarantinesWithoutConsumingStock() throws Exception {
        PurchaseReturnRepository returns = mock(PurchaseReturnRepository.class);
        ProductRepository products = mock(ProductRepository.class);
        PurchaseReturnMapper mapper = mock(PurchaseReturnMapper.class);
        Product product = Product.builder().id(4).name("Part").stockQty(10).quarantinedQty(0).hasSerial(false).build();
        PurchaseReturnDetail detail = PurchaseReturnDetail.builder().product(product).qty(3).quarantinedQty(0).build();
        PurchaseReturn entity = PurchaseReturn.builder().id(8).status("PENDING_APPROVAL")
                .purchase(Purchase.builder().id(2).build()).details(List.of(detail)).build();
        when(returns.findByIdForUpdate(8)).thenReturn(Optional.of(entity));
        when(returns.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toDto(any(PurchaseReturn.class))).thenReturn(new PurchaseReturnDTO());
        PurchaseReturnService service = construct(Map.of(
                PurchaseReturnRepository.class, returns,
                ProductRepository.class, products,
                PurchaseReturnMapper.class, mapper));

        service.approve(8, new PurchaseReturnDTO());

        assertEquals("APPROVED", entity.getStatus());
        assertEquals(10, product.getStockQty());
        assertEquals(3, product.getQuarantinedQty());
        assertEquals(3, detail.getQuarantinedQty());
        verify(products).save(product);
    }

    @Test
    void dispatchRejectsUnapprovedReturn() throws Exception {
        PurchaseReturnRepository returns = mock(PurchaseReturnRepository.class);
        PurchaseReturn entity = PurchaseReturn.builder().id(8).status("PENDING_APPROVAL").build();
        when(returns.findByIdForUpdate(8)).thenReturn(Optional.of(entity));
        PurchaseReturnService service = construct(Map.of(PurchaseReturnRepository.class, returns));
        PurchaseReturnDTO request = new PurchaseReturnDTO();
        request.setCarrier("Carrier");
        request.setTrackingNo("TRACK");

        assertThrows(IllegalStateException.class, () -> service.dispatch(8, request));
    }

    @Test
    void dispatchRejectsClosedAccountingPeriod() throws Exception {
        PurchaseReturnRepository returns = mock(PurchaseReturnRepository.class);
        AccountingPeriodGuard periodGuard = mock(AccountingPeriodGuard.class);
        ProductRepository products = mock(ProductRepository.class);
        PurchaseReturn entity = PurchaseReturn.builder().id(8).status("APPROVED")
                .details(List.of(PurchaseReturnDetail.builder().qty(1).quarantinedQty(1).build()))
                .build();
        when(returns.findByIdForUpdate(8)).thenReturn(Optional.of(entity));
        LocalDateTime dispatchedAt = LocalDateTime.of(2024, 1, 15, 10, 0);
        doThrow(new IllegalStateException("Accounting period is locked"))
                .when(periodGuard).assertOpen(dispatchedAt, "dispatch purchase return");
        PurchaseReturnService service = construct(Map.of(
                PurchaseReturnRepository.class, returns,
                AccountingPeriodGuard.class, periodGuard,
                ProductRepository.class, products));
        PurchaseReturnDTO request = new PurchaseReturnDTO();
        request.setCarrier("Carrier");
        request.setTrackingNo("TRACK");
        request.setDispatchedAt(dispatchedAt);

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> service.dispatch(8, request));
        assertTrue(thrown.getMessage().contains("Accounting period is locked"));
        verify(products, never()).save(any());
        verify(returns, never()).save(any());
    }

    @Test
    void dispatchRejectsDateBeforeReturnOrApprovalAndFutureDates() throws Exception {
        PurchaseReturnRepository returns = mock(PurchaseReturnRepository.class);
        ProductRepository products = mock(ProductRepository.class);
        LocalDateTime returnedAt = LocalDateTime.of(2026, 9, 10, 9, 0);
        LocalDateTime approvedAt = LocalDateTime.of(2026, 9, 12, 9, 0);
        PurchaseReturn entity = PurchaseReturn.builder().id(8).status("APPROVED")
                .returnDate(returnedAt).approvedAt(approvedAt)
                .details(List.of(PurchaseReturnDetail.builder().qty(1).quarantinedQty(1).build()))
                .build();
        when(returns.findByIdForUpdate(8)).thenReturn(Optional.of(entity));
        PurchaseReturnService service = construct(Map.of(
                PurchaseReturnRepository.class, returns,
                ProductRepository.class, products));

        IllegalArgumentException beforeReturn = assertThrows(IllegalArgumentException.class, () ->
                service.dispatch(8, dispatchRequest(returnedAt.minusDays(1))));
        assertTrue(beforeReturn.getMessage().toLowerCase().contains("return date"));

        IllegalArgumentException beforeApproved = assertThrows(IllegalArgumentException.class, () ->
                service.dispatch(8, dispatchRequest(approvedAt.minusHours(1))));
        assertTrue(beforeApproved.getMessage().toLowerCase().contains("approved"));

        IllegalArgumentException future = assertThrows(IllegalArgumentException.class, () ->
                service.dispatch(8, dispatchRequest(LocalDateTime.now().plusDays(1))));
        assertTrue(future.getMessage().toLowerCase().contains("future"));
        verify(products, never()).save(any());
        verify(returns, never()).save(any());
    }

    @Test
    void supplierReceivedRejectsDateBeforeDispatch() throws Exception {
        PurchaseReturnRepository returns = mock(PurchaseReturnRepository.class);
        PurchaseReturn entity = PurchaseReturn.builder().id(8).status("DISPATCHED")
                .dispatchedAt(LocalDateTime.of(2026, 9, 15, 10, 0))
                .build();
        when(returns.findByIdForUpdate(8)).thenReturn(Optional.of(entity));
        PurchaseReturnService service = construct(Map.of(PurchaseReturnRepository.class, returns));
        PurchaseReturnDTO request = new PurchaseReturnDTO();
        request.setSupplierReceivedAt(LocalDateTime.of(2026, 9, 14, 10, 0));

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> service.supplierReceived(8, request));
        assertTrue(thrown.getMessage().toLowerCase().contains("dispatch"));
        request.setSupplierReceivedAt(LocalDateTime.now().plusDays(1));
        IllegalArgumentException future = assertThrows(IllegalArgumentException.class,
                () -> service.supplierReceived(8, request));
        assertTrue(future.getMessage().toLowerCase().contains("future"));
        verify(returns, never()).save(any());
    }

    @Test
    void settleRejectsClosedAccountingPeriod() throws Exception {
        PurchaseReturnRepository returns = mock(PurchaseReturnRepository.class);
        AccountingPeriodGuard periodGuard = mock(AccountingPeriodGuard.class);
        PurchaseRepository purchases = mock(PurchaseRepository.class);
        SupplierRepository suppliers = mock(SupplierRepository.class);
        JournalWriter journals = mock(JournalWriter.class);
        PurchaseReturn entity = PurchaseReturn.builder()
                .id(8).status("SUPPLIER_RECEIVED")
                .totalReturnAmount(new BigDecimal("100.00"))
                .supplierShippingPortion(BigDecimal.ZERO)
                .build();
        when(returns.findByIdForUpdate(8)).thenReturn(Optional.of(entity));
        doThrow(new IllegalStateException("Accounting period is locked"))
                .when(periodGuard).assertOpen(any(LocalDateTime.class), eq("settle purchase return"));
        PurchaseReturnService service = construct(Map.of(
                PurchaseReturnRepository.class, returns,
                AccountingPeriodGuard.class, periodGuard,
                PurchaseRepository.class, purchases,
                SupplierRepository.class, suppliers,
                JournalWriter.class, journals));
        PurchaseReturnDTO request = new PurchaseReturnDTO();
        request.setSettlementType("CREDIT_NOTE");
        request.setExpectedCreditAmount(new BigDecimal("100.00"));
        request.setSupplierCreditNoteAmount(new BigDecimal("100.00"));
        request.setSupplierCreditNoteNo("CN-1");

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> service.settle(8, request));
        assertTrue(thrown.getMessage().contains("Accounting period is locked"));
        verify(purchases, never()).findByIdForUpdate(anyInt());
        verify(suppliers, never()).findByIdForUpdate(anyInt());
        verify(journals, never()).write(any());
    }

    @Test
    void voidReturnLocksReturnThenSupplierThenPurchase() throws Exception {
        PurchaseReturnRepository returns = mock(PurchaseReturnRepository.class);
        PurchaseRepository purchases = mock(PurchaseRepository.class);
        SupplierRepository suppliers = mock(SupplierRepository.class);
        ProductRepository products = mock(ProductRepository.class);
        PurchaseReturnMapper mapper = mock(PurchaseReturnMapper.class);
        PurchaseReturnActivityRepository activities = mock(PurchaseReturnActivityRepository.class);
        PurchaseReturnAttachmentRepository attachments = mock(PurchaseReturnAttachmentRepository.class);

        Supplier supplier = Supplier.builder().id(1).name("ACME").build();
        Purchase purchase = Purchase.builder().id(5).supplier(supplier).status(PurchaseStatus.CONFIRMED).build();
        Product product = Product.builder().id(4).name("Part").stockQty(10).quarantinedQty(3).hasSerial(false).build();
        PurchaseReturnDetail detail = PurchaseReturnDetail.builder()
                .product(product).qty(3).quarantinedQty(3).serialNumber(null).build();
        PurchaseReturn entity = PurchaseReturn.builder().id(8).status("APPROVED")
                .purchase(purchase).details(List.of(detail)).build();
        when(returns.findByIdForUpdate(8)).thenReturn(Optional.of(entity));
        when(purchases.findByIdForUpdate(5)).thenReturn(Optional.of(purchase));
        when(suppliers.findByIdForUpdate(1)).thenReturn(Optional.of(supplier));
        when(returns.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toDto(any(PurchaseReturn.class))).thenReturn(new PurchaseReturnDTO());
        when(activities.findByPurchaseReturnIdOrderByOccurredAtAsc(any())).thenReturn(List.of());
        when(attachments.findByPurchaseReturnIdOrderByUploadedAtDesc(any())).thenReturn(List.of());

        PurchaseReturnService service = construct(Map.of(
                PurchaseReturnRepository.class, returns,
                PurchaseRepository.class, purchases,
                SupplierRepository.class, suppliers,
                ProductRepository.class, products,
                PurchaseReturnMapper.class, mapper,
                PurchaseReturnActivityRepository.class, activities,
                PurchaseReturnAttachmentRepository.class, attachments));
        PurchaseReturnDTO request = new PurchaseReturnDTO();
        request.setVoidReason("mistake");

        service.voidReturn(8, request);

        InOrder order = inOrder(returns, suppliers, purchases, products);
        order.verify(returns).findByIdForUpdate(8);
        order.verify(suppliers).findByIdForUpdate(1);
        order.verify(purchases).findByIdForUpdate(5);
        order.verify(products).save(product);
        verify(returns, never()).findById(anyInt());
        verify(purchases, never()).findById(anyInt());
        assertEquals("VOIDED", entity.getStatus());
        assertEquals(0, product.getQuarantinedQty());
    }

    @Test
    void settleLocksReturnThenSupplierThenPurchase() throws Exception {
        PurchaseReturnRepository returns = mock(PurchaseReturnRepository.class);
        PurchaseRepository purchases = mock(PurchaseRepository.class);
        SupplierRepository suppliers = mock(SupplierRepository.class);
        PurchaseReturnMapper mapper = mock(PurchaseReturnMapper.class);
        PurchaseReturnActivityRepository activities = mock(PurchaseReturnActivityRepository.class);
        PurchaseReturnAttachmentRepository attachments = mock(PurchaseReturnAttachmentRepository.class);
        AccountResolver accounts = mock(AccountResolver.class);
        JournalWriter journals = mock(JournalWriter.class);

        Supplier supplier = Supplier.builder().id(1).name("ACME")
                .openingBalance(BigDecimal.ZERO).advanceBalance(BigDecimal.ZERO).build();
        Purchase purchase = Purchase.builder()
                .id(5).supplier(supplier).status(PurchaseStatus.CONFIRMED)
                .totalAmount(new BigDecimal("100.00")).paidAmount(new BigDecimal("100.00"))
                .dueAmount(BigDecimal.ZERO).netAmount(new BigDecimal("100.00"))
                .returnAmount(BigDecimal.ZERO).supplierCreditAmount(BigDecimal.ZERO)
                .discountAmount(BigDecimal.ZERO).otherCharges(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO).withholdingTaxAmount(BigDecimal.ZERO)
                .build();
        PurchaseReturn entity = PurchaseReturn.builder()
                .id(8).returnNo("PRN-8").status("SUPPLIER_RECEIVED")
                .purchase(purchase).details(List.of())
                .totalReturnAmount(new BigDecimal("100.00"))
                .supplierShippingPortion(BigDecimal.ZERO)
                .build();
        when(returns.findByIdForUpdate(8)).thenReturn(Optional.of(entity));
        when(suppliers.findByIdForUpdate(1)).thenReturn(Optional.of(supplier));
        when(purchases.findByIdForUpdate(5)).thenReturn(Optional.of(purchase));
        when(returns.findByPurchaseId(5)).thenReturn(List.of(entity));
        when(returns.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(purchases.save(purchase)).thenReturn(purchase);
        when(purchases.sumDueAmountBySupplierId(1)).thenReturn(BigDecimal.ZERO);
        when(purchases.sumSupplierCreditAmountBySupplierId(1)).thenReturn(new BigDecimal("100.00"));
        when(accounts.payable()).thenReturn(ChartOfAccount.builder().id(22).build());
        when(accounts.supplierAdvance()).thenReturn(ChartOfAccount.builder().id(11).build());
        when(accounts.inventory()).thenReturn(ChartOfAccount.builder().id(33).build());
        when(mapper.toDto(any(PurchaseReturn.class))).thenReturn(new PurchaseReturnDTO());
        when(activities.findByPurchaseReturnIdOrderByOccurredAtAsc(any())).thenReturn(List.of());
        when(attachments.findByPurchaseReturnIdOrderByUploadedAtDesc(any())).thenReturn(List.of());

        PurchaseReturnService service = construct(Map.of(
                PurchaseReturnRepository.class, returns,
                PurchaseRepository.class, purchases,
                SupplierRepository.class, suppliers,
                PurchaseReturnMapper.class, mapper,
                PurchaseReturnActivityRepository.class, activities,
                PurchaseReturnAttachmentRepository.class, attachments,
                AccountResolver.class, accounts,
                JournalWriter.class, journals));
        PurchaseReturnDTO request = new PurchaseReturnDTO();
        request.setSettlementType("CREDIT_NOTE");
        request.setExpectedCreditAmount(new BigDecimal("100.00"));
        request.setSupplierCreditNoteAmount(new BigDecimal("100.00"));
        request.setSupplierCreditNoteNo("CN-1");

        service.settle(8, request);

        InOrder order = inOrder(returns, suppliers, purchases);
        order.verify(returns).findByIdForUpdate(8);
        order.verify(suppliers).findByIdForUpdate(1);
        order.verify(purchases).findByIdForUpdate(5);
        verify(purchases, never()).findById(anyInt());
        verify(suppliers).save(supplier);
        assertEquals("SETTLED", entity.getStatus());
    }

    @Test
    void saveLocksPurchaseBeforeSummingReturnedQty() throws Exception {
        PurchaseRepository purchases = mock(PurchaseRepository.class);
        PurchaseReturnRepository returns = mock(PurchaseReturnRepository.class);
        ProductRepository products = mock(ProductRepository.class);
        PurchaseReturnMapper mapper = mock(PurchaseReturnMapper.class);
        CompanySettingsService settings = mock(CompanySettingsService.class);
        PurchaseReturnActivityRepository activities = mock(PurchaseReturnActivityRepository.class);
        PurchaseReturnAttachmentRepository attachments = mock(PurchaseReturnAttachmentRepository.class);
        PurchaseReturnReasonRepository reasons = mock(PurchaseReturnReasonRepository.class);

        Product product = Product.builder().id(4).name("Part").stockQty(10).hasSerial(false).build();
        Purchase purchase = confirmedPurchase(product, 5);
        stubCreate(purchases, returns, products, mapper, settings, activities, attachments, reasons,
                purchase, product, List.of());

        PurchaseReturnService service = construct(Map.of(
                PurchaseRepository.class, purchases,
                PurchaseReturnRepository.class, returns,
                ProductRepository.class, products,
                PurchaseReturnMapper.class, mapper,
                CompanySettingsService.class, settings,
                PurchaseReturnActivityRepository.class, activities,
                PurchaseReturnAttachmentRepository.class, attachments,
                PurchaseReturnReasonRepository.class, reasons));

        service.save(createRequest(5, 3));

        InOrder order = inOrder(purchases, returns);
        order.verify(purchases).findByIdForUpdate(5);
        order.verify(returns).findByPurchaseId(5);
        verify(purchases, never()).findById(anyInt());
        verify(returns, never()).findTopByOrderByIdDesc();
        org.mockito.ArgumentCaptor<PurchaseReturn> saved = org.mockito.ArgumentCaptor.forClass(PurchaseReturn.class);
        verify(returns, org.mockito.Mockito.atLeast(2)).save(saved.capture());
        assertEquals("PRN-00009", saved.getValue().getReturnNo());
    }

    @Test
    void saveRejectsQtyWhenExistingDraftAlreadyReturnedProduct() throws Exception {
        PurchaseRepository purchases = mock(PurchaseRepository.class);
        PurchaseReturnRepository returns = mock(PurchaseReturnRepository.class);
        ProductRepository products = mock(ProductRepository.class);
        PurchaseReturnMapper mapper = mock(PurchaseReturnMapper.class);
        CompanySettingsService settings = mock(CompanySettingsService.class);
        PurchaseReturnActivityRepository activities = mock(PurchaseReturnActivityRepository.class);
        PurchaseReturnAttachmentRepository attachments = mock(PurchaseReturnAttachmentRepository.class);
        PurchaseReturnReasonRepository reasons = mock(PurchaseReturnReasonRepository.class);

        Product product = Product.builder().id(4).name("Part").stockQty(10).hasSerial(false).build();
        Purchase purchase = confirmedPurchase(product, 5);
        PurchaseReturn existingDraft = PurchaseReturn.builder()
                .id(8).status("DRAFT").purchase(purchase)
                .details(List.of(PurchaseReturnDetail.builder().product(product).qty(5).build()))
                .build();
        stubCreate(purchases, returns, products, mapper, settings, activities, attachments, reasons,
                purchase, product, List.of(existingDraft));

        PurchaseReturnService service = construct(Map.of(
                PurchaseRepository.class, purchases,
                PurchaseReturnRepository.class, returns,
                ProductRepository.class, products,
                PurchaseReturnMapper.class, mapper,
                CompanySettingsService.class, settings,
                PurchaseReturnActivityRepository.class, activities,
                PurchaseReturnAttachmentRepository.class, attachments,
                PurchaseReturnReasonRepository.class, reasons));

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> service.save(createRequest(5, 1)));
        assertTrue(thrown.getMessage().contains("Returnable qty: 0"));
        verify(purchases).findByIdForUpdate(5);
        verify(returns, never()).save(any());
    }

    private static Purchase confirmedPurchase(Product product, int qty) {
        Purchase purchase = Purchase.builder()
                .id(5)
                .purchaseCode("PO-5")
                .status(PurchaseStatus.CONFIRMED)
                .supplier(Supplier.builder().id(1).name("ACME").build())
                .purchaseDate(LocalDateTime.now().minusDays(1))
                .totalAmount(new BigDecimal("100"))
                .netAmount(new BigDecimal("100"))
                .paidAmount(BigDecimal.ZERO)
                .returnAmount(BigDecimal.ZERO)
                .details(new ArrayList<>())
                .build();
        purchase.getDetails().add(PurchaseDetail.builder()
                .purchase(purchase).product(product).qty(qty)
                .subtotal(new BigDecimal("100")).build());
        return purchase;
    }

    private static PurchaseReturnDTO dispatchRequest(LocalDateTime dispatchedAt) {
        PurchaseReturnDTO request = new PurchaseReturnDTO();
        request.setCarrier("Carrier");
        request.setTrackingNo("TRACK");
        request.setDispatchedAt(dispatchedAt);
        return request;
    }

    private static PurchaseReturnDTO createRequest(Integer purchaseId, int qty) {
        PurchaseReturnDTO dto = new PurchaseReturnDTO();
        dto.setPurchaseId(purchaseId);
        dto.setReason("Damaged");
        dto.setReturnDate(LocalDateTime.now());
        PurchaseReturnDetailDTO detail = new PurchaseReturnDetailDTO();
        detail.setProductId(4);
        detail.setQty(qty);
        dto.setDetails(List.of(detail));
        return dto;
    }

    private static void stubCreate(PurchaseRepository purchases,
                                   PurchaseReturnRepository returns,
                                   ProductRepository products,
                                   PurchaseReturnMapper mapper,
                                   CompanySettingsService settings,
                                   PurchaseReturnActivityRepository activities,
                                   PurchaseReturnAttachmentRepository attachments,
                                   PurchaseReturnReasonRepository reasons,
                                   Purchase purchase,
                                   Product product,
                                   List<PurchaseReturn> existingReturns) {
        when(purchases.findByIdForUpdate(5)).thenReturn(Optional.of(purchase));
        when(products.findById(4)).thenReturn(Optional.of(product));
        when(returns.findByPurchaseId(5)).thenReturn(existingReturns);
        CompanySettingsDTO cfg = new CompanySettingsDTO();
        cfg.setPurchaseReturnPrefix("PRN");
        cfg.setPurchaseReturnDigits(5);
        when(settings.getSettings()).thenReturn(cfg);
        when(mapper.toEntity(any(PurchaseReturnDTO.class))).thenReturn(new PurchaseReturn());
        when(mapper.toDto(any(PurchaseReturn.class))).thenReturn(new PurchaseReturnDTO());
        when(returns.save(any(PurchaseReturn.class))).thenAnswer(inv -> {
            PurchaseReturn saved = inv.getArgument(0);
            saved.setId(9);
            return saved;
        });
        when(activities.findByPurchaseReturnIdOrderByOccurredAtAsc(any())).thenReturn(List.of());
        when(attachments.findByPurchaseReturnIdOrderByUploadedAtDesc(any())).thenReturn(List.of());
        when(reasons.findByCodeIgnoreCase("OTHER"))
                .thenReturn(Optional.of(PurchaseReturnReason.builder().id(1).code("OTHER").name("Other").build()));
    }

    private PurchaseReturnService construct(Map<Class<?>, Object> overrides) throws Exception {
        Constructor<?> constructor = Arrays.stream(PurchaseReturnService.class.getConstructors())
                .max(java.util.Comparator.comparingInt(Constructor::getParameterCount)).orElseThrow();
        Object[] args = Arrays.stream(constructor.getParameterTypes())
                .map(type -> overrides.containsKey(type) ? overrides.get(type) : mock(type))
                .toArray();
        return (PurchaseReturnService) constructor.newInstance(args);
    }
}
