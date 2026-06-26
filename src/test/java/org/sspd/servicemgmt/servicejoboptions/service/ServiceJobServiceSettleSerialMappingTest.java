package org.sspd.servicemgmt.servicejoboptions.service;

import org.junit.jupiter.api.Test;
import org.sspd.servicemgmt.accountingoptions.coaoptions.AccountResolver;
import org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.repository.PaymentMethodRepository;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.repository.PaymentTransactionRepository;
import org.sspd.servicemgmt.customeroptions.model.Customer;
import org.sspd.servicemgmt.customeroptions.repository.CustomerRepository;
import org.sspd.servicemgmt.journaloption.entry.service.JournalWriter;
import org.sspd.servicemgmt.saleoptions.dto.SaleDTO;
import org.sspd.servicemgmt.saleoptions.service.SaleService;
import org.sspd.servicemgmt.servicejoboptions.dto.SettleDTO;
import org.sspd.servicemgmt.servicejoboptions.model.ServiceJob;
import org.sspd.servicemgmt.servicejoboptions.model.ServiceJobPart;
import org.sspd.servicemgmt.servicejoboptions.repository.ServiceJobRepository;
import org.sspd.servicemgmt.serviceoptions.repository.ServiceItemRepository;
import org.sspd.servicemgmt.staffoptions.repository.StaffRepository;
import org.sspd.servicemgmt.stockoptions.productoptions.model.Product;
import org.sspd.servicemgmt.stockoptions.productoptions.repository.ProductRepository;
import org.sspd.servicemgmt.stockoptions.productserialoptions.repository.ProductSerialRepository;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class ServiceJobServiceSettleSerialMappingTest {

    @Test
    void createSaleFromServiceJobMapsSerialNumbersForSerialTrackedProducts() throws Exception {
        ServiceJobService service = newService();
        Product serialProduct = Product.builder()
            .id(101)
            .name("Kistom SSD 120 gb")
            .hasSerial(true)
            .build();
        ServiceJobPart part = ServiceJobPart.builder()
            .product(serialProduct)
            .qty(2)
            .unitPrice(new BigDecimal("15000"))
            .subtotal(new BigDecimal("30000"))
            .serialNumbers("SN-001,SN-002")
            .build();
        ServiceJob job = ServiceJob.builder()
            .customer(Customer.builder().id(7).build())
            .productParts(List.of(part))
            .build();

        SaleDTO sale = invokeCreateSaleFromServiceJob(service, job, new SettleDTO(), new BigDecimal("30000"));

        assertNotNull(sale);
        assertNotNull(sale.getDetails());
        assertEquals(1, sale.getDetails().size());
        assertEquals(List.of("SN-001", "SN-002"), sale.getDetails().get(0).getSerialNumbers());
        assertEquals(new BigDecimal("30000"), sale.getPaidAmount());
    }

    @Test
    void createSaleFromServiceJobThrowsClearErrorWhenSerialsMissingForSerialTrackedProduct() {
        ServiceJobService service = newService();
        Product serialProduct = Product.builder()
            .id(101)
            .name("Kistom SSD 120 gb")
            .hasSerial(true)
            .build();
        ServiceJobPart part = ServiceJobPart.builder()
            .product(serialProduct)
            .qty(1)
            .unitPrice(new BigDecimal("15000"))
            .subtotal(new BigDecimal("15000"))
            .serialNumbers(null)
            .build();
        ServiceJob job = ServiceJob.builder()
            .customer(Customer.builder().id(7).build())
            .productParts(List.of(part))
            .build();

        RuntimeException thrown = assertThrows(RuntimeException.class,
            () -> invokeCreateSaleFromServiceJob(service, job, new SettleDTO(), new BigDecimal("15000")));
        assertEquals("Serial numbers are required for product: Kistom SSD 120 gb", thrown.getMessage());
    }

    private static SaleDTO invokeCreateSaleFromServiceJob(ServiceJobService service, ServiceJob job,
                                                          SettleDTO settleDTO, BigDecimal totalAmount) throws Exception {
        Method method = ServiceJobService.class.getDeclaredMethod(
            "createSaleFromServiceJob", ServiceJob.class, SettleDTO.class, BigDecimal.class);
        method.setAccessible(true);
        try {
            return (SaleDTO) method.invoke(service, job, settleDTO, totalAmount);
        } catch (InvocationTargetException ex) {
            if (ex.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw ex;
        }
    }

    private static ServiceJobService newService() {
        return new ServiceJobService(
            mock(ServiceJobRepository.class),
            mock(CustomerRepository.class),
            mock(StaffRepository.class),
            mock(ServiceItemRepository.class),
            mock(ProductRepository.class),
            mock(ProductSerialRepository.class),
            mock(PaymentMethodRepository.class),
            mock(JournalWriter.class),
            mock(PaymentTransactionRepository.class),
            mock(AccountResolver.class),
            mock(SaleService.class),
            mock(org.sspd.servicemgmt.creditoptions.service.CreditService.class),
            mock(org.springframework.messaging.simp.SimpMessagingTemplate.class),
            mock(org.sspd.servicemgmt.bookingoptions.repository.BookingRepository.class)
        );
    }
}
