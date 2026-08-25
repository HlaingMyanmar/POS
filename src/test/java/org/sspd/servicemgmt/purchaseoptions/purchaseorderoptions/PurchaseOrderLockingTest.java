package org.sspd.servicemgmt.purchaseoptions.purchaseorderoptions;

import jakarta.persistence.LockModeType;
import jakarta.persistence.Version;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;
import org.sspd.servicemgmt.purchaseoptions.model.Purchase;
import org.sspd.servicemgmt.purchaseoptions.purchaseorderoptions.model.PurchaseOrder;
import org.sspd.servicemgmt.purchaseoptions.purchaseorderoptions.repository.PurchaseOrderRepository;
import org.sspd.servicemgmt.stockoptions.productoptions.model.Product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
    void mutablePurchaseEntitiesUseOptimisticVersioning() throws Exception {
        assertNotNull(Purchase.class.getDeclaredField("version").getAnnotation(Version.class));
        assertNotNull(PurchaseOrder.class.getDeclaredField("version").getAnnotation(Version.class));
        assertNotNull(Product.class.getDeclaredField("version").getAnnotation(Version.class));
    }
}
