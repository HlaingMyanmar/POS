package org.sspd.servicemgmt.customerportaloptions;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.model.PaymentMethod;
import org.sspd.servicemgmt.accountingoptions.coaoptions.AccountResolver;
import org.sspd.servicemgmt.accountingoptions.coaoptions.model.ChartOfAccount;
import org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.repository.PaymentMethodRepository;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.model.PaymentTransaction;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.model.ReferenceType;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.repository.PaymentTransactionRepository;
import org.sspd.servicemgmt.customeroptions.model.Customer;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerProductReturnRequest;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerProductReturnReviewRequest;
import org.sspd.servicemgmt.customerportaloptions.model.CustomerOrder;
import org.sspd.servicemgmt.customerportaloptions.model.CustomerOrderStatus;
import org.sspd.servicemgmt.customerportaloptions.model.CustomerProductReturn;
import org.sspd.servicemgmt.customerportaloptions.model.CustomerProductReturnLine;
import org.sspd.servicemgmt.customerportaloptions.repository.CustomerOrderRepository;
import org.sspd.servicemgmt.customerportaloptions.repository.CustomerProductReturnPhotoRepository;
import org.sspd.servicemgmt.customerportaloptions.repository.CustomerProductReturnRepository;
import org.sspd.servicemgmt.customerportaloptions.service.CustomerProductReturnService;
import org.sspd.servicemgmt.dataevent.DataEventPublisher;
import org.sspd.servicemgmt.jwt.CustomerPortalUserDetails;
import org.sspd.servicemgmt.saleoptions.model.Sale;
import org.sspd.servicemgmt.saleoptions.repository.SaleRepository;
import org.sspd.servicemgmt.saleoptions.saledetails.model.SaleDetail;
import org.sspd.servicemgmt.stockoptions.productoptions.model.Product;
import org.sspd.servicemgmt.stockoptions.productserialoptions.repository.ProductSerialRepository;
import org.sspd.servicemgmt.stockoptions.stockmovementoptions.service.StockMovementService;
import org.sspd.servicemgmt.journaloption.entry.service.JournalWriter;
import jakarta.persistence.EntityManager;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CustomerProductReturnFlowTest {

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private CustomerProductReturnService service(
            CustomerProductReturnRepository returns,
            CustomerOrderRepository orders,
            SaleRepository sales,
            PaymentMethodRepository methods,
            PaymentTransactionRepository txs,
            DataEventPublisher events) {
        CustomerProductReturnService svc = mock(CustomerProductReturnService.class, CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(svc, "returns", returns);
        ReflectionTestUtils.setField(svc, "photos", mock(CustomerProductReturnPhotoRepository.class));
        ReflectionTestUtils.setField(svc, "orders", orders);
        ReflectionTestUtils.setField(svc, "sales", sales);
        ReflectionTestUtils.setField(svc, "methods", methods);
        ReflectionTestUtils.setField(svc, "paymentTransactions", txs);
        ReflectionTestUtils.setField(svc, "events", events);
        ReflectionTestUtils.setField(svc, "entityManager", mock(EntityManager.class));
        ReflectionTestUtils.setField(svc, "serials", mock(ProductSerialRepository.class));
        ReflectionTestUtils.setField(svc, "stockMovements", mock(StockMovementService.class));
        ReflectionTestUtils.setField(svc, "accountResolver", mock(AccountResolver.class));
        ReflectionTestUtils.setField(svc, "journalWriter", mock(JournalWriter.class));
        return svc;
    }

    private Customer customer() {
        Customer c = new Customer();
        c.setId(7);
        c.setName("Test");
        return c;
    }

    private CustomerProductReturn reviewEntity(String status) {
        CustomerProductReturn entity = CustomerProductReturn.builder()
                .id(33).returnNo("CR-000033").customer(customer()).saleId(9)
                .status(status).deliveryStatus("PICKUP_REQUESTED")
                .reason("Regression").lines(new ArrayList<>()).photos(new ArrayList<>())
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
        return entity;
    }

    private CustomerProductReturnService reviewService(CustomerProductReturn entity) {
        CustomerProductReturnRepository repo = mock(CustomerProductReturnRepository.class);
        when(repo.findLockedByIdWithLines(entity.getId())).thenReturn(Optional.of(entity));
        when(repo.saveAndFlush(entity)).thenReturn(entity);
        return service(repo, mock(CustomerOrderRepository.class), mock(SaleRepository.class),
                mock(PaymentMethodRepository.class), mock(PaymentTransactionRepository.class),
                mock(DataEventPublisher.class));
    }

    private void auth() {
        var details = new CustomerPortalUserDetails("customer:id:7", "", true, List.of(), 0, 7, "T", "09");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
    }

    private Sale sale(Customer customer) {
        Product product = new Product();
        product.setId(11);
        product.setName("Phone");
        product.setHasSerial(true);
        SaleDetail detail = SaleDetail.builder()
                .id(21)
                .product(product)
                .qty(1)
                .unitPrice(new BigDecimal("50000.00"))
                .subtotal(new BigDecimal("50000.00"))
                .serialNumber("SN-1")
                .build();
        Sale sale = Sale.builder()
                .id(9)
                .saleCode("S-9")
                .customer(customer)
                .voided(false)
                .details(List.of(detail))
                .build();
        detail.setSale(sale);
        return sale;
    }

    private CustomerOrder order(Customer customer, Sale sale) {
        return CustomerOrder.builder()
                .id(42)
                .orderNo("CA-000042")
                .customer(customer)
                .status(CustomerOrderStatus.CONFIRMED)
                .completedSaleId(sale.getId())
                .paymentState("FULFILLED")
                .lines(new ArrayList<>())
                .build();
    }

    @Test
    void submitGoesToRequestedWithoutTouchingPaymentRefund() throws Exception {
        auth();
        Customer customer = customer();
        Sale sale = sale(customer);
        CustomerOrder order = order(customer, sale);
        CustomerProductReturnRepository returns = mock(CustomerProductReturnRepository.class);
        when(returns.findBySaleIdOrderByIdDesc(9)).thenReturn(List.of());
        when(returns.findByOrder_IdOrderByIdDesc(42)).thenReturn(List.of());
        when(returns.saveAndFlush(any(CustomerProductReturn.class))).thenAnswer(inv -> {
            CustomerProductReturn r = inv.getArgument(0);
            if (r.getId() == null) r.setId(3);
            return r;
        });
        CustomerOrderRepository orders = mock(CustomerOrderRepository.class);
        when(orders.findByIdWithLines(42)).thenReturn(Optional.of(order));
        SaleRepository sales = mock(SaleRepository.class);
        when(sales.findLockedWithDetails(9)).thenReturn(Optional.of(sale));

        CustomerProductReturnRequest req = new CustomerProductReturnRequest();
        req.setReason("ပစ္စည်းပျက်");
        CustomerProductReturnRequest.Line line = new CustomerProductReturnRequest.Line();
        line.setProductId(11);
        line.setQty(1);
        line.setSerialNumber("SN-1");
        req.setLines(List.of(line));

        PaymentTransactionRepository txs = mock(PaymentTransactionRepository.class);
        var dto = service(returns, orders, sales, mock(PaymentMethodRepository.class),
                txs, mock(DataEventPublisher.class))
                .submit(42, req, List.of());

        assertEquals("REQUESTED", dto.getStatus());
        assertEquals("CR-000003", dto.getReturnNo());
        assertEquals(9, dto.getSaleId());
        assertEquals(42, dto.getOrderId());
        assertEquals(1, dto.getLines().size());
        assertEquals("SN-1", dto.getLines().get(0).getSerialNumber());
        verify(txs, never()).save(any());
    }

    @Test
    void shopReviewReceiveInspectThenRefundWritesReturnTransaction() {
        Customer customer = customer();
        CustomerProductReturnLine line = CustomerProductReturnLine.builder()
                .id(5)
                .productId(11)
                .productName("Phone")
                .qty(1)
                .unitPrice(new BigDecimal("50000.00"))
                .subtotal(new BigDecimal("50000.00"))
                .build();
        CustomerProductReturn entity = CustomerProductReturn.builder()
                .id(3)
                .returnNo("CR-000003")
                .customer(customer)
                .saleId(9)
                .status("REQUESTED")
                .reason("ပျက်")
                .lines(new ArrayList<>(List.of(line)))
                .photos(new ArrayList<>())
                .build();
        line.setProductReturn(entity);
        CustomerProductReturnRepository returns = mock(CustomerProductReturnRepository.class);
        when(returns.findLockedByIdWithLines(3)).thenReturn(Optional.of(entity));
        when(returns.saveAndFlush(entity)).thenReturn(entity);
        PaymentMethod method = new PaymentMethod();
        method.setId(4);
        method.setActive(true);
        method.setMethodName("KBZPay");
        ChartOfAccount paymentAccount = new ChartOfAccount();
        paymentAccount.setId(101);
        method.setAccount(paymentAccount);
        PaymentMethodRepository methods = mock(PaymentMethodRepository.class);
        when(methods.findById(4)).thenReturn(Optional.of(method));
        PaymentTransactionRepository txs = mock(PaymentTransactionRepository.class);
        DataEventPublisher events = mock(DataEventPublisher.class);
        var svc = service(returns, mock(CustomerOrderRepository.class), mock(SaleRepository.class), methods, txs, events);
        Product returnedProduct = new Product();
        returnedProduct.setId(11);
        returnedProduct.setName("Phone");
        returnedProduct.setHasSerial(false);
        returnedProduct.setStockQty(0);
        EntityManager em = (EntityManager) ReflectionTestUtils.getField(svc, "entityManager");
        when(em.find(Product.class, 11, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE))
                .thenReturn(returnedProduct);
        AccountResolver resolver = (AccountResolver) ReflectionTestUtils.getField(svc, "accountResolver");
        ChartOfAccount salesReturn = new ChartOfAccount();
        salesReturn.setId(401);
        when(resolver.salesRtn()).thenReturn(salesReturn);

        CustomerProductReturnReviewRequest approve = new CustomerProductReturnReviewRequest();
        approve.setAction("APPROVE");
        approve.setNote("လက်ခံသည်");
        assertEquals("APPROVED", svc.review(3, approve).getStatus());

        for (String state : List.of("PICKED_UP", "RETURN_IN_TRANSIT", "RECEIVED_BY_SHOP")) {
            CustomerProductReturnReviewRequest delivery = new CustomerProductReturnReviewRequest();
            delivery.setAction("DELIVERY");
            delivery.setDeliveryStatus(state);
            svc.review(3, delivery);
        }

        CustomerProductReturnReviewRequest received = new CustomerProductReturnReviewRequest();
        received.setAction("RECEIVED");
        assertEquals("RETURNED", svc.review(3, received).getStatus());

        CustomerProductReturnReviewRequest inspect = new CustomerProductReturnReviewRequest();
        inspect.setAction("INSPECT");
        inspect.setNote("ဖွင့်ကြည့်ပြီး");
        assertEquals("INSPECTING", svc.review(3, inspect).getStatus());

        CustomerProductReturnReviewRequest complete = new CustomerProductReturnReviewRequest();
        complete.setAction("COMPLETE");
        complete.setOutcome("REFUNDED");
        complete.setRefundAmount(new BigDecimal("50000.00"));
        complete.setPaymentMethodId(4);
        complete.setTransactionNo("RF-1");
        complete.setNote("ငွေပြန်");
        CustomerProductReturnReviewRequest.Line disp = new CustomerProductReturnReviewRequest.Line();
        disp.setId(5);
        disp.setDisposition("DAMAGED");
        complete.setLines(List.of(disp));
        assertEquals("REFUNDED", svc.review(3, complete).getStatus());
        assertEquals("DAMAGED", entity.getLines().get(0).getDisposition());
        ArgumentCaptor<PaymentTransaction> captor = ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(txs).save(captor.capture());
        assertEquals(ReferenceType.Customer_Order_Return, captor.getValue().getReferenceType());
        assertEquals(0, new BigDecimal("-50000.00").compareTo(captor.getValue().getAmount()));
        assertEquals(3, captor.getValue().getReferenceId());
        JournalWriter writer = (JournalWriter) ReflectionTestUtils.getField(svc, "journalWriter");
        verify(writer).write(any());
    }

    @Test
    void deliveryCannotSkipAForwardMilestone() {
        CustomerProductReturn entity = reviewEntity("APPROVED");
        CustomerProductReturnReviewRequest request = new CustomerProductReturnReviewRequest();
        request.setAction("DELIVERY");
        request.setDeliveryStatus("RETURN_IN_TRANSIT");
        assertThrows(IllegalStateException.class,
                () -> reviewService(entity).review(entity.getId(), request));
    }

    @Test
    void deliveryMovesForwardExactlyOneMilestone() {
        CustomerProductReturn entity = reviewEntity("APPROVED");
        CustomerProductReturnReviewRequest request = new CustomerProductReturnReviewRequest();
        request.setAction("DELIVERY");
        request.setDeliveryStatus("PICKED_UP");
        assertEquals("PICKED_UP",
                reviewService(entity).review(entity.getId(), request).getDeliveryStatus());
    }

    @Test
    void shopCannotReceiveBeforeDeliveryArrives() {
        CustomerProductReturn entity = reviewEntity("APPROVED");
        CustomerProductReturnReviewRequest request = new CustomerProductReturnReviewRequest();
        request.setAction("RECEIVED");
        assertThrows(IllegalStateException.class,
                () -> reviewService(entity).review(entity.getId(), request));
    }

    @Test
    void inspectionRequiresRecordedShopReceipt() {
        CustomerProductReturn entity = reviewEntity("RETURNED");
        entity.setDeliveryStatus("RECEIVED_BY_SHOP");
        CustomerProductReturnReviewRequest request = new CustomerProductReturnReviewRequest();
        request.setAction("INSPECT");
        assertThrows(IllegalStateException.class,
                () -> reviewService(entity).review(entity.getId(), request));
    }

    @Test
    void completionSideEffectsCannotRunTwice() {
        CustomerProductReturn entity = reviewEntity("INSPECTING");
        entity.setDeliveryStatus("RECEIVED_BY_SHOP");
        entity.setReceivedAt(LocalDateTime.now());
        entity.setInventoryApplied(true);
        CustomerProductReturnReviewRequest request = new CustomerProductReturnReviewRequest();
        request.setAction("COMPLETE");
        request.setOutcome("CLOSED");
        assertThrows(IllegalStateException.class,
                () -> reviewService(entity).review(entity.getId(), request));
    }

    @Test
    void duplicateSerialCannotBeSubmittedAgain() {
        auth();
        Customer customer = customer();
        Sale sale = sale(customer);
        sale.getDetails().get(0).setQty(2);
        CustomerOrder order = order(customer, sale);
        CustomerProductReturnLine priorLine = CustomerProductReturnLine.builder()
                .productId(11).productName("Phone").qty(1)
                .unitPrice(BigDecimal.ONE).subtotal(BigDecimal.ONE).serialNumber("SN-1").build();
        CustomerProductReturn prior = reviewEntity("REFUNDED");
        prior.setLines(new ArrayList<>(List.of(priorLine)));
        CustomerProductReturnRepository repo = mock(CustomerProductReturnRepository.class);
        when(repo.findBySaleIdOrderByIdDesc(9)).thenReturn(List.of(prior));
        when(repo.findByOrder_IdOrderByIdDesc(42)).thenReturn(List.of());
        CustomerOrderRepository orderRepo = mock(CustomerOrderRepository.class);
        when(orderRepo.findByIdWithLines(42)).thenReturn(Optional.of(order));
        SaleRepository saleRepo = mock(SaleRepository.class);
        when(saleRepo.findLockedWithDetails(9)).thenReturn(Optional.of(sale));
        CustomerProductReturnRequest request = new CustomerProductReturnRequest();
        request.setReason("Duplicate serial regression");
        CustomerProductReturnRequest.Line line = new CustomerProductReturnRequest.Line();
        line.setProductId(11);
        line.setQty(1);
        line.setSerialNumber("SN-1");
        request.setLines(List.of(line));
        assertThrows(IllegalStateException.class, () -> service(repo, orderRepo, saleRepo,
                mock(PaymentMethodRepository.class), mock(PaymentTransactionRepository.class),
                mock(DataEventPublisher.class)).submit(42, request, List.of()));
    }

    @Test
    void replacementConsumesStockAndRecordsBothMovements() {
        CustomerProductReturn entity = reviewEntity("INSPECTING");
        entity.setDeliveryStatus("RECEIVED_BY_SHOP");
        entity.setReceivedAt(LocalDateTime.now());
        CustomerProductReturnLine line = CustomerProductReturnLine.builder()
                .id(71).productId(11).productName("Cable").qty(1)
                .unitPrice(BigDecimal.TEN).subtotal(BigDecimal.TEN).build();
        line.setProductReturn(entity);
        entity.setLines(new ArrayList<>(List.of(line)));
        CustomerProductReturnService svc = reviewService(entity);
        Product product = new Product();
        product.setId(11);
        product.setName("Cable");
        product.setHasSerial(false);
        product.setStockQty(5);
        product.setQuarantinedQty(0);
        product.setCustomerReservedQty(0);
        EntityManager em = (EntityManager) ReflectionTestUtils.getField(svc, "entityManager");
        when(em.find(Product.class, 11, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE))
                .thenReturn(product);
        CustomerProductReturnReviewRequest request = new CustomerProductReturnReviewRequest();
        request.setAction("COMPLETE");
        request.setOutcome("REPLACED");
        CustomerProductReturnReviewRequest.Line requested = new CustomerProductReturnReviewRequest.Line();
        requested.setId(71);
        requested.setDisposition("DAMAGED");
        requested.setReplacementProductId(11);
        request.setLines(List.of(requested));
        assertEquals("REPLACED", svc.review(33, request).getStatus());
        assertEquals(4, product.getStockQty());
        assertTrue(entity.isInventoryApplied());
        StockMovementService movements =
                (StockMovementService) ReflectionTestUtils.getField(svc, "stockMovements");
        verify(movements, times(2)).recordMovement(any());
    }
}
