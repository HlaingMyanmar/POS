package org.sspd.servicemgmt.purchaseoptions.purchaseorderoptions;

import jakarta.persistence.LockModeType;
import jakarta.persistence.Version;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.sspd.servicemgmt.purchaseoptions.model.Purchase;
import org.sspd.servicemgmt.purchaseoptions.purchaseorderoptions.model.PurchaseOrder;
import org.sspd.servicemgmt.purchaseoptions.purchaseorderoptions.repository.PurchaseOrderRepository;
import org.sspd.servicemgmt.stockoptions.productoptions.model.Product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PurchaseOrderLockingTest {

    @Test
    void receiveLookupUsesPessimisticWriteLock() throws Exception {
        Lock lock = PurchaseOrderRepository.class
                .getMethod("findByIdForUpdate", Integer.class)
                .getAnnotation(Lock.class);

        assertNotNull(lock);
        assertEquals(LockModeType.PESSIMISTIC_WRITE, lock.value());
    }

    @Test
    void purchaseCancelLooksUpSupplierIdWithoutLoadingThePurchaseEntity() throws Exception {
        Query query = org.sspd.servicemgmt.purchaseoptions.repository.PurchaseRepository.class
                .getMethod("findSupplierIdById", Integer.class)
                .getAnnotation(Query.class);
        assertNotNull(query);
        assertTrue(query.value().contains("p.supplier.id"));
        assertFalse(query.value().toLowerCase().contains("select p from"));
    }

    @Test
    void mutablePurchaseEntitiesUseOptimisticVersioning() throws Exception {
        assertNotNull(Purchase.class.getDeclaredField("version").getAnnotation(Version.class));
        assertNotNull(PurchaseOrder.class.getDeclaredField("version").getAnnotation(Version.class));
        assertNotNull(Product.class.getDeclaredField("version").getAnnotation(Version.class));
    }
}
