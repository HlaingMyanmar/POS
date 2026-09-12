package org.sspd.servicemgmt.customerportaloptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerPortalOrderRequest;
import org.sspd.servicemgmt.customerportaloptions.repository.CustomerDeliveryWardRepository;
import java.math.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DeliveryPricingService {
 private final JdbcTemplate jdbc;
 private final CustomerDeliveryWardRepository wards;
 public record Policy(BigDecimal includedKg, BigDecimal extraPerKg, BigDecimal maxAutoKg, Integer maxAutoQty,
                     Boolean deliveryEnabled) {
  public boolean isDeliveryEnabled() { return !Boolean.FALSE.equals(deliveryEnabled); }
 }
 public record Profile(BigDecimal weightKg, String shippingClass, Integer maxAutoQty) {}
 public record Quote(String state, BigDecimal weightKg, BigDecimal baseCharge, BigDecimal extraCharge,
                     BigDecimal deliveryCharge, String reason, Policy policy) {}
 @Transactional(readOnly=true) public Policy policy() {
  return jdbc.queryForObject("select * from delivery_pricing_policy where id=1",(r,rowNum)->new Policy(
   r.getBigDecimal("included_kg"),r.getBigDecimal("extra_per_kg"),r.getBigDecimal("max_auto_kg"),
   (Integer)r.getObject("max_auto_qty"), r.getObject("delivery_enabled") instanceof Number flag ? flag.intValue()!=0 : Boolean.TRUE));
 }
 @Transactional public void savePolicy(Policy p) {
  if(p==null || !valid(p.includedKg(),false,3) || !valid(p.extraPerKg(),false,2) || !valid(p.maxAutoKg(),true,3)
    || p.maxAutoKg().compareTo(p.includedKg())<0 || p.maxAutoQty()==null || p.maxAutoQty()<1 || p.maxAutoQty()>100000)
    throw new IllegalArgumentException("Enter valid included kg, extra rate, maximum kg and quantity");
  boolean enabled = p.deliveryEnabled()==null || p.isDeliveryEnabled();
  jdbc.update("update delivery_pricing_policy set included_kg=?,extra_per_kg=?,max_auto_kg=?,max_auto_qty=?,delivery_enabled=? where id=1",
   p.includedKg(),p.extraPerKg(),p.maxAutoKg(),p.maxAutoQty(),enabled);
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
  if(req.getWardId()==null)throw new IllegalArgumentException("Choose a ward");
  var ward=wards.findWithTownshipAndRegionById(req.getWardId()).orElseThrow(()->new IllegalArgumentException("Ward not found"));
  if(!Boolean.TRUE.equals(ward.getActive()) || !Boolean.TRUE.equals(ward.getTownship().getActive())
    || !Boolean.TRUE.equals(ward.getTownship().getRegion().getActive()))throw new IllegalArgumentException("Delivery area unavailable");
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
  BigDecimal base=ward.getDeliveryCharge();
  if(base==null || base.signum()<0)throw new IllegalStateException("Invalid ward delivery charge");
  BigDecimal extra=BigDecimal.ZERO;
  if(known && policy.includedKg()!=null && policy.extraPerKg()!=null) {
   extra=weight.subtract(policy.includedKg()).max(BigDecimal.ZERO).setScale(0,RoundingMode.CEILING).multiply(policy.extraPerKg());
  }
  String reason=reasons.isEmpty()
   ? (known ? "Weight-based delivery" : "Ward delivery")
   : String.join("; ",reasons).substring(0,Math.min(950,String.join("; ",reasons).length()));
  return new Quote("QUOTED",known?weight:null,base,extra,base.add(extra),reason,policy);
 }
}
