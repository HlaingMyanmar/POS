package org.sspd.servicemgmt.customerportaloptions.service;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.sspd.servicemgmt.customerportaloptions.repository.CustomerOrderRepository;
@Component @RequiredArgsConstructor @lombok.extern.slf4j.Slf4j
public class CustomerOrderReservationExpiry {
 private final CustomerOrderRepository orders;
 private final CustomerOrderPaymentService payments;
 @Scheduled(fixedDelay=30000) public void releaseExpired() {
  for(Integer id:orders.findExpired(java.time.LocalDateTime.now())) {
   try {payments.expire(id);} catch(RuntimeException e){log.warn("Reservation expiry failed for order {}",id,e);}
  }
 }
}
