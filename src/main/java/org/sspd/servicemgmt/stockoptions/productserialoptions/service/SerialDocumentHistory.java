package org.sspd.servicemgmt.stockoptions.productserialoptions.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;

@Component
public class SerialDocumentHistory {

    @PersistenceContext
    private EntityManager entityManager;

    public boolean exists(String serialNumber) {
        if (serialNumber == null || serialNumber.isBlank()) {
            return false;
        }
        String serial = serialNumber.trim();
        return existsExact("SaleDetail", "serialNumber", serial)
                || existsToken("SaleReturnDetail", "serialNumber", serial)
                || existsToken("ServiceJobPart", "serialNumbers", serial)
                || existsToken("StockAdjustment", "serialNumbers", serial)
                || existsExact("CustomerProductReturnLine", "serialNumber", serial)
                || existsExact("CustomerProductReturnLine", "replacementSerialNumber", serial)
                || existsToken("PurchaseReturnDetail", "serialNumber", serial);
    }

    private boolean existsExact(String entity, String field, String serial) {
        Long count = entityManager.createQuery(
                        "select count(e) from " + entity + " e where lower(e." + field + ") = lower(:serial)",
                        Long.class)
                .setParameter("serial", serial)
                .getSingleResult();
        return count != null && count > 0;
    }

    private boolean existsToken(String entity, String field, String serial) {
        Long count = entityManager.createQuery(
                        "select count(e) from " + entity + " e where e." + field + " is not null and ("
                                + "lower(e." + field + ") = lower(:serial) "
                                + "or lower(concat(',', function('replace', e." + field + ", ' ', ''), ',')) "
                                + "like concat('%,', lower(:serial), ',%'))",
                        Long.class)
                .setParameter("serial", serial)
                .getSingleResult();
        return count != null && count > 0;
    }
}
