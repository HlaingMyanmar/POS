package org.sspd.servicemgmt.customerportaloptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.multipart.MultipartFile;
import org.sspd.servicemgmt.customerportaloptions.model.*;
import org.sspd.servicemgmt.customerportaloptions.repository.*;
import org.sspd.servicemgmt.customerportaloptions.dto.*;
import org.sspd.servicemgmt.customerportaloptions.support.CustomerPortalAuth;
import org.sspd.servicemgmt.customerportaloptions.support.CustomerOrderDeliveryRules;
import org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.repository.PaymentMethodRepository;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.model.PaymentTransaction;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.model.ReferenceType;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.repository.PaymentTransactionRepository;
import org.sspd.servicemgmt.accountingoptions.coaoptions.AccountResolver;
import org.sspd.servicemgmt.journaloption.detail.dto.JournalDetailDTO;
import org.sspd.servicemgmt.journaloption.entry.dto.JournalEntryDTO;
import org.sspd.servicemgmt.journaloption.entry.service.JournalWriter;
import org.sspd.servicemgmt.dataevent.DataEventPublisher;
import org.sspd.servicemgmt.saleoptions.service.SaleService;
import org.sspd.servicemgmt.saleoptions.dto.SaleDTO;
import org.sspd.servicemgmt.saleoptions.saledetails.dto.SaleDetailDTO;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service @RequiredArgsConstructor
public class CustomerOrderPaymentService {
 private final CustomerOrderRepository orders;
 private final CustomerOrderPaymentProofRepository proofs;
 private final CustomerStockReservationService stock;
 private final PaymentMethodRepository methods;
 private final PaymentTransactionRepository paymentTransactions;
 private final DataEventPublisher events;
 private final SaleService sales;
 private final org.sspd.servicemgmt.companysettingoptions.service.CompanySettingsService companySettings;
 private final org.springframework.jdbc.core.JdbcTemplate shippingJdbc;
 private final CustomerPromoService promos;
 private final CustomerLoyaltyService loyalty;
 private final AccountResolver accounts;
 private final JournalWriter journalWriter;
 private final DeliveryPricingService deliveryPricing;

 public record ShippingDecision(BigDecimal amount,String handler,String reason,Integer version,Boolean accept,
                              Boolean fullPaymentRequired, java.time.LocalDateTime scheduledAt) {}
 private void shippingReady(CustomerOrder o) {
  if("DELIVERY".equals(o.getOrderType()) && o.getShippingState()!=null
    && !Set.of("LEGACY","ACCEPTED").contains(o.getShippingState()))
   throw new IllegalStateException("Customer must accept the final delivery quote before payment or reservation");
 }
 private void editableShipping(CustomerOrder o) {
  if(o.getStatus()!=CustomerOrderStatus.PENDING || o.isReservationActive() || o.getCompletedSaleId()!=null
    || o.getLatestProofId()!=null || !"NONE".equals(o.getPaymentState()))
   throw new IllegalStateException("Delivery pricing is locked once the order enters payment");
  if(!"DELIVERY".equals(o.getOrderType()))throw new IllegalStateException("Not a delivery order");
 }
 private void shippingAudit(CustomerOrder o,String action) {
  shippingJdbc.update("insert into customer_shipping_audit(order_id,quote_version,action,actor,details,created_at) values (?,?,?,?,?,?)",
   o.getId(),o.getShippingVersion(),action,actor(),
   "handler="+o.getDeliveryHandler()+"; fullPayment="+o.isFullPaymentRequired()+"; amount="+o.getDeliveryCharge()+"; "+o.getShippingReason(),LocalDateTime.now());
 }
 @Transactional public void quoteShipping(Integer id,ShippingDecision request) {
  CustomerOrder o=locked(id);editableShipping(o);
  if(request==null || request.version()==null || !request.version().equals(o.getShippingVersion()))
   throw new IllegalStateException("Quote changed; refresh the order");
  note(request.reason());
  if(request.handler()==null || !Set.of("OWN","HANDOFF").contains(request.handler()))throw new IllegalArgumentException("Choose delivery handler");
  if(request.amount()==null || request.amount().signum()<0 || request.amount().scale()>2 || request.amount().precision()>15)
   throw new IllegalArgumentException("Invalid delivery charge");
  if("HANDOFF".equals(request.handler()) && request.amount().signum()!=0)
   throw new IllegalArgumentException("အပြင်ပို့ အပ်ရင် ဆိုင်ပို့ခ ၀ ဖြစ်ရမည် — ဘောင်ချာတွင် ပို့ခ မထည့်ပါ");
  if(request.scheduledAt()==null) throw new IllegalArgumentException("ပို့မည့် ရက်နှင့် အချိန် အတည်ပြုပါ");
  deliveryPricing.validateRequestedAt(request.scheduledAt());
  o.setDeliveryScheduledAt(request.scheduledAt());
  o.setDeliveryHandler(request.handler());
  o.setFullPaymentRequired("HANDOFF".equals(request.handler()) || Boolean.TRUE.equals(request.fullPaymentRequired()));
  o.setDeliveryCharge(request.amount());o.setQuotedDeliveryCharge(request.amount());
  o.setShippingReason(request.reason().trim());
  o.setShippingVersion(o.getShippingVersion()+1);
  o.setShippingState("QUOTED");
  shippingAudit(o,"QUOTED");
  boolean sameTime = o.getRequestedDeliveryAt()!=null && request.scheduledAt()!=null
    && o.getRequestedDeliveryAt().truncatedTo(ChronoUnit.MINUTES)
        .equals(request.scheduledAt().truncatedTo(ChronoUnit.MINUTES));
  changed(o, "HANDOFF".equals(request.handler())
    ? (sameTime
      ? "ဆိုင်က တောင်းဆိုချိန် အဆင်ပြေသည်။ အပြင်ပို့ အပ်မည် — ပို့ခ ၀။ လက်ခံမလား ညှိနှိုင်းအုံးမလား ရွေးပါ။"
      : "ဆိုင်က လုပ်ငန်းအဆင်ပြေသည့် ပို့ချိန် ပို့လိုက်ပါသည်။ အပြင်ပို့ အပ်မည် — ပို့ခ ၀။ လက်ခံမလား ညှိနှိုင်းအုံးမလား ရွေးပါ။")
    : (sameTime
      ? "ဆိုင်က တောင်းဆိုချိန် အဆင်ပြေသည်။ ပို့ခကို ကြည့်ပြီး လက်ခံမလား ညှိနှိုင်းအုံးမလား ရွေးပါ။"
      : "ဆိုင်က လုပ်ငန်းအဆင်ပြေသည့် ပို့ချိန် ပို့လိုက်ပါသည်။ လက်ခံမလား ညှိနှိုင်းအုံးမလား ရွေးပါ။"));
 }
 @Transactional public void decideShipping(Integer id,ShippingDecision request) {
  CustomerOrder o=locked(id);
  if(!o.getCustomer().getId().equals(CustomerPortalAuth.require().getCustomerId()))
   throw new org.springframework.security.access.AccessDeniedException("Not your order");
  editableShipping(o);
  if(request==null || request.version()==null || !request.version().equals(o.getShippingVersion()) || request.accept()==null)
   throw new IllegalStateException("Quote changed; refresh and review the latest quote");
  if("ACCEPTED".equals(o.getShippingState()) && request.accept())return;
  if(!"QUOTED".equals(o.getShippingState()))throw new IllegalStateException("No delivery quote to accept");
  if(request.accept()) {
   o.setShippingState("ACCEPTED");
   shippingAudit(o,"ACCEPTED");
   changed(o,CustomerOrderDeliveryRules.requiresFullTransfer(o)
    ? "Customer လက်ခံပြီးပါပြီ။ ဒီ order အတွက် ငွေအပြည့်အကြေ ကြိုလွှဲရပါမယ်။"
    : "Customer လက်ခံပြီးပါပြီ။ ငွေပေးချေနည်း ရွေးပါ — ငွေအပြည့်လွှဲ သို့မဟုတ် လက်ခံချိန်ရှင်း (စရံကြို)။");
  } else {
   String customerNote = request.reason() == null ? "" : request.reason().trim();
   if(request.scheduledAt()!=null) {
    if(request.scheduledAt().isBefore(LocalDateTime.now().plusMinutes(15)))
     throw new IllegalArgumentException("ပြန်ညှိမည့် ပို့ချိန်သည် အနည်းဆုံး ၁၅ မိနစ် ကြာမှ ဖြစ်ရမည်");
    deliveryPricing.validateRequestedAt(request.scheduledAt());
    o.setRequestedDeliveryAt(request.scheduledAt());
   }
   if(!customerNote.isBlank()) {
    if(customerNote.length()>1000) throw new IllegalArgumentException("မှတ်ချက် အလွန်ရှည်နေသည်");
    o.setShippingReason(customerNote);
   }
   o.setDeliveryScheduledAt(null);
   o.setShippingState("AWAITING_SHOP");
   o.setShippingVersion(o.getShippingVersion()+1);
   o.setShippingRenegotiated(true);
   shippingJdbc.update(
     "insert into customer_shipping_audit(order_id,quote_version,action,actor,details,created_at) values (?,?,?,?,?,?)",
     o.getId(),o.getShippingVersion(),"DECLINED",actor(),
     "requestedAt="+o.getRequestedDeliveryAt()+"; note="+customerNote,LocalDateTime.now());
   changed(o, request.scheduledAt()!=null
     ? "Customer က ပို့ချိန် အသစ် တောင်းဆိုပါသည်။"+(customerNote.isBlank()?"":" — "+customerNote)
     : "Customer က အချိန် ထပ်ညှိရန် တောင်းဆိုပါသည်။"+(customerNote.isBlank()?"":" — "+customerNote));
  }
 }

 @Transactional public void customerCancel(Integer id) {
  CustomerOrder o=locked(id);
  if(!o.getCustomer().getId().equals(CustomerPortalAuth.require().getCustomerId()))
   throw new org.springframework.security.access.AccessDeniedException("Not your order");
  if(o.getStatus()==CustomerOrderStatus.CANCELLED)return;
  if(o.getCompletedSaleId()!=null
    || Set.of("PAID","DEPOSIT_PAID","REVIEW","LATE_REVIEW","PROOF_SUBMITTED","CHECKING","REFUND_REQUIRED","REFUNDED","FORFEITED").contains(o.getPaymentState()))
   throw new IllegalStateException("ငွေလွှဲပြီး/စစ်ဆေးဆဲ အော်ဒါကို App မှ ပယ်ဖျက်မရပါ — ဆိုင်နှင့် ဆက်သွယ်ပါ");
  stock.release(o);
  o.setStatus(CustomerOrderStatus.CANCELLED);
  releasePromo(o);
  changed(o,"Customer က အော်ဒါကို ပယ်ဖျက်လိုက်ပါသည်။");
 }

 @Transactional(readOnly = true)
 public List<CustomerPortalOrderDTO.TimelineEvent> shippingTimeline(Integer orderId) {
  return shippingJdbc.query(
    "select quote_version, action, actor, details, created_at from customer_shipping_audit where order_id=? order by id desc limit 40",
    (rs, i) -> {
     CustomerPortalOrderDTO.TimelineEvent e = new CustomerPortalOrderDTO.TimelineEvent();
     e.setVersion(rs.getInt("quote_version"));
     e.setAction(rs.getString("action"));
     e.setActor(rs.getString("actor"));
     e.setDetails(rs.getString("details"));
     e.setAt(rs.getTimestamp("created_at").toLocalDateTime());
     return e;
    },
    orderId);
 }

 @Transactional public void choosePayment(Integer id, String choice) {
  CustomerOrder o=locked(id);
  if(!o.getCustomer().getId().equals(CustomerPortalAuth.require().getCustomerId()))
   throw new org.springframework.security.access.AccessDeniedException("Not your order");
  shippingReady(o);
  if(o.getStatus()==CustomerOrderStatus.CANCELLED || o.getCompletedSaleId()!=null)
   throw new IllegalStateException("Order is closed");
  if(!"NONE".equals(o.getPaymentState())) throw new IllegalStateException("Payment already started");
  if("DELIVERY".equalsIgnoreCase(o.getOrderType()) && !"PENDING".equalsIgnoreCase(o.getPaymentChoice()))
   throw new IllegalStateException("Payment method already chosen");
  String next = choice==null?"":choice.trim().toUpperCase();
  if(!Set.of("TRANSFER","PAY_ON_COLLECTION").contains(next)) throw new IllegalArgumentException("Invalid payment choice");
  if(CustomerOrderDeliveryRules.requiresFullTransfer(o) && !"TRANSFER".equals(next))
   throw new IllegalArgumentException("HANDOFF".equalsIgnoreCase(o.getDeliveryHandler())
    ? "အခြား delivery နဲ့ ပို့မယ့် order ဖြစ်လို့ စရံပေးပြီး ကျန်ငွေကို ပစ္စည်းရောက်မှ ရှင်းလို့မရပါ။ ငွေအပြည့်အကြေ ကြိုလွှဲရပါမယ်။"
    : "ဆိုင်က ဒီ order ကို ငွေအပြည့် ကြိုတောင်းထားလို့ စရံပေးပြီး ကျန်ငွေကို ပစ္စည်းရောက်မှ ရှင်းလို့မရပါ။ ငွေအပြည့်အကြေ ကြိုလွှဲရပါမယ်။");
  if("PICKUP".equalsIgnoreCase(o.getOrderType())) next="TRANSFER";
  o.setPaymentChoice(next);
  if("PAY_ON_COLLECTION".equals(next) || "PICKUP".equalsIgnoreCase(o.getOrderType())) applyDeposit(o);
  else { o.setDepositPercent(null); o.setDepositAmount(null); }
  boolean deliveryAccepted = "DELIVERY".equalsIgnoreCase(o.getOrderType()) && "ACCEPTED".equals(o.getShippingState());
  if(deliveryAccepted) openHoldAfterChoice(o);
  changed(o,"PAY_ON_COLLECTION".equals(next)
    ?"လက်ခံချိန် ရှင်းမည် — စရံကြိုလွှဲရပါမည်။ စရံလွှဲပြီးမှ ပယ်ဖျက်ပါက စရံငွေ ဆုံးရှုံးမည်။"
    :(deliveryAccepted
      ?"ငွေအပြည့် ဘဏ် / Wallet လွှဲ ရွေးလိုက်ပါသည်။ Channel ရွေးပြီး လွှဲပါ။"
      :"ဘဏ် / Wallet ငွေလွှဲ ရွေးလိုက်ပါသည်။"));
 }

 /** If quote is accepted and the customer already chose payment, open the hold if it never started. */
 @Transactional(propagation = Propagation.REQUIRES_NEW)
 public void ensureTransferOpened(Integer id) {
  CustomerOrder o=locked(id);
  if(!"DELIVERY".equalsIgnoreCase(o.getOrderType()==null?"":o.getOrderType())) return;
  if(!"ACCEPTED".equals(o.getShippingState())) return;
  if(o.getStatus()==CustomerOrderStatus.CANCELLED || o.getCompletedSaleId()!=null) return;
  if(!"NONE".equals(o.getPaymentState())) return;
  if("PENDING".equalsIgnoreCase(o.getPaymentChoice()) || o.getPaymentChoice()==null) return;
  if(!Set.of("TRANSFER","PAY_ON_COLLECTION").contains(o.getPaymentChoice().toUpperCase(Locale.ROOT))) return;
  openHoldAfterChoice(o);
  changed(o, transferNow(o)
    ? "ပို့ချိန် လက်ခံပြီးပါပြီ။ Channel ရွေးပြီး ငွေလွှဲပါ။"
    : "ပို့ချိန် လက်ခံပြီးပါပြီ။ လက်ခံချိန် ငွေရှင်းပါ။");
 }

 private void openHoldAfterChoice(CustomerOrder o) {
  if(o.isReservationActive() || !"NONE".equals(o.getPaymentState())) return;
  if(CustomerOrderDeliveryRules.requiresFullTransfer(o)) {
   o.setPaymentChoice("TRANSFER");
   o.setDepositPercent(null);
   o.setDepositAmount(null);
  }
  if(total(o).signum()<=0) throw new IllegalStateException("Order total must be positive");
  boolean transfer=transferNow(o);
  o.setPaymentMethodId(null);
  o.setPaymentInstructions(transfer
    ? (hasDeposit(o)
      ? "ပို့ချိန် လက်ခံပြီးပါပြီ။ စရံကြိုလွှဲပါ။ ကျန်ငွေ ပို့ချိန် ရှင်းပါ။"
      : "ပို့ချိန် လက်ခံပြီးပါပြီ။ Channel ရွေးပြီး ကျသင့်ငွေ (ပို့ခ အပါ) လွှဲပါ။")
    : null);
  stock.reserve(o);
  o.setStatus(CustomerOrderStatus.CONFIRMED);
  o.setReservationExpiresAt(LocalDateTime.now().plusMinutes(transfer?15:1440));
  o.setPaymentState(transfer?"AWAITING_PAYMENT":"AWAITING_COLLECTION");
  o.setPaymentReviewNote(null);
 }

 private void applyDeposit(CustomerOrder o) {
  var settings = companySettings.getSettings();
  BigDecimal pct = settings.getPickupDepositPercent()!=null ? settings.getPickupDepositPercent() : new BigDecimal("30.00");
  if(pct.compareTo(BigDecimal.ONE)<0 || pct.compareTo(new BigDecimal("100"))>0)
   throw new IllegalStateException("စရံရာခိုင်နှုန်း မှားနေသည်။ Company Settings မှ ပြင်ပါ");
  pct = pct.setScale(2, RoundingMode.HALF_UP);
  BigDecimal items = zero(o.getItemsTotal());
  BigDecimal deposit = items.multiply(pct).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
  if(deposit.signum()<=0 && items.signum()>0) deposit = items.min(new BigDecimal("0.01"));
  o.setDepositPercent(pct);
  o.setDepositAmount(deposit);
 }


 private CustomerOrder locked(Integer id) { return orders.findLocked(id).orElseThrow(()->new IllegalArgumentException("Order not found")); }
 private String actor() { var a=SecurityContextHolder.getContext().getAuthentication();return a==null?"system":a.getName(); }
 private BigDecimal zero(BigDecimal value) { return value==null?BigDecimal.ZERO:value; }
 public BigDecimal total(CustomerOrder o) { return o.getLines().stream().map(l->zero(l.getSubtotal())).reduce(BigDecimal.ZERO,BigDecimal::add).add(zero(o.getDeliveryCharge())); }
 private boolean hasDeposit(CustomerOrder o) {
  return o.getDepositAmount()!=null && o.getDepositAmount().signum()>0;
 }
 /** Deposit or full billed total must be transferred now (collection remainder is later). */
 private boolean transferNow(CustomerOrder o) {
  return hasDeposit(o) || !"PAY_ON_COLLECTION".equals(o.getPaymentChoice());
 }
 /** Amount the customer must transfer now (deposit when set, otherwise full billed total). */
 public BigDecimal expectedTransfer(CustomerOrder o) {
  if (hasDeposit(o)) return o.getDepositAmount().setScale(2, RoundingMode.HALF_UP);
  return total(o);
 }
 public BigDecimal remainingDue(CustomerOrder o) {
  if (!hasDeposit(o)) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
  return total(o).subtract(o.getDepositAmount()).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
 }
 private void method(Integer id) { if(id==null || !methods.findById(id).map(m->m.isActive()).orElse(false))throw new IllegalArgumentException("Choose an active payment method"); }
 private boolean isCash(org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.model.PaymentMethod method) {
  return method != null && method.getMethodName() != null
   && method.getMethodName().trim().equalsIgnoreCase("CASH");
 }
 private org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.model.PaymentMethod requireCollectionMethod(Integer id) {
  if(id==null) throw new IllegalArgumentException("Choose a remainder payment method");
  var m=methods.findById(id).orElseThrow(()->new IllegalArgumentException("Payment method not found"));
  if(!m.isActive() || Boolean.FALSE.equals(m.getShowOnCustomerApp()))
   throw new IllegalArgumentException("Payment method is not available");
  if(!isCash(m)) {
   String name=m.getPayeeName()==null?"":m.getPayeeName().trim();
   String account=m.getPayeeAccountNo()==null?"":m.getPayeeAccountNo().trim();
   if(name.isBlank() || account.isBlank())
    throw new IllegalStateException("Shop has not set payee details for this online method");
  }
  return m;
 }
 private String buildPayeeInstructions(Integer paymentMethodId) {
  var m = methods.findById(paymentMethodId).orElse(null);
  if (m == null) return null;
  String name = m.getPayeeName() == null ? "" : m.getPayeeName().trim();
  String account = m.getPayeeAccountNo() == null ? "" : m.getPayeeAccountNo().trim();
  if (name.isBlank() || account.isBlank()) return null;
  StringBuilder sb = new StringBuilder();
  sb.append(m.getMethodName()).append(" · ").append(name).append(" · ").append(account);
  sb.append('\n').append("အကောင့်အမည်: ").append(name);
  sb.append('\n').append("အကောင့်နံပါတ်: ").append(account);
  if (m.getPayeeHint() != null && !m.getPayeeHint().isBlank()) {
   sb.append('\n').append(m.getPayeeHint().trim());
  }
  return sb.toString();
 }
 private void note(String value) { if(value==null || value.isBlank() || value.length()>1000)throw new IllegalArgumentException("Review reason / refund reference is required (max 1000 characters)"); }
 private boolean waiting(CustomerOrder o) {return Set.of("AWAITING_PAYMENT","AWAITING_COLLECTION").contains(o.getPaymentState());}
 private boolean overdue(CustomerOrder o) {return waiting(o) && o.getReservationExpiresAt()!=null && !LocalDateTime.now().isBefore(o.getReservationExpiresAt());}
 private void expireHeld(CustomerOrder o) {
  if(!overdue(o)) return;
  String prior = o.getPaymentState();
  stock.release(o);
  o.setPaymentState("EXPIRED");
  if("AWAITING_PAYMENT".equals(prior)) {
   o.setStatus(CustomerOrderStatus.CANCELLED);
   releasePromo(o);
   changed(o,"ငွေလွှဲရန် အချိန်ကုန်ပါပြီ။ ပစ္စည်းဖယ်ထားမှု ပြန်လွှတ်ပြီးပါပြီ။ ငွေလွှဲပြီးသားဆို အထောက်အထားတင်ပါ။ မလွှဲရသေးရင် ပြန်မှာယူပါ။");
  } else {
   changed(o,"ငွေပေးချေရန် အချိန်ကုန်သွားပါပြီ။ ငွေမလွှဲမီ ဆိုင်ကို ပြန်ဆက်သွယ်ပါ။");
  }
 }
 private boolean lateTransferOpen(CustomerOrder o) {
  return "EXPIRED".equals(o.getPaymentState());
 }
 @Transactional public void expire(Integer id) { expireHeld(locked(id)); }

 @Transactional public void reserve(Integer id, OrderPaymentRequest request) {
  CustomerOrder o=locked(id);
  if(o.getStatus()==CustomerOrderStatus.CANCELLED || o.getCompletedSaleId()!=null)throw new IllegalStateException("Order is closed");
  expireHeld(o);
  if(o.getStatus()==CustomerOrderStatus.CANCELLED)throw new IllegalStateException("Order is closed");
  if(o.isReservationActive())return;
  if(!Set.of("NONE","EXPIRED","REJECTED").contains(o.getPaymentState()))throw new IllegalStateException("Review the submitted payment before reserving again");
  shippingReady(o);
  if("PENDING".equalsIgnoreCase(o.getPaymentChoice()))
   throw new IllegalStateException("Customer must choose a payment method first");
  applyDeliveryHandler(o, request);
  if(CustomerOrderDeliveryRules.requiresFullTransfer(o)) {
   o.setPaymentChoice("TRANSFER");
   o.setDepositPercent(null);
   o.setDepositAmount(null);
  }
  boolean transfer=transferNow(o);
  int minutes=request.getHoldMinutes()==null?(transfer?15:1440):request.getHoldMinutes();
  if(minutes<1 || minutes>(transfer?60:1440))throw new IllegalArgumentException("Invalid reservation duration");
  if(total(o).signum()<=0)throw new IllegalStateException("Order total must be positive");
  // Transfer: customer picks KBZ/Wave/etc. themselves. Collection: shop may set ledger channel now or at fulfill.
  if(!transfer) {
   if(request.getPaymentMethodId()!=null) method(request.getPaymentMethodId());
   o.setPaymentMethodId(request.getPaymentMethodId());
   o.setPaymentInstructions(null);
  } else {
   o.setPaymentMethodId(null);
   o.setPaymentInstructions("ဆိုင်မှ order လက်ခံပြီးပါပြီ။ သင်လွှဲမည့် Channel (KBZPay / WavePay စသည်) ကို app ထဲ ရွေးပြီး ငွေလွှဲအထောက်အထား တင်ပါ။");
  }
  stock.reserve(o);
  o.setStatus(CustomerOrderStatus.CONFIRMED);
  o.setReservationExpiresAt(LocalDateTime.now().plusMinutes(minutes));
  o.setPaymentState(transfer?"AWAITING_PAYMENT":"AWAITING_COLLECTION");
  o.setPaymentReviewNote(null);
  String handlerNote = "HANDOFF".equals(o.getDeliveryHandler())
    ? " အပြင်ပို့ အပ်မည် — ဆိုင်ဘောင်ချာတွင် ပို့ခ မထည့်ပါ။"
    : ("OWN".equals(o.getDeliveryHandler()) ? " ဆိုင်ကပို့မည် — ပို့ခကို ဘောင်ချာထည့်ပါမည်။" : "");
  changed(o, (transfer
   ? (hasDeposit(o)
     ? "အော်ဒါကို လက်ခံပြီး ပစ္စည်းဖယ်ထားပါပြီ။ စရံကြိုလွှဲပါ။ ကျန်ငွေ လက်ခံချိန် ရှင်းပါ။ စရံလွှဲပြီးမှ ပယ်ဖျက်ပါက စရံငွေ ဆုံးရှုံးမည်။"
     : "အော်ဒါကို လက်ခံပြီး ပစ္စည်းဖယ်ထားပါပြီ။ Customer က Channel ရွေးပြီး ငွေလွှဲပါ။")
   : "အော်ဒါကို လက်ခံပြီး ပစ္စည်းဖယ်ထားပါပြီ။ လက်ခံချိန် ငွေရှင်းပါ။") + handlerNote);
 }

 /** Customer chooses which Payment Channel to transfer to (payee comes from master data). */
 @Transactional public void chooseChannel(Integer id, Integer paymentMethodId) {
  CustomerOrder o=locked(id);
  if(!o.getCustomer().getId().equals(CustomerPortalAuth.require().getCustomerId()))
   throw new org.springframework.security.access.AccessDeniedException("Not your order");
  shippingReady(o);
  boolean remainderStage="DEPOSIT_PAID".equals(o.getPaymentState())
   && CustomerOrderDeliveryRules.canCollectRemainder(o) && remainingDue(o).signum()>0;
  if(remainderStage) {
   var m=requireCollectionMethod(paymentMethodId);
   if(CustomerOrderDeliveryRules.requiresFullTransfer(o) && isCash(m))
    throw new IllegalArgumentException("အခြား delivery နဲ့ ပို့မယ့် order ဖြစ်လို့ ကျန်ငွေကို အွန်လိုင်းမှ အပြည့်အကြေ လွှဲရပါမယ်။");
   o.setCollectionPaymentMethodId(m.getId());
   o.setPaymentInstructions(isCash(m) ? "Pay the remaining amount in cash to the rider"
    : buildPayeeInstructions(m.getId()));
   changed(o, "Remainder payment method: " + m.getMethodName());
   return;
  }
  if(!transferNow(o))
   throw new IllegalStateException("This order is pay-on-collection");
  expireHeld(o);
  if(!Set.of("AWAITING_PAYMENT","EXPIRED","REJECTED").contains(o.getPaymentState()))
   throw new IllegalStateException("Cannot change payment channel in the current state");
  var m = requireCustomerChannel(paymentMethodId);
  o.setPaymentMethodId(m.getId());
  o.setPaymentInstructions(buildPayeeInstructions(m.getId()));
  changed(o, "ငွေလွှဲ Channel: " + m.getMethodName());
 }

 private org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.model.PaymentMethod requireCustomerChannel(Integer id) {
  if(id==null) throw new IllegalArgumentException("Payment channel is required");
  var m = methods.findById(id).orElseThrow(() -> new IllegalArgumentException("Payment channel not found"));
  if(!m.isActive()) throw new IllegalArgumentException("Payment channel is not active");
  if(Boolean.FALSE.equals(m.getShowOnCustomerApp())) throw new IllegalArgumentException("Payment channel is not available on customer app");
  String name = m.getPayeeName()==null?"":m.getPayeeName().trim();
  String account = m.getPayeeAccountNo()==null?"":m.getPayeeAccountNo().trim();
  if(name.isBlank() || account.isBlank())
   throw new IllegalStateException("Shop has not set payee name/account for this channel");
  return m;
 }

 @Transactional(readOnly = true)
 public java.util.List<CustomerPaymentChannelDTO> customerChannels() {
  return methods.findAllByActiveTrue().stream()
   .filter(m -> !Boolean.FALSE.equals(m.getShowOnCustomerApp()))
   .filter(m -> {
    if(isCash(m)) return true;
    String name = m.getPayeeName()==null?"":m.getPayeeName().trim();
    String account = m.getPayeeAccountNo()==null?"":m.getPayeeAccountNo().trim();
    return !name.isBlank() && !account.isBlank();
   })
   .map(m -> {
    CustomerPaymentChannelDTO dto = new CustomerPaymentChannelDTO();
    dto.setId(m.getId());
    dto.setMethodName(m.getMethodName());
    dto.setPayeeName(m.getPayeeName());
    dto.setPayeeAccountNo(m.getPayeeAccountNo());
    dto.setPayeeHint(m.getPayeeHint());
    return dto;
   })
   .toList();
 }

 @Transactional public void cancel(Integer id) {
  CustomerOrder o=locked(id);
  if(o.getStatus()==CustomerOrderStatus.CANCELLED)return;
  if(o.getCompletedSaleId()!=null || Set.of("PAID","DEPOSIT_PAID","REVIEW","LATE_REVIEW","PROOF_SUBMITTED","CHECKING","REFUND_REQUIRED","REFUNDED","FORFEITED").contains(o.getPaymentState()))throw new IllegalStateException("Resolve payment / refund before cancellation");
  stock.release(o);o.setStatus(CustomerOrderStatus.CANCELLED);
  releasePromo(o);
  changed(o,"အော်ဒါကို ပယ်ဖျက်လိုက်ပါသည်။");
 }

 @Transactional public void submit(Integer id,String reference,BigDecimal amount,MultipartFile image, Integer paymentMethodId) throws java.io.IOException {
  CustomerOrder o=locked(id);
  if(!o.getCustomer().getId().equals(CustomerPortalAuth.require().getCustomerId()))throw new org.springframework.security.access.AccessDeniedException("Not your order");
  shippingReady(o);
  boolean remainderStage="DEPOSIT_PAID".equals(o.getPaymentState())
   && CustomerOrderDeliveryRules.canCollectRemainder(o) && remainingDue(o).signum()>0;
  if(!remainderStage) expireHeld(o);
  if(!remainderStage && !transferNow(o))throw new IllegalStateException("Order is not ready for bank transfer");
  if(remainderStage) {
   var m=requireCollectionMethod(paymentMethodId!=null?paymentMethodId:o.getCollectionPaymentMethodId());
   if(isCash(m)) throw new IllegalStateException("Cash remainder does not require transfer proof");
   o.setCollectionPaymentMethodId(m.getId());
  }
  if(!remainderStage && o.getPaymentMethodId()==null) {
   if(paymentMethodId==null) throw new IllegalArgumentException("Choose a payment channel first");
   var m = requireCustomerChannel(paymentMethodId);
   o.setPaymentMethodId(m.getId());
   o.setPaymentInstructions(buildPayeeInstructions(m.getId()));
  } else if(!remainderStage && paymentMethodId!=null && !paymentMethodId.equals(o.getPaymentMethodId())) {
   if(!Set.of("AWAITING_PAYMENT","EXPIRED","REJECTED").contains(o.getPaymentState()))
    throw new IllegalStateException("Cannot change payment channel now");
   var m = requireCustomerChannel(paymentMethodId);
   o.setPaymentMethodId(m.getId());
   o.setPaymentInstructions(buildPayeeInstructions(m.getId()));
  }
  Integer proofMethodId=remainderStage?o.getCollectionPaymentMethodId():o.getPaymentMethodId();
  if(proofMethodId==null)throw new IllegalStateException("Order is not ready for bank transfer");
  String ref = reference == null ? null : reference.trim();
  if (ref != null && ref.isBlank()) ref = null;
  if (ref != null && ref.length() > 120) throw new IllegalArgumentException("Transaction reference max 120 characters");
  if (ref != null) {
   ref = ref.toUpperCase(Locale.ROOT);
   var existing = proofs.findByPaymentMethodIdAndTransactionReference(proofMethodId, ref);
   if (existing.isPresent()) {
    if (existing.get().getOrderId().equals(id) && !"REJECTED".equals(existing.get().getReviewState())) return;
    throw new IllegalArgumentException("Transaction reference already submitted; contact the shop");
   }
  }
  if(amount==null || amount.signum()<=0 || amount.scale()>2 || amount.precision()>15)throw new IllegalArgumentException("Invalid transferred amount");
  BigDecimal required=remainderStage?remainingDue(o):expectedTransfer(o);
  if(amount.compareTo(required)!=0)
   throw new IllegalArgumentException(hasDeposit(o)
     ? "လွှဲရမည့် စရံငွေနှင့် တူညီရမည်"
     : "လွှဲရမည့် ကျသင့်ငွေနှင့် တူညီရမည်");
  if(!remainderStage && !Set.of("AWAITING_PAYMENT","EXPIRED","REJECTED").contains(o.getPaymentState()))throw new IllegalStateException("Payment is already under review or resolved");
  if(image==null || image.isEmpty() || image.getSize()>2*1024*1024)throw new IllegalArgumentException("Upload a JPEG or PNG up to 2 MB");
  byte[] data=image.getBytes(); String mime;
  try(var stream=javax.imageio.ImageIO.createImageInputStream(new java.io.ByteArrayInputStream(data))) {
   var readers=javax.imageio.ImageIO.getImageReaders(stream);
   if(!readers.hasNext())throw new IllegalArgumentException("Invalid payment image");
   var reader=readers.next();
   try {
    reader.setInput(stream);String format=reader.getFormatName().toLowerCase(Locale.ROOT);
    if(!Set.of("jpeg","jpg","png").contains(format) || (long)reader.getWidth(0)*reader.getHeight(0)>20000000)throw new IllegalArgumentException("Use a JPEG/PNG below 20 megapixels");
    mime=format.equals("png")?"image/png":"image/jpeg";
   } finally {reader.dispose();}
  }
  CustomerOrderPaymentProof proof=new CustomerOrderPaymentProof();
  proof.setOrderId(id);proof.setPaymentMethodId(proofMethodId);proof.setTransactionReference(ref);proof.setAmount(amount);
  proof.setImageData(data);proof.setImageType(mime);proof.setSubmittedAt(LocalDateTime.now());
  boolean late = !remainderStage && lateTransferOpen(o);
  proof.setReviewState(remainderStage ? "REMAINDER_PROOF_SUBMITTED" : late ? "LATE_REVIEW" : "PROOF_SUBMITTED");
  proofs.saveAndFlush(proof);
  if(remainderStage) o.setCollectionProofId(proof.getId()); else o.setLatestProofId(proof.getId());
  if(remainderStage) {
   o.setPaymentState("REMAINDER_PROOF_SUBMITTED");
   changed(o,"Remainder transfer proof submitted; waiting for shop verification");
   return;
  }
  if(late) {
   o.setPaymentState("LATE_REVIEW");
   changed(o,"နောက်ကျ ငွေလွှဲအထောက်အထား ရပါပြီ။ ဆိုင်က stock ပြန်စစ်ပြီး ဆက်ရောင်းမလား / ငွေပြန်အမ်းမလား ဆုံးဖြတ်ပါမည်။");
  } else {
   o.setPaymentState("PROOF_SUBMITTED");
   changed(o,"ငွေလွှဲအချက်အလက် ရရှိပါပြီ။ ဆိုင်မှ ချက်ချင်း စစ်ဆေးနေသည် — ခဏစောင့်ပါ။");
  }
 }

 @Transactional public void review(Integer id,OrderPaymentRequest request) {
  CustomerOrder o=locked(id);shippingReady(o);String action=request.getAction();
  if("APPROVE".equals(action) && Set.of("PAID","DEPOSIT_PAID").contains(o.getPaymentState()))return;
  if("COLLECT_REMAINDER".equals(action) && "PAID".equals(o.getPaymentState()) && o.getCollectionAmount()!=null)return;
  if(o.getCompletedSaleId()!=null)throw new IllegalStateException("Use sale returns for completed sales");
   boolean remainderReview=Set.of("REMAINDER_PROOF_SUBMITTED","REMAINDER_CHECKING")
    .contains(o.getPaymentState());
   Integer proofId=remainderReview?o.getCollectionProofId():o.getLatestProofId();
   var proof=proofId==null?null:proofs.findById(proofId).orElseThrow();
   if("START_CHECK".equals(action) && "REMAINDER_PROOF_SUBMITTED".equals(o.getPaymentState())) {
    o.setPaymentState("REMAINDER_CHECKING");
    if(proof!=null){proof.setReviewState("REMAINDER_CHECKING");proof.setReviewedBy(actor());}
    changed(o,"Shop is checking the remainder transfer");
    return;
   }
   if("APPROVE".equals(action) && "REMAINDER_CHECKING".equals(o.getPaymentState())) {
    BigDecimal remain=remainingDue(o);
    if(proof==null || proof.getAmount()==null || proof.getAmount().compareTo(remain)!=0
      || request.getAmount()==null || request.getAmount().compareTo(remain)!=0)
     throw new IllegalArgumentException("Verified remainder must equal " + remain + " Ks");
    if(o.getCollectionPaymentMethodId()==null
      || !o.getCollectionPaymentMethodId().equals(proof.getPaymentMethodId()))
     throw new IllegalStateException("Remainder proof payment method does not match");
    var collectionMethod=methods.findById(o.getCollectionPaymentMethodId()).orElseThrow();
    if(isCash(collectionMethod))
     throw new IllegalStateException("Cash remainder must not use online proof approval");
    o.setCollectionAmount(remain);
    o.setCollectionReference(proof.getTransactionReference());
    o.setCollectionAt(LocalDateTime.now());
    o.setCollectionRecordedBy(actor());
    o.setPaymentVerifiedBy(actor());
    o.setPaymentVerifiedAt(LocalDateTime.now());
    o.setPaymentState("PAID");
    proof.setReviewState("PAID");proof.setReviewedBy(actor());proof.setReviewNote(request.getNote());
    recordReceivedTransaction(o, remain, o.getCollectionPaymentMethodId(), proof.getTransactionReference(), "REMAINDER");
    changed(o,"Online remainder verified; payment is complete");
    return;
   }
   if("REJECT".equals(action) && "REMAINDER_CHECKING".equals(o.getPaymentState())) {
    note(request.getNote());
    o.setPaymentState("DEPOSIT_PAID");
    o.setCollectionProofId(null);
    if(proof!=null){proof.setReviewState("REJECTED");proof.setReviewedBy(actor());proof.setReviewNote(request.getNote());}
    changed(o,"Remainder transfer proof rejected; customer may submit again");
    return;
   }
  if("START_CHECK".equals(action)) {
   if(!"PROOF_SUBMITTED".equals(o.getPaymentState()))throw new IllegalStateException("No submitted payment to inspect");
   o.setPaymentState("CHECKING");
   if(proof!=null){proof.setReviewState("CHECKING");proof.setReviewedBy(actor());}
   changed(o,"ဆိုင်မှ ငွေလွှဲကို စစ်ဆေးနေသည်။ ခဏစောင့်ပါ။");
   return;
  } else if("APPROVE".equals(action)) {
   if(!Set.of("CHECKING","REVIEW","LATE_REVIEW","AWAITING_COLLECTION").contains(o.getPaymentState()))throw new IllegalStateException("Start inspection before verifying payment");
   if(request.getAmount()==null || request.getAmount().compareTo(expectedTransfer(o))!=0)
    throw new IllegalArgumentException("Verified received amount must equal the required transfer");
   if(proof!=null && proof.getAmount().compareTo(expectedTransfer(o))!=0)
    throw new IllegalArgumentException("Submitted amount does not match; resolve the difference first");
   if(o.getPaymentMethodId()==null && proof!=null) o.setPaymentMethodId(proof.getPaymentMethodId());
   if(o.getPaymentMethodId()==null) {
    method(request.getPaymentMethodId());
    o.setPaymentMethodId(request.getPaymentMethodId());
   }
   // A late transfer must acquire stock again under the same product locks.
   stock.reserve(o);o.setStatus(CustomerOrderStatus.CONFIRMED);
   o.setPaymentVerifiedBy(actor());o.setPaymentVerifiedAt(LocalDateTime.now());
   o.setPaymentReviewNote(request.getNote());
   if(remainingDue(o).signum()>0) {
    o.setPaymentState("DEPOSIT_PAID");
    if(proof!=null){proof.setReviewState(o.getPaymentState());proof.setReviewedBy(actor());proof.setReviewNote(request.getNote());}
    recordReceivedTransaction(o, expectedTransfer(o), o.getPaymentMethodId(), proof==null?null:proof.getTransactionReference(), "DEPOSIT");
    changed(o,"စရံငွေ အတည်ပြုပြီးပါပြီ။ ကျန် "+remainingDue(o)+" Ks လက်ခံမှတ်ပြီးမှ Sale ထည့်ပါ။"
      +(request.getNote()==null?"":" — "+request.getNote()));
    return;
   }
   o.setPaymentState("PAID");
   if(proof!=null){proof.setReviewState(o.getPaymentState());proof.setReviewedBy(actor());proof.setReviewNote(request.getNote());}
   recordReceivedTransaction(o, total(o), o.getPaymentMethodId(), proof==null?null:proof.getTransactionReference(), "FULL");
   changed(o,"ငွေဝင်မှု အတည်ပြုပြီးပါပြီ။ ငွေလက်ခံပြေစာ ရနိုင်ပါပြီ။"+(request.getNote()==null?"":" — "+request.getNote()));
   return;
  } else if("COLLECT_REMAINDER".equals(action)) {
   if(!"DEPOSIT_PAID".equals(o.getPaymentState()))throw new IllegalStateException("Collect remaining only after the deposit is verified");
   if(!CustomerOrderDeliveryRules.canCollectRemainder(o))
    throw new IllegalStateException("ပစ္စည်း ပို့ပြီး / ရောက်မှသာ ကျန်ငွေ လက်ခံမှတ်ပါ");
   BigDecimal remain=remainingDue(o);
   if(remain.signum()<=0) {
    o.setPaymentState("PAID");
    changed(o,"ကျန်ငွေ မရှိပါ။ ငွေအပြည့် ရရှိပြီးပါပြီ။");
    return;
   }
   if(request.getAmount()==null || request.getAmount().compareTo(remain)!=0)
    throw new IllegalArgumentException("ကျန်ငွေ "+remain+" Ks နှင့် တူညီရမည်");
    method(request.getPaymentMethodId());
    var collectionMethod=methods.findById(request.getPaymentMethodId()).orElseThrow();
    if(CustomerOrderDeliveryRules.requiresFullTransfer(o))
     throw new IllegalStateException("အခြား delivery နဲ့ မပို့ခင် ကျန်ငွေကို အွန်လိုင်းမှ အပြည့်အကြေ လွှဲပြီး ဆိုင်က အတည်ပြုရပါမယ်။");
    if(!isCash(collectionMethod))
     throw new IllegalStateException("Online remainder requires customer transfer proof and shop approval");
    o.setCollectionPaymentMethodId(request.getPaymentMethodId());
    o.setCollectionAmount(remain);
    o.setCollectionReference("CASH-"+o.getOrderNo());
   o.setCollectionAt(LocalDateTime.now());
   o.setCollectionRecordedBy(actor());
   o.setPaymentState("PAID");
   recordReceivedTransaction(o, remain, o.getCollectionPaymentMethodId(), o.getCollectionReference(), "REMAINDER");
   changed(o,"ကျန်ငွေ "+remain+" Ks လက်ခံမှတ်ပြီးပါပြီ။ ငွေအပြည့် ရမှ Sale ထည့်ပါ။"
     +(request.getNote()==null?"":" — "+request.getNote()));
   return;
  } else if("REJECT".equals(action)) {
   if(!Set.of("CHECKING","REVIEW","LATE_REVIEW").contains(o.getPaymentState()))throw new IllegalStateException("No proof awaiting review");
   note(request.getNote());stock.release(o);o.setPaymentState("REJECTED");
  } else if("REFUND_REQUIRED".equals(action)) {
   if(!Set.of("PAID","DEPOSIT_PAID","CHECKING","REVIEW","LATE_REVIEW").contains(o.getPaymentState()))throw new IllegalStateException("No payment to refund");
   note(request.getNote());stock.release(o);o.setPaymentState("REFUND_REQUIRED");
  } else if("FORFEIT_DEPOSIT".equals(action)) {
   forfeitDeposit(o, request);
   if(proof!=null){proof.setReviewState(o.getPaymentState());proof.setReviewedBy(actor());proof.setReviewNote(request.getNote());}
   return;
  } else if("REFUNDED".equals(action)) {
   refundPayment(o, request, proof);
   if(proof!=null){proof.setReviewState(o.getPaymentState());proof.setReviewedBy(actor());proof.setReviewNote(request.getNote());}
   return;
  } else throw new IllegalArgumentException("Unknown review action");
  o.setPaymentReviewNote(request.getNote());
  if(proof!=null){proof.setReviewState(o.getPaymentState());proof.setReviewedBy(actor());proof.setReviewNote(request.getNote());}
  changed(o,"ငွေပေးချေမှု အခြေအနေ: " + o.getPaymentState() + (request.getNote()==null?"":" — " + request.getNote()));
 }

 @Transactional public Integer fulfill(Integer id,OrderPaymentRequest request) {
  CustomerOrder o=locked(id);
  if(o.getCompletedSaleId()!=null)return o.getCompletedSaleId();
  shippingReady(o);
  if(!"PAID".equals(o.getPaymentState()) || !o.isReservationActive())
   throw new IllegalStateException(hasDeposit(o) && remainingDue(o).signum()>0 && "DEPOSIT_PAID".equals(o.getPaymentState())
     ? "ကျန်ငွေ လက်ခံမှတ်ပြီးမှ Sale ထည့်ပါ"
     : "Verify payment before completing sale");
  BigDecimal remain=remainingDue(o);
  if(hasDeposit(o) && remain.signum()>0) {
   if(o.getCollectionPaymentMethodId()==null)
    throw new IllegalStateException("ကျန်ငွေ လက်ခံ Channel ရွေးပြီးမှ Sale ထည့်ပါ");
   if(o.getCollectionAmount()==null || o.getCollectionAmount().compareTo(remain)!=0)
    throw new IllegalStateException("ကျန်ငွေ "+remain+" Ks လက်ခံမှတ်ပြီးမှ Sale ထည့်ပါ");
  }
  if(o.getPaymentMethodId()==null) {
   method(request.getPaymentMethodId());
   o.setPaymentMethodId(request.getPaymentMethodId());
  }
  method(o.getPaymentMethodId());
  SaleDTO sale=new SaleDTO();sale.setCustomerId(o.getCustomer().getId());sale.setStaffId(request.getStaffId());
  sale.setSaleDate(LocalDateTime.now());sale.setPaymentMethodId(o.getPaymentMethodId());
  sale.setPaidAmount(total(o));sale.setDeliveryCharge(zero(o.getDeliveryCharge()));
  sale.setPayments(salePayments(o));
  sale.setCustomerAdvanceApplied(advanceReceivedForSale(o));
  sale.setRemark("Customer order " + o.getOrderNo()
    + (hasDeposit(o) ? " — စရံ "+expectedTransfer(o)+" / ကျန် "+remain : "")
    + ("HANDOFF".equals(o.getDeliveryHandler()) ? " — ပြင်ပို့ အပ် (ဆိုင်ပို့ခ မထည့်)" : ""));
  if(o.getLatestProofId()!=null)sale.setTransactionNo(proofs.findById(o.getLatestProofId()).orElseThrow().getTransactionReference());
  List<SaleDetailDTO> details=new ArrayList<>();
  for(var line:o.getLines()) {
   SaleDetailDTO detail=new SaleDetailDTO();detail.setProductId(line.getProduct().getId());detail.setQty(line.getQty());detail.setUnitPrice(line.getUnitPrice());
   if(Boolean.TRUE.equals(line.getProduct().getHasSerial())) {
    var serials=request.getSerialNumbers()==null?null:request.getSerialNumbers().get(line.getProduct().getId());
    if(serials==null || serials.size()!=line.getQty())throw new IllegalArgumentException("Select the exact serial numbers for " + line.getProductName());
    detail.setSerialNumbers(serials);
   }
   details.add(detail);
  }
  sale.setDetails(details);
  // Releasing and recording the sale share one transaction: rollback restores the hold.
  stock.release(o);orders.flush();
  var completed=sales.save(sale);o.setCompletedSaleId(completed.getId());o.setPaymentState("FULFILLED");
  // SaleService has now posted the same split payments against the sale.
  if(paymentTransactions!=null) paymentTransactions.deleteByReferenceIdAndReferenceType(o.getId(), ReferenceType.Customer_Order);
  // Legacy unit tests construct this service reflectively; Spring always injects the hook in production.
  if(loyalty!=null) loyalty.award(o,total(o));
  changed(o,"အော်ဒါအတွက် အရောင်းဘောင်ချာ ထုတ်ပြီးပါပြီ။");
  return completed.getId();
 }

 private List<org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.dto.PaymentTransactionDTO> salePayments(CustomerOrder o) {
  List<org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.dto.PaymentTransactionDTO> lines=new ArrayList<>();
  String depositRef=o.getLatestProofId()==null?null:proofs.findById(o.getLatestProofId()).map(p->p.getTransactionReference()).orElse(null);
  BigDecimal deposit=hasDeposit(o)?expectedTransfer(o):total(o);
  if(deposit.signum()>0 && o.getPaymentMethodId()!=null) {
   var line=new org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.dto.PaymentTransactionDTO();
   line.setPaymentMethodId(o.getPaymentMethodId());
   line.setAmount(deposit);
   line.setTransactionNo(depositRef==null||depositRef.isBlank()?(hasDeposit(o)?"DEPOSIT-"+o.getOrderNo():o.getOrderNo()):depositRef);
   lines.add(line);
  }
  BigDecimal remain=remainingDue(o);
  if(remain.signum()>0 && o.getCollectionPaymentMethodId()!=null) {
   var line=new org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.dto.PaymentTransactionDTO();
   line.setPaymentMethodId(o.getCollectionPaymentMethodId());
   line.setAmount(remain);
    line.setTransactionNo(o.getCollectionReference()==null
      ?"REMAINDER-"+o.getOrderNo():o.getCollectionReference());
   lines.add(line);
  }
  return lines;
 }

 private BigDecimal confirmedReceived(CustomerOrder o, CustomerOrderPaymentProof proof) {
  if(o.getCollectionAmount()!=null && o.getCollectionAmount().signum()>0)
   return expectedTransfer(o).add(o.getCollectionAmount()).setScale(2, RoundingMode.HALF_UP);
  if("DEPOSIT_PAID".equals(o.getPaymentState())) return expectedTransfer(o);
  if("PAID".equals(o.getPaymentState())) return total(o);
  if("REFUND_REQUIRED".equals(o.getPaymentState())) {
   if(hasDeposit(o)) return expectedTransfer(o);
   if(proof!=null && proof.getAmount()!=null && proof.getAmount().signum()>0)
    return proof.getAmount().setScale(2, RoundingMode.HALF_UP);
   return total(o);
  }
  if(proof!=null && proof.getAmount()!=null && proof.getAmount().signum()>0)
   return proof.getAmount().setScale(2, RoundingMode.HALF_UP);
  return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
 }

 private void recordSettlement(CustomerOrder o, String action, BigDecimal refunded, BigDecimal kept,
                              Integer methodId, String reference, String noteText) {
  o.setSettlementAction(action);
  o.setSettlementAmount(refunded);
  o.setSettlementKeptAmount(kept);
  o.setSettlementPaymentMethodId(methodId);
  o.setSettlementReference(reference);
  o.setSettlementAt(LocalDateTime.now());
  o.setSettlementRecordedBy(actor());
  o.setPaymentReviewNote(noteText);
 }

 private void recordRefundTransaction(CustomerOrder o, BigDecimal amount, Integer methodId, String reference) {
  var method = methods.findById(methodId).orElseThrow(() -> new IllegalArgumentException("Choose an active payment method"));
  PaymentTransaction tx = new PaymentTransaction();
  tx.setReferenceId(o.getId());
  tx.setReferenceType(ReferenceType.Customer_Order);
  tx.setPaymentMethod(method);
  tx.setAmount(amount.negate());
  tx.setPaymentDate(LocalDateTime.now());
  tx.setTransactionNo(reference);
  paymentTransactions.save(tx);
 }

 private void recordReceivedTransaction(CustomerOrder o, BigDecimal amount, Integer methodId, String reference, String stage) {
  if(paymentTransactions==null || amount==null || amount.signum()<=0 || methodId==null) return;
  String transactionNo=(reference==null||reference.isBlank())?stage+"-"+o.getOrderNo():reference.trim();
  boolean exists=paymentTransactions.findByReferenceIdAndReferenceType(o.getId(), ReferenceType.Customer_Order)
    .stream().anyMatch(tx -> !Boolean.TRUE.equals(tx.getReversed()) && transactionNo.equals(tx.getTransactionNo()));
  if(!exists) {
   var method=methods.findById(methodId).orElseThrow(() -> new IllegalArgumentException("Choose an active payment method"));
   PaymentTransaction tx=new PaymentTransaction();
   tx.setReferenceId(o.getId());tx.setReferenceType(ReferenceType.Customer_Order);tx.setPaymentMethod(method);
   tx.setAmount(amount.setScale(2, RoundingMode.HALF_UP));tx.setPaymentDate(LocalDateTime.now());tx.setTransactionNo(transactionNo);
   paymentTransactions.save(tx);
  }
  postAdvanceReceiptJournal(o, amount, methodId, stage);
 }

 private String advanceJournalRef(CustomerOrder o, String stage) {
  return "CUSTOMER-ORDER-"+o.getId()+"-ADV-"+stage;
 }

 private void postAdvanceReceiptJournal(CustomerOrder o, BigDecimal amount, Integer methodId, String stage) {
  if(journalWriter==null || accounts==null || amount==null || amount.signum()<=0) return;
  String ref=advanceJournalRef(o, stage);
  if(journalWriter.hasActiveReferencePrefix(ref)) return;
  var method=methods.findById(methodId).orElseThrow(() -> new IllegalArgumentException("Choose an active payment method"));
  if(method.getAccount()==null || method.getAccount().getId()==null)
   throw new IllegalStateException("Payment method must have a ledger account");
  JournalEntryDTO entry=new JournalEntryDTO();
  entry.setReferenceNo(ref);entry.setEntryDate(LocalDateTime.now());
  entry.setDescription("Customer order advance receipt - "+o.getOrderNo()+" ("+stage+")");
  entry.setDetails(List.of(journalLine(method.getAccount().getId(), amount, BigDecimal.ZERO),
    journalLine(accounts.custAdvance().getId(), BigDecimal.ZERO, amount)));
  journalWriter.write(entry);
 }

 private BigDecimal advanceReceivedForSale(CustomerOrder o) {
  if(journalWriter==null) return BigDecimal.ZERO;
  if(journalWriter.hasActiveReferencePrefix(advanceJournalRef(o,"FULL"))) return total(o);
  BigDecimal amount=BigDecimal.ZERO;
  if(journalWriter.hasActiveReferencePrefix(advanceJournalRef(o,"DEPOSIT"))) amount=amount.add(expectedTransfer(o));
  if(journalWriter.hasActiveReferencePrefix(advanceJournalRef(o,"REMAINDER"))) amount=amount.add(remainingDue(o));
  return amount.min(total(o));
 }

 private JournalDetailDTO journalLine(Integer accountId, BigDecimal debit, BigDecimal credit) {
  JournalDetailDTO line=new JournalDetailDTO();line.setAccountId(accountId);line.setDebit(debit);line.setCredit(credit);return line;
 }
 private void postAdvanceSettlementJournal(CustomerOrder o, BigDecimal refund, Integer refundMethodId, String action) {
  if(journalWriter==null || accounts==null) return;
  BigDecimal journaled=advanceReceivedForSale(o);
  if(journaled.signum()<=0) return;
  String ref="CUSTOMER-ORDER-"+o.getId()+"-ADV-SETTLEMENT";
  if(journalWriter.hasActiveReferencePrefix(ref)) return;
  BigDecimal refunded=refund==null?BigDecimal.ZERO:refund.max(BigDecimal.ZERO).min(journaled);
  BigDecimal kept=journaled.subtract(refunded);
  List<JournalDetailDTO> lines=new ArrayList<>();
  lines.add(journalLine(accounts.custAdvance().getId(), journaled, BigDecimal.ZERO));
  if(refunded.signum()>0) {
   var method=methods.findById(refundMethodId).orElseThrow(() -> new IllegalArgumentException("Choose an active payment method"));
   if(method.getAccount()==null || method.getAccount().getId()==null)
    throw new IllegalStateException("Refund payment method must have a ledger account");
   lines.add(journalLine(method.getAccount().getId(), BigDecimal.ZERO, refunded));
  }
  if(kept.signum()>0) lines.add(journalLine(accounts.otherIncome().getId(), BigDecimal.ZERO, kept));
  JournalEntryDTO entry=new JournalEntryDTO();entry.setReferenceNo(ref);entry.setEntryDate(LocalDateTime.now());
  entry.setDescription("Customer order advance settlement - "+o.getOrderNo()+" ("+action+")");entry.setDetails(lines);
  journalWriter.write(entry);
 }
 private String settlementReference(OrderPaymentRequest request) {
  String ref = request.getTransactionNo()==null ? null : request.getTransactionNo().trim().toUpperCase(Locale.ROOT);
  if(ref==null || ref.isBlank()) throw new IllegalArgumentException("ပြန်အမ်း transaction reference ထည့်ပါ");
  if(ref.length()>120) throw new IllegalArgumentException("Transaction reference max 120 characters");
  return ref;
 }

 private void forfeitDeposit(CustomerOrder o, OrderPaymentRequest request) {
  if(!hasDeposit(o)) throw new IllegalStateException("စရံမရှိပါ — ငွေပြန်အမ်း ရွေးပါ");
  if(!Set.of("DEPOSIT_PAID").contains(o.getPaymentState()))
   throw new IllegalStateException("စရံအတည်ပြုပြီး ကျန်ငွေမကောက်ခင်မှ သိမ်းနိုင်သည်");
  note(request.getNote());
  BigDecimal received = confirmedReceived(o, null);
  recordSettlement(o, "FORFEIT", BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), received,
    o.getPaymentMethodId(), "FORFEIT-"+o.getOrderNo(), request.getNote());
  postAdvanceSettlementJournal(o, BigDecimal.ZERO, null, "FORFEIT");
  stock.release(o);
  o.setPaymentState("FORFEITED");
  o.setStatus(CustomerOrderStatus.CANCELLED);
  releasePromo(o);
  changed(o,"စရံ "+received+" Ks သိမ်းပြီး အော်ဒါ ပယ်ဖျက်ပါသည်။ — "+request.getNote());
 }

 private void refundPayment(CustomerOrder o, OrderPaymentRequest request, CustomerOrderPaymentProof proof) {
  if(!Set.of("DEPOSIT_PAID","PAID","REFUND_REQUIRED","CHECKING","REVIEW","LATE_REVIEW").contains(o.getPaymentState()))
   throw new IllegalStateException("Refund must follow a received or queued payment");
  note(request.getNote());
  BigDecimal received = confirmedReceived(o, proof);
  if(received.signum()<=0) throw new IllegalStateException("ပြန်အမ်းရန် လက်ခံပြီးငွေ မရှိပါ");
  BigDecimal amount = request.getAmount();
  if(amount==null || amount.signum()<=0 || amount.scale()>2 || amount.precision()>15 || amount.compareTo(received)>0)
   throw new IllegalArgumentException("ပြန်အမ်းငွေ 0 နှင့် "+received+" Ks ကြား ဖြစ်ရမည်");
  method(request.getPaymentMethodId());
  String ref = settlementReference(request);
  recordRefundTransaction(o, amount.setScale(2, RoundingMode.HALF_UP), request.getPaymentMethodId(), ref);
  BigDecimal kept = received.subtract(amount).setScale(2, RoundingMode.HALF_UP);
  recordSettlement(o, "REFUND", amount.setScale(2, RoundingMode.HALF_UP), kept,
    request.getPaymentMethodId(), ref, request.getNote());
  postAdvanceSettlementJournal(o, amount.setScale(2, RoundingMode.HALF_UP), request.getPaymentMethodId(), "REFUND");
  stock.release(o);
  o.setPaymentState("REFUNDED");
  o.setStatus(CustomerOrderStatus.CANCELLED);
  releasePromo(o);
  changed(o,"ငွေ "+amount+" Ks ပြန်အမ်းမှတ်ပြီးပါပြီ"
    +(kept.signum()>0 ? " · သိမ်း "+kept+" Ks" : "")
    +" — "+request.getNote());
 }

 private void applyDeliveryHandler(CustomerOrder o, OrderPaymentRequest request) {
  if(!"DELIVERY".equalsIgnoreCase(o.getOrderType()==null?"":o.getOrderType())) {
   o.setDeliveryHandler(null);
   return;
  }
  String handler = request==null || request.getDeliveryHandler()==null ? "" : request.getDeliveryHandler().trim().toUpperCase();
  if ("ACCEPTED".equals(o.getShippingState())) {
   if(handler.isBlank() || handler.equals(o.getDeliveryHandler())) return;
   throw new IllegalStateException("Handler changed; issue a new delivery quote first");
  }
  if(!Set.of("OWN","HANDOFF").contains(handler))
   throw new IllegalArgumentException("ပို့မည့်ပုံ ရွေးပါ — ကိုယ်တိုင်ပို့ သို့မဟုတ် အခြားသူဆီ အပ်ပေး");
  if(o.getQuotedDeliveryCharge()==null) o.setQuotedDeliveryCharge(zero(o.getDeliveryCharge()));
  o.setDeliveryHandler(handler);
  if("HANDOFF".equals(handler)) o.setDeliveryCharge(BigDecimal.ZERO);
  else o.setDeliveryCharge(zero(o.getQuotedDeliveryCharge()));
 }

 private void changed(CustomerOrder o,String text) {
  o.setUpdatedAt(LocalDateTime.now());orders.saveAndFlush(o);
  events.publishCustomerOrder("CUSTOMER_ORDER_UPDATED", o.getId()); events.publishTopic("/topic/products","STOCK_UPDATED");
  CustomerPortalNotificationDTO dto=new CustomerPortalNotificationDTO();dto.setId(-o.getId());dto.setOrderId(o.getId());dto.setOrderNo(o.getOrderNo());
  dto.setStatus(o.getPaymentState());dto.setChannel("CUSTOMER_ORDER");dto.setNote(o.getOrderNo()+" — "+text);dto.setNotifiedAt(o.getUpdatedAt());
  events.publishToUser(CustomerPortalAuth.usernameForCustomer(o.getCustomer().getId()),"/topic/customer-orders",dto);
 }

 private void releasePromo(CustomerOrder o) {
  if (promos != null && o != null) promos.release(o.getId());
 }
}





