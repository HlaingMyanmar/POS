package org.sspd.servicemgmt.customerportaloptions.dto;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Map;
import java.util.List;
@Data public class OrderPaymentRequest {
 private Integer paymentMethodId;
 /** TRANSFER or PAY_ON_COLLECTION */
 private String paymentChoice;
 /** OWN or HANDOFF — required when reserving a DELIVERY order. */
 private String deliveryHandler;
 private String instructions;
 private Integer holdMinutes;
 private String action;
 private String note;
 /** Outbound refund / settlement transaction number. */
 private String transactionNo;
 private BigDecimal amount;
 private Integer staffId;
 private Map<Integer,List<String>> serialNumbers;
}
