package org.sspd.servicemgmt.saleoptions.salereturnoptions.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.sspd.servicemgmt.accountingoptions.coaoptions.AccountResolver;
import org.sspd.servicemgmt.accountingoptions.coaoptions.model.ChartOfAccount;
import org.sspd.servicemgmt.journaloption.entry.dto.JournalEntryDTO;
import org.sspd.servicemgmt.journaloption.entry.service.JournalWriter;
import org.sspd.servicemgmt.saleoptions.model.Sale;
import org.sspd.servicemgmt.saleoptions.repository.SaleRepository;
import org.sspd.servicemgmt.saleoptions.saledetails.model.SaleDetail;
import org.sspd.servicemgmt.saleoptions.saledetails.repository.SaleDetailRepository;
import org.sspd.servicemgmt.saleoptions.salereturndetails.dto.SaleReturnDetailDTO;
import org.sspd.servicemgmt.saleoptions.salereturndetails.model.SaleReturnDetail;
import org.sspd.servicemgmt.saleoptions.salereturndetails.repository.SaleReturnDetailRepository;
import org.sspd.servicemgmt.saleoptions.salereturnoptions.dto.SaleReturnDTO;
import org.sspd.servicemgmt.saleoptions.salereturnoptions.mapper.SaleReturnMapper;
import org.sspd.servicemgmt.saleoptions.salereturnoptions.model.SaleReturn;
import org.sspd.servicemgmt.saleoptions.salereturnoptions.repository.SaleReturnRepository;
import org.sspd.servicemgmt.saleoptions.salereturnreasonoptions.model.SaleReturnReason;
import org.sspd.servicemgmt.saleoptions.salereturnreasonoptions.repository.SaleReturnReasonRepository;
import org.sspd.servicemgmt.stockoptions.productoptions.model.Product;
import org.sspd.servicemgmt.stockoptions.productoptions.repository.ProductRepository;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SaleReturnAccountingRegressionTest {

    @Test
    void saveRejectsVoidedSale() throws Exception {
        SaleRepository sales = mock(SaleRepository.class);
        when(sales.findLockedWithDetails(11)).thenReturn(Optional.of(Sale.builder().id(11).saleCode("INV-11").voided(true).build()));
        SaleReturnService service = construct(Map.of(SaleRepository.class, sales));

        SaleReturnDTO dto = new SaleReturnDTO();
        dto.setSaleId(11);
        SaleReturnDetailDTO line = new SaleReturnDetailDTO();
        line.setProductId(3);
        line.setQty(1);
        dto.setDetails(List.of(line));

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> service.save(dto));
        assertTrue(thrown.getMessage().toLowerCase().contains("voided"));
    }

    @Test
    void saveRejectsDuplicateLinesThatExceedSoldQuantity() throws Exception {
        SaleRepository sales = mock(SaleRepository.class);
        SaleDetailRepository saleDetails = mock(SaleDetailRepository.class);
        SaleReturnRepository returns = mock(SaleReturnRepository.class);
        ProductRepository products = mock(ProductRepository.class);
        SaleReturnMapper mapper = mock(SaleReturnMapper.class);
        SaleReturnReasonRepository reasons = mock(SaleReturnReasonRepository.class);
        Product product = Product.builder().id(3).name("Filter").stockQty(10).build();
        Sale sale = Sale.builder().id(11).saleCode("INV-11").voided(false).discountAmount(BigDecimal.ZERO).build();
        SaleDetail sold = SaleDetail.builder().product(product).qty(5).subtotal(new BigDecimal("50")).build();
        when(sales.findLockedWithDetails(11)).thenReturn(Optional.of(sale));
        when(returns.findAllBySaleIdAndDeletedFalse(11)).thenReturn(List.of());
        when(saleDetails.findAllBySaleId(11)).thenReturn(List.of(sold));
        when(products.findById(3)).thenReturn(Optional.of(product));
        when(mapper.toEntity(org.mockito.ArgumentMatchers.any())).thenReturn(SaleReturn.builder().build());
        when(reasons.findById(1)).thenReturn(Optional.of(SaleReturnReason.builder().id(1).code("OTHER").build()));

        SaleReturnService service = construct(Map.of(
                SaleRepository.class, sales,
                SaleDetailRepository.class, saleDetails,
                SaleReturnRepository.class, returns,
                ProductRepository.class, products,
                SaleReturnMapper.class, mapper,
                SaleReturnReasonRepository.class, reasons));

        SaleReturnDTO dto = new SaleReturnDTO();
        dto.setSaleId(11);
        dto.setDetails(List.of(line(3, 4), line(3, 4)));

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> service.save(dto));
        assertTrue(thrown.getMessage().toLowerCase().contains("exceeds"));
        assertEquals(14, product.getStockQty());
    }

    @Test
    void returnJournalRestoresInventoryAndCogs() throws Exception {
        JournalWriter writer = mock(JournalWriter.class);
        AccountResolver accounts = mock(AccountResolver.class);
        SaleDetailRepository saleDetails = mock(SaleDetailRepository.class);
        when(accounts.salesRtn()).thenReturn(account(1));
        when(accounts.receivable()).thenReturn(account(9));
        when(accounts.inventory()).thenReturn(account(22));
        when(accounts.cogs()).thenReturn(account(7));
        Product product = Product.builder().id(3).name("Filter").build();
        when(saleDetails.findAllBySaleId(11)).thenReturn(List.of(
                SaleDetail.builder().product(product).qty(5).costPriceSnapshot(new BigDecimal("8.00")).build()));
        SaleReturnService service = construct(Map.of(
                JournalWriter.class, writer,
                AccountResolver.class, accounts,
                SaleDetailRepository.class, saleDetails));
        SaleReturn saleReturn = SaleReturn.builder()
                .returnCode("SR-9")
                .totalReturnAmount(new BigDecimal("40.00"))
                .sale(Sale.builder().id(11).build())
                .details(List.of(SaleReturnDetail.builder().product(product).qty(2).subtotal(new BigDecimal("40.00")).restock(true).build()))
                .build();

        invoke(service, "createReturnJournal",
                new Class<?>[]{SaleReturn.class, BigDecimal.class, BigDecimal.class, List.class},
                saleReturn, BigDecimal.ZERO, BigDecimal.ZERO, List.of());

        ArgumentCaptor<JournalEntryDTO> captor = ArgumentCaptor.forClass(JournalEntryDTO.class);
        org.mockito.Mockito.verify(writer).write(captor.capture());
        JournalEntryDTO entry = captor.getValue();
        assertEquals(new BigDecimal("16.00"), entry.getDetails().stream()
                .filter(d -> Integer.valueOf(22).equals(d.getAccountId()))
                .map(d -> d.getDebit()).findFirst().orElseThrow());
        assertEquals(new BigDecimal("16.00"), entry.getDetails().stream()
                .filter(d -> Integer.valueOf(7).equals(d.getAccountId()))
                .map(d -> d.getCredit()).findFirst().orElseThrow());
        assertEquals(
                entry.getDetails().stream().map(d -> d.getDebit()).reduce(BigDecimal.ZERO, BigDecimal::add),
                entry.getDetails().stream().map(d -> d.getCredit()).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    @Test
    void voidReturnRejectsInsufficientStock() throws Exception {
        SaleReturnRepository returns = mock(SaleReturnRepository.class);
        SaleReturnDetailRepository details = mock(SaleReturnDetailRepository.class);
        Product product = Product.builder().id(3).name("Filter").stockQty(2).build();
        Sale sale = Sale.builder().id(11).build();
        SaleReturn existing = SaleReturn.builder().id(9).status("COMPLETED").deleted(false).sale(sale).build();
        when(returns.findByIdForUpdate(9)).thenReturn(Optional.of(existing));
        SaleRepository sales = mock(SaleRepository.class);
        when(sales.findLockedWithDetails(11)).thenReturn(Optional.of(sale));
        when(details.findAllBySaleReturnIn(List.of(existing))).thenReturn(List.of(
                SaleReturnDetail.builder().product(product).qty(5).restock(true).build()));
        SaleReturnService service = construct(Map.of(
                SaleReturnRepository.class, returns,
                SaleReturnDetailRepository.class, details,
                SaleRepository.class, sales));

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> service.voidReturn(9, "mistake"));
        assertTrue(thrown.getMessage().toLowerCase().contains("insufficient"));
        assertEquals(2, product.getStockQty());
    }

    @Test
    void voidReturnRejectsSerialAlreadySoldAgain() throws Exception {
        SaleReturnRepository returns = mock(SaleReturnRepository.class);
        SaleReturnDetailRepository details = mock(SaleReturnDetailRepository.class);
        org.sspd.servicemgmt.stockoptions.productserialoptions.repository.ProductSerialRepository serials =
                mock(org.sspd.servicemgmt.stockoptions.productserialoptions.repository.ProductSerialRepository.class);
        Product product = Product.builder().id(3).name("Pump").build();
        Sale sale = Sale.builder().id(11).build();
        SaleReturn existing = SaleReturn.builder().id(9).status("COMPLETED").deleted(false).sale(sale).build();
        org.sspd.servicemgmt.stockoptions.productserialoptions.model.ProductSerial serial =
                org.sspd.servicemgmt.stockoptions.productserialoptions.model.ProductSerial.builder()
                        .serialNumber("SN-1")
                        .status(org.sspd.servicemgmt.stockoptions.productserialoptions.enums.SerialStatus.Sold)
                        .product(product)
                        .build();
        when(returns.findByIdForUpdate(9)).thenReturn(Optional.of(existing));
        when(details.findAllBySaleReturnIn(List.of(existing))).thenReturn(List.of(
                SaleReturnDetail.builder().product(product).qty(1).serialNumber("SN-1").restock(true).build()));
        when(serials.findLockedBySerialNumber("SN-1")).thenReturn(Optional.of(serial));
        SaleRepository sales = mock(SaleRepository.class);
        when(sales.findLockedWithDetails(11)).thenReturn(Optional.of(sale));
        SaleReturnService service = construct(Map.of(
                SaleReturnRepository.class, returns,
                SaleReturnDetailRepository.class, details,
                SaleRepository.class, sales,
                org.sspd.servicemgmt.stockoptions.productserialoptions.repository.ProductSerialRepository.class, serials));

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> service.voidReturn(9, "mistake"));
        assertTrue(thrown.getMessage().toLowerCase().contains("serial"));
        assertEquals(org.sspd.servicemgmt.stockoptions.productserialoptions.enums.SerialStatus.Sold, serial.getStatus());
    }

    @Test
    void voidReturnReversesCashDrawerRefund() throws Exception {
        SaleReturnRepository returns = mock(SaleReturnRepository.class);
        SaleReturnDetailRepository details = mock(SaleReturnDetailRepository.class);
        org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.repository.PaymentTransactionRepository transactions =
                mock(org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.repository.PaymentTransactionRepository.class);
        org.sspd.servicemgmt.cashdraweroptions.service.CashDrawerService drawer =
                mock(org.sspd.servicemgmt.cashdraweroptions.service.CashDrawerService.class);
        AccountResolver accounts = mock(AccountResolver.class);
        SaleRepository sales = mock(SaleRepository.class);
        ChartOfAccount cash = account(41);
        when(accounts.cash()).thenReturn(cash);
        org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.model.PaymentMethod cashMethod =
                org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.model.PaymentMethod.builder()
                        .id(7).account(cash).methodName("Cash").build();
        org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.model.PaymentTransaction live =
                new org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.model.PaymentTransaction();
        live.setPaymentMethod(cashMethod);
        live.setAmount(new BigDecimal("50"));
        live.setReversed(false);
        org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.model.PaymentTransaction alreadyVoided =
                new org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.model.PaymentTransaction();
        alreadyVoided.setPaymentMethod(cashMethod);
        alreadyVoided.setAmount(new BigDecimal("80"));
        alreadyVoided.setReversed(true);
        Sale sale = Sale.builder().id(11).creditStatus(org.sspd.servicemgmt.saleoptions.model.CreditStatus.Not_Credit)
                .totalAmount(new BigDecimal("50")).netAmount(new BigDecimal("50"))
                .paidAmount(BigDecimal.ZERO).dueAmount(BigDecimal.ZERO).build();
        SaleReturn existing = SaleReturn.builder().id(9).status("COMPLETED").deleted(false)
                .sale(sale).totalReturnAmount(new BigDecimal("50")).refundAmount(new BigDecimal("50"))
                .returnCode("SR-9").build();
        when(returns.findByIdForUpdate(9)).thenReturn(Optional.of(existing));
        when(details.findAllBySaleReturnIn(List.of(existing))).thenReturn(List.of());
        when(transactions.findByReferenceIdAndReferenceType(9,
                org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.model.ReferenceType.Sale_Return))
                .thenReturn(List.of(live, alreadyVoided));
        when(returns.save(existing)).thenReturn(existing);
        when(sales.findLockedWithDetails(11)).thenReturn(Optional.of(sale));

        SaleReturnService service = construct(Map.of(
                SaleReturnRepository.class, returns,
                SaleReturnDetailRepository.class, details,
                org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.repository.PaymentTransactionRepository.class, transactions,
                org.sspd.servicemgmt.cashdraweroptions.service.CashDrawerService.class, drawer,
                AccountResolver.class, accounts,
                SaleRepository.class, sales));
        service.voidReturn(9, "mistake");

        org.mockito.Mockito.verify(drawer).recordCompensatingCashIn(
                new BigDecimal("50"),
                org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.model.ReferenceType.Sale_Return.name(),
                9, "Void sale return SR-9");
        assertTrue(Boolean.TRUE.equals(live.getReversed()));
    }

    @Test
    void creditSaleCashRefundDoesNotAlsoReduceReceivable() throws Exception {
        SaleRepository sales = mock(SaleRepository.class);
        Sale sale = Sale.builder()
                .id(11)
                .creditStatus(org.sspd.servicemgmt.saleoptions.model.CreditStatus.Active)
                .dueDate(java.time.LocalDate.now().plusDays(7))
                .totalAmount(new BigDecimal("100"))
                .netAmount(new BigDecimal("100"))
                .paidAmount(new BigDecimal("60"))
                .dueAmount(new BigDecimal("40"))
                .build();
        SaleReturnService service = construct(Map.of(SaleRepository.class, sales));

        invoke(service, "applySaleAdjustments",
                new Class<?>[]{Sale.class, BigDecimal.class, BigDecimal.class},
                sale, new BigDecimal("20"), new BigDecimal("20"));

        assertEquals(new BigDecimal("80"), sale.getNetAmount());
        assertEquals(new BigDecimal("40"), sale.getPaidAmount());
        assertEquals(new BigDecimal("40"), sale.getDueAmount());
    }

    @Test
    void creditSaleReturnWithoutRefundReducesReceivableOnly() throws Exception {
        SaleRepository sales = mock(SaleRepository.class);
        Sale sale = Sale.builder()
                .id(11)
                .creditStatus(org.sspd.servicemgmt.saleoptions.model.CreditStatus.Active)
                .dueDate(java.time.LocalDate.now().plusDays(7))
                .totalAmount(new BigDecimal("100"))
                .netAmount(new BigDecimal("100"))
                .paidAmount(new BigDecimal("60"))
                .dueAmount(new BigDecimal("40"))
                .build();
        SaleReturnService service = construct(Map.of(SaleRepository.class, sales));

        invoke(service, "applySaleAdjustments",
                new Class<?>[]{Sale.class, BigDecimal.class, BigDecimal.class},
                sale, new BigDecimal("20"), BigDecimal.ZERO);

        assertEquals(new BigDecimal("80"), sale.getNetAmount());
        assertEquals(new BigDecimal("60"), sale.getPaidAmount());
        assertEquals(new BigDecimal("20"), sale.getDueAmount());
    }

    @Test
    void fullyPaidCreditNoteReducesPaidAndVoidRestoresOriginalPaid() throws Exception {
        SaleRepository sales = mock(SaleRepository.class);
        Sale sale = Sale.builder()
                .id(11)
                .creditStatus(org.sspd.servicemgmt.saleoptions.model.CreditStatus.Not_Credit)
                .totalAmount(new BigDecimal("100"))
                .netAmount(new BigDecimal("100"))
                .paidAmount(new BigDecimal("100"))
                .dueAmount(BigDecimal.ZERO)
                .build();
        SaleReturnService service = construct(Map.of(SaleRepository.class, sales));

        Object leftover = invoke(service, "applySaleAdjustments",
                new Class<?>[]{Sale.class, BigDecimal.class, BigDecimal.class},
                sale, new BigDecimal("20"), BigDecimal.ZERO);
        assertEquals(new BigDecimal("20"), leftover);
        assertEquals(new BigDecimal("80"), sale.getNetAmount());
        assertEquals(new BigDecimal("80"), sale.getPaidAmount());
        assertEquals(BigDecimal.ZERO, sale.getDueAmount());

        invoke(service, "reverseSaleAdjustments",
                new Class<?>[]{Sale.class, BigDecimal.class, BigDecimal.class, BigDecimal.class},
                sale, new BigDecimal("20"), BigDecimal.ZERO, leftover);
        assertEquals(new BigDecimal("100"), sale.getNetAmount());
        assertEquals(new BigDecimal("100"), sale.getPaidAmount());
        assertEquals(BigDecimal.ZERO, sale.getDueAmount());
    }

    private SaleReturnDetailDTO line(int productId, int qty) {
        SaleReturnDetailDTO dto = new SaleReturnDetailDTO();
        dto.setProductId(productId);
        dto.setQty(qty);
        dto.setReasonId(1);
        return dto;
    }

    private SaleReturnService construct(Map<Class<?>, Object> overrides) throws Exception {
        Constructor<?> constructor = Arrays.stream(SaleReturnService.class.getConstructors())
                .max(java.util.Comparator.comparingInt(Constructor::getParameterCount)).orElseThrow();
        Object[] args = Arrays.stream(constructor.getParameterTypes())
                .map(type -> overrides.containsKey(type) ? overrides.get(type) : mock(type))
                .toArray();
        return (SaleReturnService) constructor.newInstance(args);
    }

    private Object invoke(Object target, String name, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private ChartOfAccount account(int id) {
        return ChartOfAccount.builder().id(id).build();
    }
}
