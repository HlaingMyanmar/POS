package org.sspd.servicemgmt.customerportaloptions.service;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.*;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerPortalNotificationDTO;
@Aspect @Component @RequiredArgsConstructor
public class CustomerPushEventBridge {
 private final CustomerFcmService push;
 @AfterReturning(value="execution(* org.sspd.servicemgmt.dataevent.DataEventPublisher.publishToUser(..)) && args(username,destination,payload)",argNames="username,destination,payload")
 public void forward(String username,String destination,Object payload){
  if(!"/topic/customer-orders".equals(destination)||!(payload instanceof CustomerPortalNotificationDTO notice)||username==null||!username.startsWith("customer:id:"))return;
  Runnable send=()->{try{push.sendAsync(Integer.valueOf(username.substring(12)),notice);}catch(NumberFormatException ignored){}};
  if(TransactionSynchronizationManager.isSynchronizationActive())TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCommit(){send.run();}});else send.run();
 }
}
