package org.sspd.servicemgmt.customerportaloptions.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerPortalOrderRequest;
import org.sspd.servicemgmt.customerportaloptions.repository.CustomerDeliveryTownshipRepository;
import org.sspd.servicemgmt.customerportaloptions.repository.CustomerDeliveryWardRepository;
import org.sspd.servicemgmt.customerportaloptions.support.DeliveryScheduleRules;
import org.sspd.servicemgmt.customerportaloptions.support.DeliveryScheduleRules.ClosedDate;
import org.sspd.servicemgmt.customerportaloptions.support.DeliveryScheduleRules.DayWindow;
import org.sspd.servicemgmt.customerportaloptions.support.DeliveryScheduleRules.DayWindowRow;
import org.sspd.servicemgmt.customerportaloptions.support.DeliveryScheduleRules.Schedule;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Time;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DeliveryPricingService {
 private final JdbcTemplate jdbc;
 private final CustomerDeliveryWardRepository wards;
 private final CustomerDeliveryTownshipRepository townships;
 private final ObjectMapper objectMapper;

 public record Policy(BigDecimal includedKg, BigDecimal extraPerKg, BigDecimal maxAutoKg, Integer maxAutoQty,
                     Boolean deliveryEnabled, LocalTime opensAt, LocalTime closesAt, String deliveryDays,
                     List<DayWindowRow> weekdayHours, List<ClosedDate> closedDates, Integer minLeadDays) {
  public boolean isDeliveryEnabled() { return !Boolean.FALSE.equals(deliveryEnabled); }
 }
 public record Profile(BigDecimal weightKg, String shippingClass, Integer maxAutoQty) {}
 public record Quote(String state, BigDecimal weightKg, BigDecimal baseCharge, BigDecimal extraCharge,
                     BigDecimal deliveryCharge, String reason, Policy policy) {}

 @Transactional(readOnly=true) public Policy policy() {
  return jdbc.queryForObject("select * from delivery_pricing_policy where id=1",(r,rowNum)->{
   LocalTime opens = time(r.getTime("opens_at"), DeliveryScheduleRules.OPENS_AT);
   LocalTime closes = time(r.getTime("closes_at"), DeliveryScheduleRules.CLOSES_AT);
   String days = r.getString("delivery_days");
   Integer lead = r.getObject("min_lead_days") instanceof Number n ? n.intValue() : 1;
   List<DayWindowRow> weekdayHours = readWeekdays(r.getString("weekday_hours"));
   List<ClosedDate> closedDates = readClosedDates(r.getString("closed_dates"));
   Schedule schedule = DeliveryScheduleRules.fromLegacy(
     opens, closes, days, lead, DeliveryScheduleRules.indexByDayName(weekdayHours), closedDates);
   return toPolicy(r.getBigDecimal("included_kg"), r.getBigDecimal("extra_per_kg"), r.getBigDecimal("max_auto_kg"),
     (Integer) r.getObject("max_auto_qty"),
     r.getObject("delivery_enabled") instanceof Number flag ? flag.intValue()!=0 : Boolean.TRUE,
     schedule);
  });
 }

 public Schedule schedule() {
  Policy p = policy();
  return DeliveryScheduleRules.fromLegacy(
    p.opensAt(), p.closesAt(), p.deliveryDays(), p.minLeadDays(),
    DeliveryScheduleRules.indexByDayName(p.weekdayHours()), p.closedDates());
 }

 public void validateRequestedAt(LocalDateTime requested) {
  DeliveryScheduleRules.validate(requested, LocalDate.now(), schedule());
 }

 @Transactional public void savePolicy(Policy p) {
  if(p==null || !valid(p.includedKg(),false,3) || !valid(p.extraPerKg(),false,2) || !valid(p.maxAutoKg(),true,3)
    || p.maxAutoKg().compareTo(p.includedKg())<0 || p.maxAutoQty()==null || p.maxAutoQty()<1 || p.maxAutoQty()>100000)
    throw new IllegalArgumentException("Enter valid included kg, extra rate, maximum kg and quantity");
  boolean enabled = p.deliveryEnabled()==null || p.isDeliveryEnabled();
  int lead = p.minLeadDays()==null ? 1 : p.minLeadDays();
  if(lead<0 || lead>30) throw new IllegalArgumentException("ကြိုမှာရက်သည် ၀ မှ ၃၀ အတွင်း ဖြစ်ရမည်");
  LocalTime defaultOpen = p.opensAt()==null ? DeliveryScheduleRules.OPENS_AT : p.opensAt();
  LocalTime defaultClose = p.closesAt()==null ? DeliveryScheduleRules.CLOSES_AT : p.closesAt();
  List<ClosedDate> closedDates = normalizeClosedDates(p.closedDates());
  Schedule schedule = DeliveryScheduleRules.fromLegacy(
    defaultOpen, defaultClose, p.deliveryDays(), lead,
    DeliveryScheduleRules.indexByDayName(normalizeWeekdays(p.weekdayHours(), defaultOpen, defaultClose, p.deliveryDays())),
    closedDates);
  boolean anyOpen = schedule.weekdays().values().stream().anyMatch(DayWindow::open);
  if(!anyOpen) throw new IllegalArgumentException("အနည်းဆုံး ပို့ဆောင်မည့်နေ့ တစ်ရက်ရွေးပါ");
  for (DayWindow window : schedule.weekdays().values()) {
   if(window.open() && !window.opensAt().isBefore(window.closesAt()))
    throw new IllegalArgumentException("ပို့ဆောင်ချိန် အစသည် အဆုံးမတိုင်မီ ဖြစ်ရမည်");
  }
  jdbc.update("update delivery_pricing_policy set included_kg=?,extra_per_kg=?,max_auto_kg=?,max_auto_qty=?,delivery_enabled=?,"
    +"opens_at=?,closes_at=?,delivery_days=?,weekday_hours=?,closed_dates=?,min_lead_days=? where id=1",
   p.includedKg(),p.extraPerKg(),p.maxAutoKg(),p.maxAutoQty(),enabled,
   schedule.defaultOpensAt(),schedule.defaultClosesAt(),schedule.deliveryDays(),
   writeJson(toRows(schedule)), writeJson(closedDates), schedule.minLeadDays());
 }

 public void requireDeliveryAvailable() {
  if(!policy().isDeliveryEnabled()) {
   throw new IllegalStateException("သွားပို့ ယာယီပိတ်ထားသည်။ ဆိုင်မှာလာယူ ရွေးပါ");
  }
 }
 private static boolean valid(BigDecimal v,boolean positive,int scale) {
  return v!=null && v.signum()>=(positive?1:0) && v.scale()<=scale && v.compareTo(new BigDecimal("9999999"))<=0;
 }
 @Transactional(readOnly=true) public Profile profile(int id) {
  var rows=jdbc.query("select * from product_shipping_profiles where product_id=?",(r,n)->new Profile(r.getBigDecimal("weight_kg"),
   r.getString("shipping_class"),(Integer)r.getObject("max_auto_qty")),id);
  return rows.isEmpty()?new Profile(null,"MANUAL",null):rows.get(0);
 }
 @Transactional public void saveProfile(int id,Profile p) {
  if(p==null || p.shippingClass()==null || !Set.of("STANDARD","BULKY","MANUAL").contains(p.shippingClass())
   || (p.weightKg()!=null && !valid(p.weightKg(),true,3))
   || ("STANDARD".equals(p.shippingClass()) && p.weightKg()==null)
   || (p.maxAutoQty()!=null && (p.maxAutoQty()<1 || p.maxAutoQty()>100000))) throw new IllegalArgumentException("Invalid shipping profile");
  if(jdbc.queryForObject("select count(*) from products where id=?",Integer.class,id)==0)throw new IllegalArgumentException("Product not found");
  jdbc.update("insert into product_shipping_profiles(product_id,weight_kg,shipping_class,max_auto_qty) values (?,?,?,?) "
   +"on duplicate key update weight_kg=values(weight_kg),shipping_class=values(shipping_class),max_auto_qty=values(max_auto_qty)",
   id,p.weightKg(),p.shippingClass(),p.maxAutoQty());
 }
 @Transactional(readOnly=true) public Quote quote(CustomerPortalOrderRequest req) {
  if(req==null)throw new IllegalArgumentException("Order required");
  if("PICKUP".equals(req.getOrderType()))return new Quote("ACCEPTED",BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,"Pickup",null);
  if(!"DELIVERY".equals(req.getOrderType()))throw new IllegalArgumentException("Choose delivery or pickup");
  requireDeliveryAvailable();
  if(req.getTownshipId()==null)throw new IllegalArgumentException("Choose a township");
  var township=townships.findById(req.getTownshipId()).orElseThrow(()->new IllegalArgumentException("Township not found"));
  if(!Boolean.TRUE.equals(township.getActive()) || township.getRegion()==null
    || !Boolean.TRUE.equals(township.getRegion().getActive()))throw new IllegalArgumentException("Delivery area unavailable");
  var ward=req.getWardId()==null?null:wards.findWithTownshipAndRegionById(req.getWardId())
   .orElseThrow(()->new IllegalArgumentException("Ward not found"));
  if(ward!=null && (!Boolean.TRUE.equals(ward.getActive()) || ward.getTownship()==null
    || !township.getId().equals(ward.getTownship().getId())))throw new IllegalArgumentException("Ward does not match township");
  if(req.getLines()==null || req.getLines().isEmpty() || req.getLines().size()>200)throw new IllegalArgumentException("Choose 1-200 product lines");
  Policy policy=policy();
  BigDecimal weight=BigDecimal.ZERO;
  boolean known=true;
  long qty=0;
  List<String> reasons=new ArrayList<>();
  Set<Integer> seen=new HashSet<>();
  for(var line:req.getLines()) {
   if(line==null || line.getProductId()==null || line.getQty()==null || line.getQty()<1 || line.getQty()>100000
     || !seen.add(line.getProductId()))throw new IllegalArgumentException("Invalid or duplicate product quantity");
   var active=jdbc.queryForObject("select count(*) from products where id=? and archived=false",Integer.class,line.getProductId());
   if(active==null || active==0)throw new IllegalArgumentException("Product unavailable");
   var p=profile(line.getProductId());qty+=line.getQty();
   if(p.weightKg()==null)known=false;
   else weight=weight.add(p.weightKg().multiply(BigDecimal.valueOf(line.getQty())));
   if(!"STANDARD".equals(p.shippingClass()) || p.weightKg()==null)reasons.add("Product "+line.getProductId()+": shop quote required");
   if(p.maxAutoQty()!=null && line.getQty()>p.maxAutoQty())reasons.add("Product "+line.getProductId()+": quantity exceeds limit");
  }
  if(policy.includedKg()==null || policy.extraPerKg()==null || policy.maxAutoKg()==null || policy.maxAutoQty()==null)
   reasons.add("Delivery weight rates are not configured");
  else {
   if(weight.compareTo(policy.maxAutoKg())>0)reasons.add("Total weight exceeds automatic delivery limit");
   if(qty>policy.maxAutoQty())reasons.add("Total quantity exceeds automatic delivery limit");
  }
  BigDecimal base=ward==null?township.getDeliveryCharge():ward.getDeliveryCharge();
  if(base==null || base.signum()<0)throw new IllegalStateException("Invalid delivery charge");
  BigDecimal extra=BigDecimal.ZERO;
  if(known && policy.includedKg()!=null && policy.extraPerKg()!=null) {
   extra=weight.subtract(policy.includedKg()).max(BigDecimal.ZERO).setScale(0,RoundingMode.CEILING).multiply(policy.extraPerKg());
  }
  String reason=reasons.isEmpty()
   ? (known ? "Weight-based delivery" : (ward==null?"Township delivery":"Ward delivery"))
   : String.join("; ",reasons).substring(0,Math.min(950,String.join("; ",reasons).length()));
  return new Quote("QUOTED",known?weight:null,base,extra,base.add(extra),reason,policy);
 }

 private Policy toPolicy(BigDecimal includedKg, BigDecimal extraPerKg, BigDecimal maxAutoKg, Integer maxAutoQty,
                         Boolean enabled, Schedule schedule) {
  return new Policy(includedKg, extraPerKg, maxAutoKg, maxAutoQty, enabled,
    schedule.defaultOpensAt(), schedule.defaultClosesAt(), schedule.deliveryDays(),
    toRows(schedule), schedule.closedDates(), schedule.minLeadDays());
 }

 private static List<DayWindowRow> toRows(Schedule schedule) {
  List<DayWindowRow> rows = new ArrayList<>();
  for (DayOfWeek day : DeliveryScheduleRules.WEEK) {
   DayWindow window = DeliveryScheduleRules.windowFor(schedule, day);
   rows.add(new DayWindowRow(day.name(), window.open(), window.opensAt(), window.closesAt()));
  }
  return rows;
 }

 private List<DayWindowRow> normalizeWeekdays(List<DayWindowRow> rows, LocalTime defaultOpen, LocalTime defaultClose, String deliveryDays) {
  if (rows == null || rows.isEmpty()) {
   return toRows(DeliveryScheduleRules.fromLegacy(defaultOpen, defaultClose, deliveryDays, 1, Map.of(), List.of()));
  }
  List<DayWindowRow> normalized = new ArrayList<>();
  for (DayWindowRow row : rows) {
   if (row == null || row.day() == null) continue;
   LocalTime open = row.opensAt() == null ? defaultOpen : row.opensAt();
   LocalTime close = row.closesAt() == null ? defaultClose : row.closesAt();
   normalized.add(new DayWindowRow(row.day().trim().toUpperCase(), row.open(), open, close));
  }
  return normalized;
 }

 private List<ClosedDate> normalizeClosedDates(List<ClosedDate> dates) {
  if (dates == null) return List.of();
  Set<LocalDate> seen = new HashSet<>();
  List<ClosedDate> out = new ArrayList<>();
  for (ClosedDate item : dates) {
   if (item == null || item.date() == null) continue;
   if (!seen.add(item.date())) throw new IllegalArgumentException("ပိတ်ရက် ရက်စွဲ ထပ်နေသည်");
   String reason = item.reason() == null ? null : item.reason().trim();
   if (reason != null && reason.length() > 200) throw new IllegalArgumentException("ပိတ်ရက် အကြောင်းပြချက် အလွန်ရှည်နေသည်");
   out.add(new ClosedDate(item.date(), reason == null || reason.isBlank() ? null : reason));
  }
  out.sort((a,b) -> a.date().compareTo(b.date()));
  return out;
 }

 private List<DayWindowRow> readWeekdays(String json) {
  if (json == null || json.isBlank()) return List.of();
  try {
   return objectMapper.readValue(json, new TypeReference<>() {});
  } catch (Exception ex) {
   return List.of();
  }
 }

 private List<ClosedDate> readClosedDates(String json) {
  if (json == null || json.isBlank()) return List.of();
  try {
   return objectMapper.readValue(json, new TypeReference<>() {});
  } catch (Exception ex) {
   return List.of();
  }
 }

 private String writeJson(Object value) {
  try {
   return objectMapper.writeValueAsString(value == null ? List.of() : value);
  } catch (Exception ex) {
   throw new IllegalStateException("Delivery schedule သိမ်းမရပါ");
  }
 }

 private static LocalTime time(Time sql, LocalTime fallback) {
  return sql == null ? fallback : sql.toLocalTime();
 }
}
