package org.sspd.servicemgmt.customerportaloptions.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.customeroptions.repository.CustomerRepository;
import org.sspd.servicemgmt.customerportaloptions.dto.*;
import org.sspd.servicemgmt.customerportaloptions.model.CustomerPushDevice;
import org.sspd.servicemgmt.customerportaloptions.repository.CustomerPushDeviceRepository;
import org.sspd.servicemgmt.customerportaloptions.support.CustomerPortalAuth;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

@Service @Slf4j
public class CustomerFcmService {
 private final CustomerPushDeviceRepository devices; private final CustomerRepository customers;
 private final ObjectMapper json; private final HttpClient http=HttpClient.newHttpClient();
 private final ExecutorService executor=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"customer-fcm-push");t.setDaemon(true);return t;});
 private final Credentials credentials; private volatile AccessToken accessToken;
 record Credentials(String projectId,String clientEmail,String privateKey,String tokenUri){}
 record AccessToken(String value,Instant expiresAt){}

 public CustomerFcmService(CustomerPushDeviceRepository devices,CustomerRepository customers,ObjectMapper json,
  @Value("${app.fcm.credentials-file:}") String file,@Value("${app.fcm.credentials-base64:}") String base64){
  this.devices=devices;this.customers=customers;this.json=json;this.credentials=load(file,base64);
 }
 private Credentials load(String file,String base64){
  try{
   byte[] raw=null;
   if(!blank(base64)) raw=Base64.getDecoder().decode(base64.replaceAll("\\s",""));
   else if(!blank(file)&&Files.isRegularFile(Path.of(file))) raw=Files.readAllBytes(Path.of(file));
   if(raw==null){log.info("FCM disabled: add Firebase service-account JSON at APP_FCM_CREDENTIALS_FILE (not google-services.json)");return null;}
   var node=json.readTree(raw);
   if(blank(node.path("private_key").asText())||blank(node.path("client_email").asText())||blank(node.path("project_id").asText())){
    log.error("FCM disabled: credentials must be a Firebase service-account private key, not google-services.json");
    return null;
   }
   return new Credentials(node.path("project_id").asText(),node.path("client_email").asText(),node.path("private_key").asText(),node.path("token_uri").asText("https://oauth2.googleapis.com/token"));
  }catch(Exception e){log.error("FCM credentials are invalid; push disabled",e);return null;}
 }

 @Transactional public void register(CustomerPushTokenRequest request){
  String token=token(request);Integer id=CustomerPortalAuth.require().getCustomerId();var now=LocalDateTime.now();
  var device=devices.findByToken(token).orElseGet(()->CustomerPushDevice.builder().token(token).createdAt(now).build());
  device.setCustomer(customers.findById(id).orElseThrow());device.setPlatform(platform(request.platform()));device.setActive(true);
  device.setUpdatedAt(now);device.setLastSeenAt(now);devices.save(device);
 }
 @Transactional public void unregister(CustomerPushTokenRequest request){
  String token=token(request);Integer id=CustomerPortalAuth.require().getCustomerId();
  devices.findByToken(token).filter(d->d.getCustomer().getId().equals(id)).ifPresent(d->{d.setActive(false);d.setUpdatedAt(LocalDateTime.now());devices.save(d);});
 }
 public void sendAsync(Integer id,CustomerPortalNotificationDTO notice){if(credentials!=null&&id!=null&&notice!=null)executor.execute(()->send(id,notice));}
 private void send(Integer id,CustomerPortalNotificationDTO notice){
  String body=blank(notice.getNote())?"အော်ဒါအခြေအနေ အသစ်ရှိပါသည်":notice.getNote();
  for(var device:devices.findByCustomer_IdAndActiveTrue(id))try{
   Map<String,Object> data=new LinkedHashMap<>();data.put("type","CUSTOMER_ORDER");data.put("orderId",Objects.toString(notice.getOrderId(),""));
   data.put("status",Objects.toString(notice.getStatus(),""));data.put("orderNo",Objects.toString(notice.getOrderNo(),""));data.put("note",body);
   data.put("notifiedAt",Objects.toString(notice.getNotifiedAt(),""));
   Map<String,Object> message=new LinkedHashMap<>();message.put("token",device.getToken());
   message.put("notification",Map.of("title",Objects.toString(notice.getOrderNo(),"SSPD Customer"),"body",body));message.put("data",data);
   message.put("android",Map.of(
     "priority","HIGH",
     "notification",Map.of(
       "channel_id","customer_orders",
       "click_action","OPEN_CUSTOMER_ORDER"
     )));
   var request=HttpRequest.newBuilder(URI.create("https://fcm.googleapis.com/v1/projects/"+credentials.projectId()+"/messages:send"))
    .header("Authorization","Bearer "+accessToken()).header("Content-Type","application/json")
    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("message",message)))).build();
   var response=http.send(request,HttpResponse.BodyHandlers.ofString());
   if(response.statusCode()/100!=2){if(response.statusCode()==404||response.body().contains("UNREGISTERED")){device.setActive(false);devices.save(device);}else log.warn("FCM rejected message: {}",response.statusCode());}
  }catch(Exception e){log.warn("FCM send failed for customer {}: {}",id,e.getMessage());}
 }

 public void sendTechnicianAlertAsync(Integer staffId){
  if(credentials==null||staffId==null||staffId<=0)return;
  executor.execute(()->sendTechnicianAlert(staffId));
 }
 private void sendTechnicianAlert(Integer staffId){
  try{
   Map<String,Object> message=new LinkedHashMap<>();message.put("topic","technician_staff_"+staffId);
   message.put("notification",Map.of("title","New service job","body","Open Technician App to view your assigned jobs"));
   message.put("data",Map.of("type","TECHNICIAN_JOB_REFRESH"));
   message.put("android",Map.of("priority","HIGH","notification",Map.of("channel_id","technician_new_jobs")));
   var request=HttpRequest.newBuilder(URI.create("https://fcm.googleapis.com/v1/projects/"+credentials.projectId()+"/messages:send"))
    .header("Authorization","Bearer "+accessToken()).header("Content-Type","application/json")
    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("message",message)))).build();
   var response=http.send(request,HttpResponse.BodyHandlers.ofString());
   if(response.statusCode()/100!=2)log.warn("FCM rejected technician alert: {}",response.statusCode());
  }catch(Exception e){log.warn("FCM technician alert failed: {}",e.getMessage());}
 }
 private synchronized String accessToken()throws Exception{
  if(accessToken!=null&&accessToken.expiresAt().isAfter(Instant.now().plusSeconds(60)))return accessToken.value();
  long now=Instant.now().getEpochSecond();String header=b64(json.writeValueAsBytes(Map.of("alg","RS256","typ","JWT")));
  String claims=b64(json.writeValueAsBytes(Map.of("iss",credentials.clientEmail(),"scope","https://www.googleapis.com/auth/firebase.messaging","aud",credentials.tokenUri(),"iat",now,"exp",now+3600)));
  String unsigned=header+"."+claims;Signature signer=Signature.getInstance("SHA256withRSA");signer.initSign(privateKey());signer.update(unsigned.getBytes(StandardCharsets.US_ASCII));
  String assertion=unsigned+"."+b64(signer.sign());String form="grant_type="+URLEncoder.encode("urn:ietf:params:oauth:grant-type:jwt-bearer",StandardCharsets.UTF_8)+"&assertion="+URLEncoder.encode(assertion,StandardCharsets.UTF_8);
  var request=HttpRequest.newBuilder(URI.create(credentials.tokenUri())).header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(form)).build();
  var response=http.send(request,HttpResponse.BodyHandlers.ofString());if(response.statusCode()/100!=2)throw new IllegalStateException("FCM OAuth failed: "+response.statusCode());
  var node=json.readTree(response.body());accessToken=new AccessToken(node.path("access_token").asText(),Instant.now().plusSeconds(node.path("expires_in").asLong(3600)));return accessToken.value();
 }
 private PrivateKey privateKey()throws Exception{String pem=credentials.privateKey().replace("-----BEGIN PRIVATE KEY-----","").replace("-----END PRIVATE KEY-----","").replaceAll("\\s","");return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)));}
 private static String b64(byte[] value){return Base64.getUrlEncoder().withoutPadding().encodeToString(value);}
 private static String token(CustomerPushTokenRequest r){String v=r==null||r.token()==null?"":r.token().trim();if(v.length()<20||v.length()>512)throw new IllegalArgumentException("Invalid push token");return v;}
 private static String platform(String v){String p=blank(v)?"ANDROID":v.trim().toUpperCase(Locale.ROOT);if(!Set.of("ANDROID","IOS","WEB").contains(p))throw new IllegalArgumentException("Invalid push platform");return p;}
 private static boolean blank(String v){return v==null||v.isBlank();}
}
