package org.sspd.servicemgmt.integration;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerPortalOrderRequest;
import org.sspd.servicemgmt.customerportaloptions.service.DeliveryPricingService;
import org.sspd.servicemgmt.support.AbstractMysqlIntegrationTest;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("it") @Transactional @Rollback
class DeliveryPricingIT extends AbstractMysqlIntegrationTest {
 @Autowired DeliveryPricingService pricing; @Autowired JdbcTemplate jdbc;
 private int wardId, productId;
 @BeforeEach void fixture() {
  String suffix=UUID.randomUUID().toString().substring(0,8);
  String regionName="shipping-region-"+suffix;
  jdbc.update("insert into customer_delivery_regions(name,kind,active,sort_order,created_at,updated_at) values (?,'STATE',true,0,now(6),now(6))",regionName);
  int regionId=jdbc.queryForObject("select id from customer_delivery_regions where name=?",Integer.class,regionName);
  jdbc.update("insert into customer_delivery_townships(region_id,name,delivery_charge,active,sort_order,created_at,updated_at) values (?, ?, 3000, true,0,now(6),now(6))",regionId,"shipping-township-"+suffix);
  int townshipId=jdbc.queryForObject("select id from customer_delivery_townships where region_id=? and name=?",Integer.class,regionId,"shipping-township-"+suffix);
  jdbc.update("insert into customer_delivery_wards(township_id,name,delivery_charge,active,sort_order,created_at,updated_at) values (?, ?, 3000, true,0,now(6),now(6))",townshipId,"shipping-ward-"+suffix);
  wardId=jdbc.queryForObject("select id from customer_delivery_wards where township_id=? and name=?",Integer.class,townshipId,"shipping-ward-"+suffix);
  jdbc.update("insert into products(name,product_code,product_type,archived,has_serial,stock_qty,quarantined_qty,customer_reserved_qty,reorder_level,warranty_months,version,selling_price) values (?,?,'New',false,false,20,0,0,0,0,0,1000)","shipping-test-"+suffix,"SHIP-"+suffix);
  productId=jdbc.queryForObject("select id from products where product_code=?",Integer.class,"SHIP-"+suffix);
    pricing.savePolicy(new DeliveryPricingService.Policy(new BigDecimal("2.000"),new BigDecimal("1000.00"),new BigDecimal("20.000"),10,true));
 }
 @Test void standardUsesBasePlusRoundedExcessWeight() {
  pricing.saveProfile(productId,new DeliveryPricingService.Profile(new BigDecimal("1.250"),"STANDARD",8));
  var q=pricing.quote(request(3));
  BigDecimal base=jdbc.queryForObject("select delivery_charge from customer_delivery_wards where id=?",BigDecimal.class,wardId);
  assertEquals("QUOTED",q.state()); assertEquals(0,new BigDecimal("3.750").compareTo(q.weightKg()));
  assertEquals(0,new BigDecimal("2000.00").compareTo(q.extraCharge()));
  assertEquals(0,base.add(new BigDecimal("2000.00")).compareTo(q.deliveryCharge()));
 }
 @Test void missingProfileStillQuotesWardCharge() {
  BigDecimal base=jdbc.queryForObject("select delivery_charge from customer_delivery_wards where id=?",BigDecimal.class,wardId);
  var missing=pricing.quote(request(1));
  assertEquals("QUOTED",missing.state());
  assertEquals(0,base.compareTo(missing.deliveryCharge()));
  pricing.saveProfile(productId,new DeliveryPricingService.Profile(new BigDecimal("1.000"),"BULKY",null));
  var bulky=pricing.quote(request(1));
  assertEquals("QUOTED",bulky.state());
  assertNotNull(bulky.deliveryCharge());
  pricing.saveProfile(productId,new DeliveryPricingService.Profile(new BigDecimal("1.000"),"STANDARD",2));
  var q=pricing.quote(request(3));
  assertEquals("QUOTED",q.state());
  assertTrue(q.reason().contains("quantity"));
  assertNotNull(q.deliveryCharge());
 }
 @Test void pickupIsAcceptedAtZeroCharge() {
  var r=request(2);r.setOrderType("PICKUP");var q=pricing.quote(r);
  assertEquals("ACCEPTED",q.state());assertEquals(0,BigDecimal.ZERO.compareTo(q.deliveryCharge()));
 }
 private CustomerPortalOrderRequest request(int qty) {
  var line=new CustomerPortalOrderRequest.Line();line.setProductId(productId);line.setQty(qty);
  var r=new CustomerPortalOrderRequest();r.setOrderType("DELIVERY");r.setWardId(wardId);r.setLines(List.of(line));return r;
 }
}
