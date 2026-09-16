package org.sspd.servicemgmt.customerportaloptions.service;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.sspd.servicemgmt.customerportaloptions.model.CustomerOrder;
import org.sspd.servicemgmt.stockoptions.productoptions.model.Product;
import org.sspd.servicemgmt.stockoptions.productserialoptions.model.ProductSerial;
import org.sspd.servicemgmt.stockoptions.productserialoptions.enums.SerialStatus;
import java.util.*;
@Service @RequiredArgsConstructor
public class CustomerStockReservationService {
 private final EntityManager em;
 public Map<Integer,Product> lockProducts(Collection<Integer> ids) {
  em.flush();
  Map<Integer,Product> result = new TreeMap<>();
  ids.stream().distinct().sorted().forEach(id -> {
   Product p=em.find(Product.class,id,LockModeType.PESSIMISTIC_WRITE);
   if(p==null) throw new IllegalArgumentException("Product not found: " + id);
   em.refresh(p,LockModeType.PESSIMISTIC_WRITE);
   result.put(id,p);
  });
  return result;
 }
 public int reserved(Product p) { return p.getCustomerReservedQty()==null?0:p.getCustomerReservedQty(); }
 /** Available for a walk-in/POS sale after subtracting active customer-order holds. Serials are locked so two cashiers cannot take reserved units. */
 public int availableLocked(Product p) {
  int physical;
  if(Boolean.TRUE.equals(p.getHasSerial())) {
   physical=em.createQuery("select s from ProductSerial s where s.product.id=:id and s.status=:status", ProductSerial.class)
    .setParameter("id",p.getId()).setParameter("status",SerialStatus.Available)
    .setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList().size();
  } else physical=(p.getStockQty()==null?0:p.getStockQty())-(p.getQuarantinedQty()==null?0:p.getQuarantinedQty());
  return Math.max(0, physical-reserved(p));
 }
 public void assertSellable(Product p, int qty) {
  if(availableLocked(p)<qty) throw new IllegalStateException("Insufficient available stock (customer orders reserved): " + p.getName());
 }
 public Map<Integer,Integer> quantities(CustomerOrder order) {
  Map<Integer,Integer> amounts=new TreeMap<>();
  order.getLines().forEach(l -> {
   if(l.getQty()==null || l.getQty()<=0)throw new IllegalArgumentException("Invalid order quantity");
   amounts.merge(l.getProduct().getId(),l.getQty(),Math::addExact);
  });
  return amounts;
 }
 public void reserve(CustomerOrder order) {
  if(order.isReservationActive())return;
  var quantities=quantities(order); var products=lockProducts(quantities.keySet());
  quantities.forEach((id,qty)-> {
   var p=products.get(id);
   if(Boolean.TRUE.equals(p.getArchived()) || availableLocked(p)<qty)throw new IllegalStateException("Insufficient available stock: " + p.getName());
   p.setCustomerReservedQty(Math.addExact(reserved(p),qty));
  });
  order.setReservationActive(true);
 }
 public void release(CustomerOrder order) {
  if(!order.isReservationActive())return;
  var quantities=quantities(order); var products=lockProducts(quantities.keySet());
  quantities.forEach((id,qty)-> {
   var p=products.get(id);
   if(reserved(p)<qty)throw new IllegalStateException("Reservation count mismatch");
   p.setCustomerReservedQty(reserved(p)-qty);
  });
  order.setReservationActive(false);
 }
}
