package org.sspd.servicemgmt.customerportaloptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.customeroptions.model.Customer;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerOrderRatingDTO;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerOrderRatingModerateRequest;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerOrderRatingRequest;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerPortalNotificationDTO;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerPortalOrderDTO;
import org.sspd.servicemgmt.customerportaloptions.model.CustomerOrder;
import org.sspd.servicemgmt.customerportaloptions.model.CustomerOrderLine;
import org.sspd.servicemgmt.customerportaloptions.model.CustomerOrderRating;
import org.sspd.servicemgmt.customerportaloptions.model.CustomerOrderRatingLine;
import org.sspd.servicemgmt.customerportaloptions.repository.CustomerOrderRatingRepository;
import org.sspd.servicemgmt.customerportaloptions.repository.CustomerOrderRepository;
import org.sspd.servicemgmt.customerportaloptions.support.CustomerPortalAuth;
import org.sspd.servicemgmt.dataevent.DataEventPublisher;
import org.sspd.servicemgmt.exceptionhandler.ResourceNotFoundException;
import org.sspd.servicemgmt.saleoptions.model.Sale;
import org.sspd.servicemgmt.saleoptions.repository.SaleRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CustomerOrderRatingService {

    private final CustomerOrderRatingRepository ratings;
    private final CustomerOrderRepository orders;
    private final SaleRepository sales;
    private final DataEventPublisher events;

    @Value("${app.customer-rating.edit-days:7}")
    private int editDays;

    @Transactional
    public CustomerOrderRatingDTO rate(Integer orderId, CustomerOrderRatingRequest request) {
        if (orderId == null) throw new IllegalArgumentException("Order လိုအပ်သည်");
        int customerId = CustomerPortalAuth.require().getCustomerId();
        CustomerOrder order = orders.findByIdWithLines(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        if (!order.getCustomer().getId().equals(customerId)) throw new AccessDeniedException("Not your order");
        if (!"CONFIRMED".equalsIgnoreCase(blankToNone(order.getCustomerReceiptState()))) {
            throw new IllegalStateException("ပစ္စည်းလက်ခံအတည်ပြုပြီးမှသာ အဆင့်ပေးနိုင်သည်");
        }

        int productRating = stars(firstNonNull(request.getProductRating(), request.getRating()), "ပစ္စည်းအဆင့်");
        int serviceRating = stars(firstNonNull(request.getServiceRating(), request.getRating()), "ပို့ဆောင်မှု/ဝန်ဆောင်မှု အဆင့်");
        String comment = firstNonBlank(request.getComment(), request.getReview());
        if (comment != null && comment.length() > 1000) throw new IllegalArgumentException("မှတ်ချက် ၁၀၀၀ လုံးထက် မကျော်ရ");

        Integer saleId = order.getCompletedSaleId();
        Sale sale = saleId == null ? null : sales.findById(saleId).orElse(null);
        CustomerOrderRating existing = ratings.findByOrderId(orderId).orElse(null);
        LocalDateTime now = LocalDateTime.now();
        Customer customer = order.getCustomer();
        if (existing == null) {
            existing = CustomerOrderRating.builder()
                    .orderId(order.getId())
                    .saleId(sale == null ? saleId : sale.getId())
                    .customer(customer)
                    .createdAt(now)
                    .editableUntil(now.plusDays(Math.max(0, editDays)))
                    .build();
        } else {
            if (!existing.getCustomer().getId().equals(customerId)) throw new AccessDeniedException("Not your rating");
            if (existing.getEditableUntil() != null && now.isAfter(existing.getEditableUntil())) {
                throw new IllegalStateException("ပြင်နိုင်သောကာလ ကုန်ဆုံးပါပြီ");
            }
        }
        existing.setProductRating(productRating);
        existing.setServiceRating(serviceRating);
        existing.setRating((int) Math.round((productRating + serviceRating) / 2.0));
        existing.setReview(comment);
        existing.setUpdatedAt(now);
        if (existing.getLines() == null) existing.setLines(new ArrayList<>());
        applyProductLines(existing, order, request);
        existing = ratings.saveAndFlush(existing);

        CustomerPortalNotificationDTO dto = new CustomerPortalNotificationDTO();
        dto.setId(-existing.getId());
        dto.setOrderId(existing.getOrderId());
        dto.setOrderNo(order.getOrderNo());
        dto.setStatus("RATED");
        dto.setChannel("CUSTOMER_RATING");
        dto.setNote(order.getOrderNo() + " — ပစ္စည်း " + productRating + " / ပို့ဆောင် " + serviceRating);
        dto.setNotifiedAt(now);
        events.publishCustomerOrder("CUSTOMER_RATING_UPDATED", existing.getOrderId());
        events.publishToUser(CustomerPortalAuth.usernameForCustomer(customerId), "/topic/customer-orders", dto);
        return toDto(existing, order, sale, now);
    }

    @Transactional
    public CustomerOrderRatingDTO rateSale(Integer saleId, CustomerOrderRatingRequest request) {
        CustomerOrder order = orders.findFirstByCompletedSaleId(saleId)
                .orElseThrow(() -> new IllegalStateException("ဤ Sale နှင့် ချိတ်ဆက်သော အော်ဒါ မရှိပါ"));
        return rate(order.getId(), request);
    }

    @Transactional
    public CustomerOrderRatingDTO moderate(Integer id, CustomerOrderRatingModerateRequest request) {
        CustomerOrderRating entity = ratings.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Rating not found"));
        LocalDateTime now = LocalDateTime.now();
        entity.setHidden(request.isHidden());
        if (request.isHidden()) {
            entity.setHiddenBy(currentUsername());
            entity.setHiddenAt(now);
            String reason = request.getReason() == null ? null : request.getReason().trim();
            entity.setHideReason(reason == null || reason.isBlank() ? null : reason);
        } else {
            entity.setHiddenBy(null);
            entity.setHiddenAt(null);
            entity.setHideReason(null);
        }
        entity.setUpdatedAt(now);
        entity = ratings.saveAndFlush(entity);
        events.publishCustomerOrder("CUSTOMER_RATING_MODERATED", entity.getOrderId());
        return toDto(entity, lookupOrder(entity.getOrderId()), null, now);
    }

    @Transactional(readOnly = true)
    public CustomerOrderRatingDTO forOrder(Integer orderId) {
        int customerId = CustomerPortalAuth.require().getCustomerId();
        CustomerOrder order = orders.findByIdWithLines(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        if (!order.getCustomer().getId().equals(customerId)) throw new AccessDeniedException("Not your order");
        return ratings.findByOrderId(orderId)
                .map(r -> toDto(r, order, null, LocalDateTime.now()))
                .orElseThrow(() -> new ResourceNotFoundException("Rating not found"));
    }

    @Transactional(readOnly = true)
    public List<CustomerOrderRatingDTO> mine() {
        int customerId = CustomerPortalAuth.require().getCustomerId();
        LocalDateTime now = LocalDateTime.now();
        return ratings.findByCustomer_IdOrderByIdDesc(customerId).stream()
                .map(r -> toDto(r, lookupOrder(r.getOrderId()), null, now))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CustomerOrderRatingDTO> shopList() {
        LocalDateTime now = LocalDateTime.now();
        return ratings.findAllByOrderByIdDesc().stream()
                .map(r -> toDto(r, lookupOrder(r.getOrderId()), null, now))
                .toList();
    }

    public void attachToOrder(CustomerPortalOrderDTO dto, CustomerOrder order) {
        boolean confirmed = "CONFIRMED".equalsIgnoreCase(blankToNone(order.getCustomerReceiptState()));
        CustomerOrderRating existing = order.getId() == null ? null : ratings.findByOrderId(order.getId()).orElse(null);
        LocalDateTime now = LocalDateTime.now();
        dto.setCanRate(confirmed && existing == null);
        dto.setCanEditRating(confirmed && existing != null && editable(existing, now));
        if (existing != null) dto.setRating(toDto(existing, order, null, now));
    }

    private void applyProductLines(CustomerOrderRating entity, CustomerOrder order, CustomerOrderRatingRequest request) {
        if (request.getProducts() == null) return;
        Map<Integer, String> names = new HashMap<>();
        if (order.getLines() != null) {
            for (CustomerOrderLine line : order.getLines()) {
                if (line.getProduct() != null) {
                    String name = line.getProductName() != null ? line.getProductName() : line.getProduct().getName();
                    names.put(line.getProduct().getId(), name);
                }
            }
        }
        entity.getLines().clear();
        for (CustomerOrderRatingRequest.Line wanted : request.getProducts()) {
            if (wanted.getProductId() == null) continue;
            if (!names.containsKey(wanted.getProductId())) {
                throw new IllegalArgumentException("ဤအော်ဒါတွင် မပါသော ပစ္စည်းကို အဆင့်မပေးနိုင်ပါ");
            }
            int stars = stars(wanted.getRating(), "ပစ္စည်းအဆင့်");
            CustomerOrderRatingLine line = CustomerOrderRatingLine.builder()
                    .orderRating(entity)
                    .productId(wanted.getProductId())
                    .productName(names.get(wanted.getProductId()))
                    .rating(stars)
                    .build();
            entity.getLines().add(line);
        }
    }

    private CustomerOrderRatingDTO toDto(CustomerOrderRating entity, CustomerOrder order, Sale sale, LocalDateTime now) {
        CustomerOrderRatingDTO dto = new CustomerOrderRatingDTO();
        dto.setId(entity.getId());
        dto.setOrderId(entity.getOrderId());
        dto.setSaleId(entity.getSaleId());
        dto.setCustomerId(entity.getCustomer().getId());
        dto.setCustomerName(entity.getCustomer().getName());
        dto.setRating(entity.getRating());
        dto.setProductRating(entity.getProductRating());
        dto.setServiceRating(entity.getServiceRating());
        dto.setComment(entity.getReview());
        dto.setReview(entity.getReview());
        dto.setHidden(entity.isHidden());
        dto.setHiddenBy(entity.getHiddenBy());
        dto.setHiddenAt(entity.getHiddenAt());
        dto.setHideReason(entity.getHideReason());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        dto.setEditableUntil(entity.getEditableUntil());
        dto.setEditable(editable(entity, now));
        if (order != null) {
            dto.setOrderNo(order.getOrderNo());
            dto.setOrderType(order.getOrderType());
        } else if (sale != null) {
            dto.setOrderNo(sale.getSaleCode());
        }
        if (entity.getLines() != null) {
            for (CustomerOrderRatingLine line : entity.getLines()) {
                CustomerOrderRatingDTO.Line dtoLine = new CustomerOrderRatingDTO.Line();
                dtoLine.setProductId(line.getProductId());
                dtoLine.setProductName(line.getProductName());
                dtoLine.setRating(line.getRating());
                dto.getProducts().add(dtoLine);
            }
        }
        return dto;
    }

    private CustomerOrder lookupOrder(Integer orderId) {
        if (orderId == null) return null;
        return orders.findById(orderId).orElse(null);
    }

    private static boolean editable(CustomerOrderRating entity, LocalDateTime now) {
        return entity.getEditableUntil() == null || !now.isAfter(entity.getEditableUntil());
    }

    private static int stars(Integer value, String label) {
        if (value == null || value < 1 || value > 5) {
            throw new IllegalArgumentException(label + " 1 မှ 5 ထိ ပေးပါ");
        }
        return value;
    }

    private static Integer firstNonNull(Integer a, Integer b) {
        return a != null ? a : b;
    }

    private static String firstNonBlank(String a, String b) {
        String left = a == null ? null : a.trim();
        if (left != null && !left.isBlank()) return left;
        String right = b == null ? null : b.trim();
        if (right != null && !right.isBlank()) return right;
        return null;
    }

    private static String blankToNone(String value) {
        return value == null || value.isBlank() ? "NONE" : value.trim();
    }

    private static String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null || auth.getName() == null || auth.getName().isBlank() ? "SYSTEM" : auth.getName();
    }
}
