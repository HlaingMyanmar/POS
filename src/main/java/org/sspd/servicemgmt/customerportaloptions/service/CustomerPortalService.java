package org.sspd.servicemgmt.customerportaloptions.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.sspd.servicemgmt.bookingoptions.dto.BookingDTO;
import org.sspd.servicemgmt.bookingoptions.repository.BookingRepository;
import org.sspd.servicemgmt.bookingoptions.service.BookingService;
import org.sspd.servicemgmt.companysettingoptions.service.CompanySettingsService;
import org.sspd.servicemgmt.customeroptions.model.Customer;
import org.sspd.servicemgmt.customeroptions.repository.CustomerRepository;
import org.sspd.servicemgmt.brandoptions.repository.BrandRepository;
import org.sspd.servicemgmt.categoryoptions.repository.CategoryRepository;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerCatalogOptionDTO;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerCatalogProductDTO;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerCatalogServiceDTO;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerDeliveryRegionDTO;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerDeliveryTownshipDTO;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerDeliveryWardDTO;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerOrderDeliveryUpdateRequest;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerPortalBookingRequest;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerPortalJobDTO;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerPortalOrderDTO;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerPortalOrderRequest;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerReceiptConfirmRequest;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerPortalPurchaseDTO;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerPortalNotificationDTO;
import org.sspd.servicemgmt.customerportaloptions.dto.OrderPaymentRequest;
import org.sspd.servicemgmt.customerportaloptions.model.CustomerDeliveryRegion;
import org.sspd.servicemgmt.customerportaloptions.model.CustomerDeliveryTownship;
import org.sspd.servicemgmt.customerportaloptions.model.CustomerDeliveryWard;
import org.sspd.servicemgmt.customerportaloptions.model.CustomerOrder;
import org.sspd.servicemgmt.customerportaloptions.model.CustomerOrderLine;
import org.sspd.servicemgmt.customerportaloptions.model.CustomerOrderStatus;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerAppAccountDTO;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerAppActivityDTO;
import org.sspd.servicemgmt.customerportaloptions.model.CustomerAppAccount;
import org.sspd.servicemgmt.customerportaloptions.repository.CustomerAppAccountRepository;
import org.sspd.servicemgmt.customerportaloptions.repository.CustomerDeliveryRegionRepository;
import org.sspd.servicemgmt.customerportaloptions.repository.CustomerDeliveryTownshipRepository;
import org.sspd.servicemgmt.customerportaloptions.repository.CustomerDeliveryWardRepository;
import org.sspd.servicemgmt.customerportaloptions.model.CustomerDeliveryMilestone;
import org.sspd.servicemgmt.customerportaloptions.repository.CustomerDeliveryMilestoneRepository;
import org.sspd.servicemgmt.customerportaloptions.repository.CustomerOrderRepository;
import org.sspd.servicemgmt.customerportaloptions.support.CustomerOrderDeliveryRules;
import org.sspd.servicemgmt.customerportaloptions.support.CustomerPortalAuth;
import org.sspd.servicemgmt.dataevent.DataEventPublisher;
import org.sspd.servicemgmt.exceptionhandler.ResourceNotFoundException;
import org.sspd.servicemgmt.saleoptions.model.Sale;
import org.sspd.servicemgmt.saleoptions.repository.SaleRepository;
import org.sspd.servicemgmt.saleoptions.saledetails.model.SaleDetail;
import org.sspd.servicemgmt.servicejoboptions.model.ServiceJob;
import org.sspd.servicemgmt.servicejoboptions.model.ServiceJobLine;
import org.sspd.servicemgmt.servicejoboptions.model.ServiceJobPart;
import org.sspd.servicemgmt.servicejoboptions.repository.ServiceJobRepository;
import org.sspd.servicemgmt.servicejoboptions.repository.ServiceJobNotificationRepository;
import org.sspd.servicemgmt.serviceoptions.repository.ServiceItemRepository;
import org.sspd.servicemgmt.stockoptions.productoptions.model.Product;
import org.sspd.servicemgmt.stockoptions.productoptions.model.ProductPhoto;
import org.sspd.servicemgmt.stockoptions.productoptions.repository.ProductRepository;
import org.sspd.servicemgmt.stockoptions.productserialoptions.enums.SerialStatus;
import org.sspd.servicemgmt.stockoptions.productserialoptions.repository.ProductSerialRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerPortalService {

    private final ProductRepository productRepository;
    private final ProductSerialRepository productSerialRepository;
    private final ServiceItemRepository serviceItemRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final CustomerRepository customerRepository;
    private final CustomerPortalAuthService authService;
    private final BookingService bookingService;
    private final BookingRepository bookingRepository;
    private final CustomerOrderRepository orderRepository;
    private final CustomerDeliveryTownshipRepository townshipRepository;
    private final CustomerDeliveryWardRepository wardRepository;
    private final CustomerDeliveryRegionRepository regionRepository;
    private final SaleRepository saleRepository;
    private final org.sspd.servicemgmt.saleoptions.warranty.SaleWarrantyQueryService saleWarrantyQuery;
    private final ServiceJobRepository serviceJobRepository;
    private final ServiceJobNotificationRepository serviceJobNotificationRepository;
    private final CustomerAppAccountRepository accountRepository;
    private final CustomerAppActivityService activityService;
    private final DataEventPublisher dataEventPublisher;
    private final CustomerOrderPaymentService orderPayments;
    private final DeliveryPricingService deliveryPricing;
    private final CompanySettingsService companySettingsService;
    private final PlatformTransactionManager transactionManager;
    private final org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.repository.PaymentMethodRepository paymentMethodRepository;
    private final CustomerOrderRatingService orderRatings;
    private final CustomerPromoService promos;
    private final CustomerDeliveryMilestoneRepository deliveryMilestones;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<CustomerCatalogProductDTO> catalogProducts() {
        return productRepository.findAll().stream()
                .filter(p -> !Boolean.TRUE.equals(p.getArchived()))
                .map(this::toCatalogProduct)
                .toList();
    }

    private final org.sspd.servicemgmt.customerportaloptions.repository.CustomerCatalogRepository catalogRepository;

    @Transactional(readOnly = true)
    public org.sspd.servicemgmt.customerportaloptions.dto.CustomerCatalogPageDTO catalogPage(
            int page, int size, String q, Integer categoryId, Integer brandId, String productType, String sort) {
        return catalogRepository.search(page, size, q, categoryId, brandId, productType, sort);
    }

    /** Same active Category names as web Category master (includes parent for sub-categories). */
    @Transactional(readOnly = true)
    public List<CustomerCatalogOptionDTO> catalogCategories() {
        return categoryRepository.findAllByIsActiveTrue().stream()
                .filter(c -> c.getName() != null && !c.getName().isBlank())
                .map(c -> {
                    CustomerCatalogOptionDTO dto = new CustomerCatalogOptionDTO();
                    dto.setId(c.getId());
                    dto.setName(c.getName().trim());
                    if (c.getParent() != null) {
                        dto.setParentId(c.getParent().getId());
                        if (c.getParent().getName() != null) {
                            dto.setParentName(c.getParent().getName().trim());
                        }
                    }
                    return dto;
                })
                .toList();
    }

    /** Same active Brand names as web Brand master. */
    @Transactional(readOnly = true)
    public List<CustomerCatalogOptionDTO> catalogBrands() {
        return brandRepository.findAll().stream()
                .filter(b -> !Boolean.FALSE.equals(b.getIsActive()))
                .filter(b -> b.getName() != null && !b.getName().isBlank())
                .map(b -> {
                    CustomerCatalogOptionDTO dto = new CustomerCatalogOptionDTO();
                    dto.setId(b.getId());
                    dto.setName(b.getName().trim());
                    return dto;
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CustomerCatalogServiceDTO> catalogServices() {
        return serviceItemRepository.findByIsActiveTrue().stream().map(s -> {
            CustomerCatalogServiceDTO dto = new CustomerCatalogServiceDTO();
            dto.setId(s.getId());
            dto.setName(s.getItem());
            dto.setServiceTypeName(s.getServiceType() != null ? s.getServiceType().getName() : null);
            dto.setPrice(s.getPrice());
            dto.setWarrantyMonths(s.getWarrantyMonths());
            dto.setDescription(s.getDescription());
            return dto;
        }).toList();
    }

    @Transactional
    public BookingDTO requestService(CustomerPortalBookingRequest req) {
        var me = CustomerPortalAuth.require();
        authService.requireCompleteProfile(me.getCustomerId());
        if (req == null || blank(req.getProblem()) && blank(req.getServiceName())) {
            throw new IllegalArgumentException("ပြဿနာ သို့မဟုတ် ဝန်ဆောင်မှု ထည့်ပါ");
        }
        StringBuilder note = new StringBuilder();
        if (!blank(req.getServiceName())) note.append("Service: ").append(req.getServiceName().trim());
        if (!blank(req.getDeviceName())) {
            if (!note.isEmpty()) note.append('\n');
            note.append("Device: ").append(req.getDeviceName().trim());
        }
        if (!blank(req.getProblem())) {
            if (!note.isEmpty()) note.append('\n');
            note.append(req.getProblem().trim());
        }
        BookingDTO dto = new BookingDTO();
        dto.setCustomerId(me.getCustomerId());
        dto.setAppointmentDate(req.getAppointmentDate());
        dto.setComplaintNote(note.toString());
        dto.setRemark(blank(req.getRemark()) ? "CUSTOMER_APP" : req.getRemark().trim());
        dto.setSource("CUSTOMER_APP");
        BookingDTO created = bookingService.create(dto);
        accountRepository.findByCustomer_Id(me.getCustomerId()).ifPresent(account ->
                activityService.record(account, "SERVICE_REQUESTED",
                        created.getId() != null ? "Booking #" + created.getId() : note.toString()));
        return created;
    }

    @Transactional(readOnly = true)
    public List<BookingDTO> myBookings() {
        var me = CustomerPortalAuth.require();
        return bookingRepository.findByCustomer_IdOrderByIdDesc(me.getCustomerId()).stream()
                .map(bookingService::toSummaryDto)
                .toList();
    }

    public CustomerPortalOrderDTO placeOrder(CustomerPortalOrderRequest req) {
        var me = CustomerPortalAuth.require();
        authService.requireCompleteProfile(me.getCustomerId());
        if (req == null || req.getLines() == null || req.getLines().isEmpty()) {
            throw new IllegalArgumentException("ပစ္စည်း အနည်းဆုံး တစ်ခု ရွေးပါ");
        }
        String key = requireIdempotencyKey(req);
        var existing = orderRepository.findByCustomer_IdAndIdempotencyKey(me.getCustomerId(), key);
        if (existing.isPresent()) {
            return toOrderDto(existing.get());
        }
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        try {
            CustomerPortalOrderDTO created = tx.execute(status -> createOrder(me.getCustomerId(), req, key));
            if (created != null) {
                return created;
            }
            return replayExistingOrder(me.getCustomerId(), key)
                    .orElseThrow(() -> new IllegalStateException("Order create failed"));
        } catch (RuntimeException ex) {
            if (!causedByDataIntegrity(ex)) {
                throw ex;
            }
            return replayExistingOrder(me.getCustomerId(), key).orElseThrow(() -> ex);
        }
    }

    private java.util.Optional<CustomerPortalOrderDTO> replayExistingOrder(Integer customerId, String key) {
        return orderRepository.findByCustomer_IdAndIdempotencyKey(customerId, key).map(this::toOrderDto);
    }

    private static boolean causedByDataIntegrity(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof DataIntegrityViolationException) {
                return true;
            }
        }
        return false;
    }

    private CustomerPortalOrderDTO createOrder(Integer customerId, CustomerPortalOrderRequest req, String key) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        CustomerOrder order = CustomerOrder.builder()
                .orderNo("TMP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10))
                .customer(customer)
                .status(CustomerOrderStatus.PENDING)
                .note(blank(req.getNote()) ? null : req.getNote().trim())
                .lines(new ArrayList<>())
                .build();
        String choice = req.getPaymentChoice() == null ? "TRANSFER" : req.getPaymentChoice().trim().toUpperCase();
        applyOrderFulfillment(order, customer, req);
        if ("DELIVERY".equalsIgnoreCase(order.getOrderType())) {
            deliveryPricing.requireDeliveryAvailable();
            java.time.LocalDateTime requested = req.getRequestedDeliveryAt();

            deliveryPricing.validateRequestedAt(requested);

            order.setRequestedDeliveryAt(requested);
            order.setDeliveryScheduledAt(requested);
            order.setPaymentChoice("PENDING");
        } else if (!java.util.Set.of("TRANSFER", "PAY_ON_COLLECTION").contains(choice)) {
            throw new IllegalArgumentException("Invalid payment choice");
        } else {
            order.setPaymentChoice(choice);
        }
        order.setIdempotencyKey(key);
        java.util.Set<Integer> productIds = new java.util.HashSet<>();
        for (var line : req.getLines()) if (!productIds.add(line.getProductId())) throw new IllegalArgumentException("Combine duplicate product lines");

        for (CustomerPortalOrderRequest.Line lineReq : req.getLines()) {
            if (lineReq.getProductId() == null) throw new IllegalArgumentException("productId လိုအပ်သည်");
            int qty = lineReq.getQty() == null ? 0 : lineReq.getQty();
            if (qty <= 0) throw new IllegalArgumentException("အရေအတွက် မှန်ကန်စွာ ထည့်ပါ");
            Product product = productRepository.findById(lineReq.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
            if (Boolean.TRUE.equals(product.getArchived())) {
                throw new IllegalArgumentException(product.getName() + " ကို မှာယူ၍ မရပါ");
            }
            BigDecimal price = product.getSellingPrice() == null ? BigDecimal.ZERO : product.getSellingPrice();
            CustomerOrderLine line = CustomerOrderLine.builder()
                    .order(order)
                    .product(product)
                    .productName(product.getName())
                    .qty(qty)
                    .unitPrice(price)
                    .subtotal(price.multiply(BigDecimal.valueOf(qty)))
                    .build();
            order.getLines().add(line);
        }
        BigDecimal itemsTotal = order.getLines().stream()
                .map(CustomerOrderLine::getSubtotal)
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        order.setItemsTotal(itemsTotal);
        if (order.getDeliveryCharge() == null) {
            order.setDeliveryCharge(BigDecimal.ZERO);
        }
        if (order.getQuotedDeliveryCharge() == null) {
            order.setQuotedDeliveryCharge(order.getDeliveryCharge());
        }
        if ("DELIVERY".equals(order.getOrderType())) {
            var quote = deliveryPricing.quote(req);
            if (quote.deliveryCharge() == null) {
                throw new IllegalStateException("ပို့ဆောင်ခ တွက်မရပါ — ရပ်ကွက် ပြန်ရွေးပါ");
            }
            order.setShippingState("AWAITING_SHOP");
            order.setShippingVersion(1);
            order.setShippingWeightKg(quote.weightKg());
            order.setShippingReason(quote.reason());
            order.setDeliveryHandler(null);
            order.setDeliveryCharge(quote.deliveryCharge());
            order.setQuotedDeliveryCharge(quote.deliveryCharge());
            try { order.setShippingSnapshot(objectMapper.writeValueAsString(quote)); }
            catch (JsonProcessingException e) { throw new IllegalStateException(e); }
        }
        CustomerOrder saved = orderRepository.saveAndFlush(order);
        if (!blank(req.getPromoCode())) {
            promos.apply(saved, req.getPromoCode());
        }
        BigDecimal afterDiscount = saved.getItemsTotal() == null ? BigDecimal.ZERO : saved.getItemsTotal();
        if (saved.getDiscountAmount() != null) {
            afterDiscount = afterDiscount.subtract(saved.getDiscountAmount()).max(BigDecimal.ZERO);
        }
        if ("PICKUP".equalsIgnoreCase(saved.getOrderType())) {
            saved.setPaymentChoice("TRANSFER");
            applyOrderDeposit(saved, afterDiscount);
        } else {
            saved.setDepositPercent(null);
            saved.setDepositAmount(null);
        }
        saved.setOrderNo(String.format("CA-%06d", saved.getId()));
        saved = orderRepository.save(saved);
        if ("DELIVERY".equalsIgnoreCase(saved.getOrderType())) {
            recordDeliveryMilestone(saved.getId(), null, "PENDING", "အော်ဒါတင်ပြီး ပို့ဆောင်ရန် စောင့်ဆိုင်း");
        }
        dataEventPublisher.publishCustomerOrder("CUSTOMER_ORDER_CREATED", saved.getId());
        dataEventPublisher.publishToUser(
                CustomerPortalAuth.usernameForCustomer(customerId),
                "/topic/customer-orders",
                toOrderNotification(saved));
        CustomerOrder finalSaved = saved;
        accountRepository.findByCustomer_Id(customerId).ifPresent(account ->
                activityService.record(account, "ORDER_PLACED",
                        finalSaved.getOrderNo() + " (" + finalSaved.getLines().size() + " items)"));
        return attachMilestones(toOrderDto(saved));
    }

    public DeliveryPricingService.Policy deliveryPolicy() { return deliveryPricing.policy(); }

    public boolean deliveryEnabled() {
        return deliveryPricing.policy().isDeliveryEnabled();
    }

    private void applyOrderDeposit(CustomerOrder order, BigDecimal itemsTotal) {
        var settings = companySettingsService.getSettings();
        BigDecimal pct = settings.getPickupDepositPercent() != null
                ? settings.getPickupDepositPercent()
                : new BigDecimal("30.00");
        if (pct.compareTo(BigDecimal.ONE) < 0 || pct.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalStateException("စရံရာခိုင်နှုန်း မှားနေသည်။ Company Settings မှ ပြင်ပါ");
        }
        pct = pct.setScale(2, RoundingMode.HALF_UP);
        BigDecimal deposit = itemsTotal.multiply(pct).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        if (deposit.signum() <= 0 && itemsTotal.signum() > 0) {
            deposit = itemsTotal.min(new BigDecimal("0.01"));
        }
        order.setDepositPercent(pct);
        order.setDepositAmount(deposit);
    }

    private String requireIdempotencyKey(CustomerPortalOrderRequest req) {
        String key = req.getIdempotencyKey() == null ? "" : req.getIdempotencyKey().trim();
        if (key.length() < 8 || key.length() > 64) {
            throw new IllegalArgumentException("idempotencyKey လိုအပ်သည်");
        }
        return key;
    }

    @Transactional(readOnly = true)
    public List<CustomerDeliveryTownshipDTO> activeTownships() {
        return townshipRepository.findByActiveTrueAndRegion_ActiveTrueOrderByRegion_SortOrderAscSortOrderAscNameAsc()
                .stream()
                .map(this::toTownshipDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CustomerDeliveryTownshipDTO> shopTownships() {
        return townshipRepository.findAllByOrderByRegion_SortOrderAscSortOrderAscNameAsc().stream()
                .map(this::toTownshipDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CustomerDeliveryRegionDTO> shopRegions() {
        return regionRepository.findAllByOrderBySortOrderAscNameAsc().stream()
                .map(this::toRegionDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CustomerDeliveryRegionDTO> activeRegions() {
        return regionRepository.findByActiveTrueOrderBySortOrderAscNameAsc().stream()
                .map(this::toRegionDto)
                .toList();
    }

    @Transactional
    public CustomerDeliveryRegionDTO saveRegion(CustomerDeliveryRegionDTO req) {
        if (req == null || blank(req.getName())) {
            throw new IllegalArgumentException("တိုင်း/ပြည်နယ် အမည် လိုအပ်သည်");
        }
        String name = req.getName().trim();
        String kind = blank(req.getKind()) ? "STATE" : req.getKind().trim().toUpperCase();
        if (!"YANGON".equals(kind) && !"STATE".equals(kind)) {
            throw new IllegalArgumentException("Kind သည် YANGON သို့မဟုတ် STATE ဖြစ်ရမည်");
        }
        CustomerDeliveryRegion entity;
        if (req.getId() == null) {
            if (regionRepository.existsByNameIgnoreCase(name)) {
                throw new IllegalArgumentException("တိုင်း/ပြည်နယ် အမည် ရှိပြီးသား ဖြစ်သည်");
            }
            entity = CustomerDeliveryRegion.builder().build();
        } else {
            entity = regionRepository.findById(req.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Region not found"));
            if (regionRepository.existsByNameIgnoreCaseAndIdNot(name, req.getId())) {
                throw new IllegalArgumentException("တိုင်း/ပြည်နယ် အမည် ရှိပြီးသား ဖြစ်သည်");
            }
        }
        entity.setName(name);
        entity.setKind(kind);
        entity.setActive(req.getActive() == null || Boolean.TRUE.equals(req.getActive()));
        entity.setSortOrder(req.getSortOrder() == null ? 0 : req.getSortOrder());
        return toRegionDto(regionRepository.save(entity));
    }

    @Transactional
    public CustomerDeliveryTownshipDTO saveTownship(CustomerDeliveryTownshipDTO req) {
        if (req == null || blank(req.getName())) {
            throw new IllegalArgumentException("မြို့နယ် အမည် လိုအပ်သည်");
        }
        if (req.getRegionId() == null) {
            throw new IllegalArgumentException("တိုင်း/ပြည်နယ် ရွေးပါ");
        }
        CustomerDeliveryRegion region = regionRepository.findById(req.getRegionId())
                .orElseThrow(() -> new ResourceNotFoundException("Region not found"));
        String name = req.getName().trim();
        CustomerDeliveryTownship entity;
        if (req.getId() == null) {
            if (townshipRepository.existsByRegion_IdAndNameIgnoreCase(region.getId(), name)) {
                throw new IllegalArgumentException("ဤတိုင်း/ပြည်နယ်တွင် မြို့နယ် အမည် ရှိပြီးသား ဖြစ်သည်");
            }
            entity = CustomerDeliveryTownship.builder().build();
        } else {
            entity = townshipRepository.findById(req.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Township not found"));
            if (townshipRepository.existsByRegion_IdAndNameIgnoreCaseAndIdNot(region.getId(), name, req.getId())) {
                throw new IllegalArgumentException("ဤတိုင်း/ပြည်နယ်တွင် မြို့နယ် အမည် ရှိပြီးသား ဖြစ်သည်");
            }
        }
        entity.setRegion(region);
        entity.setName(name);
        BigDecimal charge = req.getDeliveryCharge() == null ? BigDecimal.ZERO : req.getDeliveryCharge();
        if (charge.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Delivery charge အနုတ် မဖြစ်ရပါ");
        }
        entity.setDeliveryCharge(charge);
        entity.setActive(req.getActive() == null || Boolean.TRUE.equals(req.getActive()));
        entity.setSortOrder(req.getSortOrder() == null ? 0 : req.getSortOrder());
        return toTownshipDto(townshipRepository.save(entity));
    }

    @Transactional
    public CustomerPortalOrderDTO updateDelivery(Integer id, CustomerOrderDeliveryUpdateRequest req) {
        CustomerOrder order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        if (!"DELIVERY".equalsIgnoreCase(blank(order.getOrderType()) ? "" : order.getOrderType())) {
            throw new IllegalStateException("Pickup order အတွက် delivery tracking မလိုပါ");
        }
        if (req == null) throw new IllegalArgumentException("Delivery update လိုအပ်သည်");
        CustomerOrderDeliveryRules.assertMutable(order);

        String previousStatus = CustomerOrderDeliveryRules.normalize(order.getDeliveryStatus());
        if (!blank(req.getDeliveryStatus())) {
            String status = req.getDeliveryStatus().trim().toUpperCase();
            CustomerOrderDeliveryRules.assertStatusTransition(order, status);
            order.setDeliveryStatus(status);
            if ("DELIVERED".equals(status)) {
                if (order.getDeliveredAt() == null || !"DELIVERED".equals(previousStatus)) {
                    order.setDeliveredAt(LocalDateTime.now());
                }
                if (!"CONFIRMED".equalsIgnoreCase(order.getCustomerReceiptState())) {
                    order.setCustomerReceiptState("NONE");
                }
            } else {
                order.setDeliveredAt(null);
            }
        }
        if (req.getDeliveryCurrentLocation() != null) {
            String loc = req.getDeliveryCurrentLocation().trim();
            order.setDeliveryCurrentLocation(loc.isEmpty() ? null : loc);
        }
        if (req.getDeliveryScheduledAt() != null) {
            order.setDeliveryScheduledAt(req.getDeliveryScheduledAt());
        }
        if (req.getDeliveryPersonPhone() != null) {
            String phone = req.getDeliveryPersonPhone().trim();
            order.setDeliveryPersonPhone(phone.isEmpty() ? null : phone);
        }
        order.setUpdatedAt(LocalDateTime.now());
        CustomerOrder saved = orderRepository.saveAndFlush(order);
        String nextStatus = CustomerOrderDeliveryRules.normalize(saved.getDeliveryStatus());
        if (!blank(req.getDeliveryStatus()) && !previousStatus.equals(nextStatus)) {
            recordDeliveryMilestone(saved.getId(), previousStatus, nextStatus, req.getNote());
        }
        dataEventPublisher.publishCustomerOrder("CUSTOMER_ORDER_UPDATED", saved.getId());
        dataEventPublisher.publishToUser(
                CustomerPortalAuth.usernameForCustomer(saved.getCustomer().getId()),
                "/topic/customer-orders",
                toOrderNotification(saved));
        return attachMilestones(toOrderDto(saved));
    }

    static boolean awaitingCustomerReceipt(CustomerOrder order) {
        if (order == null || order.getStatus() == CustomerOrderStatus.CANCELLED) return false;
        String receipt = order.getCustomerReceiptState() == null ? "NONE" : order.getCustomerReceiptState().trim().toUpperCase();
        if ("CONFIRMED".equals(receipt)) return false;
        String type = blank(order.getOrderType()) ? "" : order.getOrderType();
        if ("DELIVERY".equalsIgnoreCase(type)) {
            return CustomerOrderDeliveryRules.awaitingCustomerReceipt(order.getDeliveryStatus());
        }
        String pay = order.getPaymentState() == null ? "" : order.getPaymentState().trim().toUpperCase();
        return "PICKUP".equalsIgnoreCase(type) && (pay.equals("PAID") || pay.equals("FULFILLED"));
    }

    @Transactional
    public CustomerPortalOrderDTO confirmReceipt(Integer id, CustomerReceiptConfirmRequest req) {
        var me = CustomerPortalAuth.require();
        CustomerOrder order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        if (!order.getCustomer().getId().equals(me.getCustomerId())) {
            throw new org.springframework.security.access.AccessDeniedException("Not your order");
        }
        if (order.getStatus() == CustomerOrderStatus.CANCELLED) {
            throw new IllegalStateException("ပယ်ဖျက်ပြီး အော်ဒါကို အတည်ပြုမရပါ");
        }
        if (req == null || req.getReceived() == null) {
            throw new IllegalArgumentException("ပစ္စည်းရောက်/မရောက် ရွေးပါ");
        }
        if ("CONFIRMED".equalsIgnoreCase(order.getCustomerReceiptState()) && Boolean.TRUE.equals(req.getReceived())) {
            return attachMilestones(toOrderDto(order));
        }
        boolean canAct = awaitingCustomerReceipt(order)
                || "NOT_RECEIVED".equalsIgnoreCase(order.getCustomerReceiptState())
                || "CONFIRMED".equalsIgnoreCase(order.getCustomerReceiptState());
        if (!canAct) {
            throw new IllegalStateException("ပစ္စည်းလက်ခံအတည်ပြုရန် အချိန် မရောက်သေးပါ");
        }
        String note = req.getNote() == null ? null : req.getNote().trim();
        if (note != null && note.isBlank()) note = null;
        if (note != null && note.length() > 500) {
            throw new IllegalArgumentException("မှတ်ချက် အလွန်ရှည်နေသည်");
        }
        String previousStatus = order.getDeliveryStatus();
        if (Boolean.TRUE.equals(req.getReceived())) {
            order.setCustomerReceiptState("CONFIRMED");
            order.setCustomerReceivedAt(LocalDateTime.now());
            order.setCustomerReceiptNote(note);
            if ("DELIVERY".equalsIgnoreCase(blank(order.getOrderType()) ? "" : order.getOrderType())) {
                order.setDeliveryStatus("DELIVERED");
                if (order.getDeliveredAt() == null) order.setDeliveredAt(LocalDateTime.now());
            }
        } else {
            order.setCustomerReceiptState("NOT_RECEIVED");
            order.setCustomerReceivedAt(null);
            order.setCustomerReceiptNote(note);
            if ("DELIVERY".equalsIgnoreCase(blank(order.getOrderType()) ? "" : order.getOrderType())
                    && "DELIVERED".equalsIgnoreCase(order.getDeliveryStatus())) {
                order.setDeliveryStatus("IN_TRANSIT");
            }
        }
        order.setUpdatedAt(LocalDateTime.now());
        CustomerOrder saved = orderRepository.saveAndFlush(order);
        String nextStatus = saved.getDeliveryStatus();
        if ("DELIVERY".equalsIgnoreCase(blank(saved.getOrderType()) ? "" : saved.getOrderType())
                && nextStatus != null
                && !nextStatus.equalsIgnoreCase(previousStatus == null ? "" : previousStatus)) {
            String historyNote = Boolean.TRUE.equals(req.getReceived())
                    ? (note == null ? "ဖောက်သည် လက်ခံအတည်ပြု" : note)
                    : (note == null ? "ဖောက်သည် မရောက်သေးဟု ပြော" : note);
            recordDeliveryMilestone(saved.getId(), previousStatus, nextStatus, historyNote);
        }
        dataEventPublisher.publishCustomerOrder("CUSTOMER_ORDER_UPDATED", saved.getId());
        dataEventPublisher.publishToUser(
                CustomerPortalAuth.usernameForCustomer(saved.getCustomer().getId()),
                "/topic/customer-orders",
                toOrderNotification(saved));
        return attachMilestones(toOrderDto(saved));
    }

    @Transactional
    public List<CustomerPortalOrderDTO> myOrders() {
        var me = CustomerPortalAuth.require();
        List<CustomerOrder> orders = orderRepository.findByCustomer_IdOrderByIdDesc(me.getCustomerId());
        boolean healed = false;
        for (CustomerOrder order : orders) {
            if (overduePaymentWindow(order)) {
                try {
                    orderPayments.expire(order.getId());
                    healed = true;
                } catch (RuntimeException ignored) {
                    // Keep listing even if expiry cannot complete this pass.
                }
            }
            if (needsPaymentWindow(order)) {
                try {
                    orderPayments.ensureTransferOpened(order.getId());
                    healed = true;
                } catch (RuntimeException ignored) {
                    // Keep listing even if stock cannot be reserved yet.
                }
            }
        }
        if (healed) {
            orders = orderRepository.findByCustomer_IdOrderByIdDesc(me.getCustomerId());
        }
        return attachMilestones(orders.stream().map(this::toOrderDto).toList());
    }

    @Transactional
    public CustomerPortalOrderDTO myOrder(Integer id) {
        var me = CustomerPortalAuth.require();
        CustomerOrder order = orderRepository.findByIdWithLines(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        if (!order.getCustomer().getId().equals(me.getCustomerId())) {
            throw new org.springframework.security.access.AccessDeniedException("Not your order");
        }
        if (overduePaymentWindow(order)) {
            try {
                orderPayments.expire(id);
                order = orderRepository.findByIdWithLines(id).orElse(order);
            } catch (RuntimeException ignored) {
                // Surface current state; scheduler will retry.
            }
        }
        if (needsPaymentWindow(order)) {
            try {
                orderPayments.ensureTransferOpened(id);
                order = orderRepository.findByIdWithLines(id).orElse(order);
            } catch (RuntimeException ignored) {
                // Surface current state; customer can retry after shop restocks.
            }
        }
        CustomerPortalOrderDTO dto = attachMilestones(toOrderDto(order));
        dto.setTimeline(orderPayments.shippingTimeline(id));
        return dto;
    }

    private static boolean overduePaymentWindow(CustomerOrder order) {
        return ("AWAITING_PAYMENT".equalsIgnoreCase(order.getPaymentState())
                || "AWAITING_COLLECTION".equalsIgnoreCase(order.getPaymentState()))
                && order.getReservationExpiresAt() != null
                && !LocalDateTime.now().isBefore(order.getReservationExpiresAt());
    }

    private static boolean needsPaymentWindow(CustomerOrder order) {
        String choice = order.getPaymentChoice() == null ? "" : order.getPaymentChoice().toUpperCase();
        return "DELIVERY".equalsIgnoreCase(order.getOrderType())
                && "ACCEPTED".equalsIgnoreCase(order.getShippingState())
                && "NONE".equalsIgnoreCase(order.getPaymentState())
                && order.getStatus() == CustomerOrderStatus.PENDING
                && java.util.Set.of("TRANSFER", "PAY_ON_COLLECTION").contains(choice);
    }

    @Transactional
    public CustomerPortalOrderDTO customerCancel(Integer id) {
        orderPayments.customerCancel(id);
        return myOrder(id);
    }

    @Transactional(readOnly = true)
    public List<CustomerPortalPurchaseDTO> myPurchases() {
        var me = CustomerPortalAuth.require();
        return saleRepository.findByCustomerIdOrderBySaleDateDescIdDesc(me.getCustomerId()).stream()
                .filter(s -> !Boolean.TRUE.equals(s.getVoided()))
                .map(this::toPurchaseDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<org.sspd.servicemgmt.saleoptions.warranty.SaleWarrantyDTO> myWarranties() {
        var me = CustomerPortalAuth.require();
        return saleWarrantyQuery.search(null, me.getCustomerId(), null, null);
    }

    @Transactional(readOnly = true)
    public List<CustomerPortalJobDTO> myJobs() {
        var me = CustomerPortalAuth.require();
        return serviceJobRepository.findByCustomer_IdOrderByReceivedDateDescIdDesc(me.getCustomerId()).stream()
                .filter(j -> !Boolean.TRUE.equals(j.getVoided()))
                .map(this::toJobDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CustomerPortalNotificationDTO> myNotifications() {
        var me = CustomerPortalAuth.require();
        List<CustomerPortalNotificationDTO> notifications = new ArrayList<>(serviceJobNotificationRepository
                .findByServiceJob_Customer_IdOrderByNotifiedAtDesc(me.getCustomerId())
                .stream()
                .map(notification -> {
                    CustomerPortalNotificationDTO dto = new CustomerPortalNotificationDTO();
                    dto.setId(notification.getId());
                    dto.setJobId(notification.getServiceJob().getId());
                    dto.setJobNo(notification.getServiceJob().getJobNo());
                    dto.setChannel(notification.getChannel());
                    dto.setNote(notification.getNote());
                    dto.setNotifiedAt(notification.getNotifiedAt());
                    return dto;
                })
                .toList());
        orderRepository.findByCustomer_IdOrderByIdDesc(me.getCustomerId()).stream()
                .filter(order -> order.getStatus() != CustomerOrderStatus.PENDING)
                .map(this::toOrderNotification).forEach(notifications::add);
        notifications.sort(java.util.Comparator.comparing(CustomerPortalNotificationDTO::getNotifiedAt,
                java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())));
        return notifications;
    }

    @Transactional(readOnly = true)
    public List<CustomerPortalOrderDTO> shopList() {
        return attachMilestones(orderRepository.findAllByOrderByIdDesc().stream().map(this::toOrderDto).toList());
    }

    @Transactional
    public CustomerPortalOrderDTO updateStatus(Integer id, CustomerOrderStatus status) {
        if (status == CustomerOrderStatus.CANCELLED) {
            orderPayments.cancel(id);
            return shopOrder(id);
        }
        if (status == CustomerOrderStatus.CONFIRMED) {
            CustomerOrder order = orderRepository.findByIdWithLines(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
            OrderPaymentRequest request = new OrderPaymentRequest();
            boolean collectionOnly = "PAY_ON_COLLECTION".equalsIgnoreCase(order.getPaymentChoice())
                    && (order.getDepositAmount() == null || order.getDepositAmount().signum() <= 0);
            request.setHoldMinutes(collectionOnly ? 1440 : 15);
            if ("DELIVERY".equalsIgnoreCase(order.getOrderType())) {
                request.setDeliveryHandler(order.getDeliveryHandler());
            }
            orderPayments.reserve(id, request);
            return shopOrder(id);
        }
        throw new IllegalArgumentException("Use Reserve & confirm to accept an order with payment instructions");
    }

    @Transactional(readOnly = true)
    public CustomerPortalOrderDTO shopOrder(Integer id) {
        CustomerPortalOrderDTO dto = attachMilestones(toOrderDto(orderRepository.findByIdWithLines(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"))));
        dto.setTimeline(orderPayments.shippingTimeline(id));
        return dto;
    }

    @Transactional(readOnly = true)
    public List<CustomerAppAccountDTO> shopAccounts() {
        return accountRepository.findAllByOrderByIdDesc().stream().map(this::toAccountDto).toList();
    }

    @Transactional(readOnly = true)
    public List<CustomerAppActivityDTO> shopAccountActivity(Integer accountId) {
        if (!accountRepository.existsById(accountId)) {
            throw new ResourceNotFoundException("Customer app account not found");
        }
        return activityService.forAccount(accountId);
    }

    @Transactional
    public CustomerAppAccountDTO setAccountEnabled(Integer id, boolean enabled) {
        CustomerAppAccount account = accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer app account not found"));
        account.setEnabled(enabled);
        return toAccountDto(accountRepository.save(account));
    }

    @Transactional
    public CustomerAppAccountDTO updateAccountEmail(Integer id, String rawEmail) {
        CustomerAppAccount account = accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer app account not found"));
        String email = normalizeShopEmail(rawEmail);
        if (email == null) {
            throw new IllegalArgumentException("Email မှန်ကန်စွာ ထည့်ပါ");
        }
        CustomerAppAccount taken = accountRepository.findByAccountOrCustomerEmail(email).orElse(null);
        if (taken != null && !taken.getId().equals(account.getId())) {
            throw new IllegalArgumentException("ဤ Email ကို အခြား App အကောင့် သုံးပြီးသား ဖြစ်သည်");
        }
        account.setEmail(email);
        if (account.getCustomer() != null) {
            account.getCustomer().setEmail(email);
        }
        CustomerAppAccount saved = accountRepository.save(account);
        activityService.record(saved, "EMAIL_UPDATED", email);
        return toAccountDto(saved);
    }

    private static String normalizeShopEmail(String email) {
        if (email == null) return null;
        String t = email.trim().toLowerCase();
        if (!t.contains("@") || t.length() < 6) return null;
        return t;
    }

    private CustomerAppAccountDTO toAccountDto(CustomerAppAccount account) {
        var customer = account.getCustomer();
        String phone = account.getPhone();
        if (phone != null && phone.startsWith("g-")) phone = "";
        CustomerAppAccountDTO dto = new CustomerAppAccountDTO();
        dto.setId(account.getId());
        dto.setCustomerId(customer != null ? customer.getId() : null);
        dto.setCustomerName(customer != null ? customer.getName() : null);
        dto.setCustomerPhone(customer != null && customer.getPhone() != null && !customer.getPhone().startsWith("g-")
                ? customer.getPhone() : phone);
        dto.setPhone(phone);
        dto.setEmail(account.getEmail() != null ? account.getEmail() : (customer != null ? customer.getEmail() : null));
        dto.setHasPassword(account.getPasswordHash() != null && !account.getPasswordHash().isBlank());
        dto.setHasGoogle(account.getGoogleSub() != null && !account.getGoogleSub().isBlank());
        dto.setProfileComplete(Boolean.TRUE.equals(account.getProfileComplete()));
        dto.setEnabled(Boolean.TRUE.equals(account.getEnabled()));
        dto.setLastLoginAt(account.getLastLoginAt());
        dto.setLoginCount(account.getLoginCount() == null ? 0 : account.getLoginCount());
        dto.setHasUsedApp(account.getLastLoginAt() != null || (account.getLoginCount() != null && account.getLoginCount() > 0));
        dto.setCreatedAt(account.getCreatedAt());
        dto.setUpdatedAt(account.getUpdatedAt());
        return dto;
    }

    public CustomerCatalogProductDTO toCatalogProduct(Product p) {
        CustomerCatalogProductDTO dto = new CustomerCatalogProductDTO();
        dto.setId(p.getId());
        dto.setName(p.getName());
        dto.setProductCode(p.getProductCode());
        dto.setProductType(p.getProductType() != null ? p.getProductType().name() : "New");
        dto.setSellingPrice(p.getSellingPrice());
        dto.setWarrantyMonths(p.getWarrantyMonths());
        dto.setRemark(blank(p.getRemark()) ? null : p.getRemark().trim());
        int stock;
        if (Boolean.TRUE.equals(p.getHasSerial())) {
            Long available = productSerialRepository.countByProductIdAndStatus(p.getId(), SerialStatus.Available);
            stock = available != null ? available.intValue() : 0;
        } else {
            int raw = p.getStockQty() == null ? 0 : p.getStockQty();
            int quarantined = p.getQuarantinedQty() == null ? 0 : p.getQuarantinedQty();
            stock = Math.max(0, raw - quarantined);
        }
        stock = Math.max(0, stock - (p.getCustomerReservedQty() == null ? 0 : p.getCustomerReservedQty()));
        dto.setInStock(stock > 0);
        dto.setStockQty(stock);
        if (p.getCategory() != null) {
            dto.setCategoryId(p.getCategory().getId());
            dto.setCategoryName(p.getCategory().getName());
            if (p.getCategory().getParent() != null) {
                dto.setParentCategoryId(p.getCategory().getParent().getId());
                dto.setParentCategoryName(p.getCategory().getParent().getName());
            }
        }
        dto.setBrandName(p.getBrand() != null ? p.getBrand().getName() : null);
        List<String> urls = new ArrayList<>();
        if (p.getPhotos() != null) {
            p.getPhotos().stream()
                    .sorted(java.util.Comparator.comparing(photo -> photo.getSlot() == null ? 99 : photo.getSlot()))
                    .limit(3)
                    .forEach(photo -> {
                        String path = photo.getImagePath();
                        if (path == null || path.isBlank()) path = photo.getThumbnailPath();
                        if (path != null && !path.isBlank()) urls.add(path);
                    });
        }
        if (urls.isEmpty() && p.getImagePath() != null && !p.getImagePath().isBlank()) urls.add(p.getImagePath());
        else if (urls.isEmpty() && p.getThumbnailPath() != null && !p.getThumbnailPath().isBlank()) urls.add(p.getThumbnailPath());
        dto.setPhotoUrls(urls);
        return dto;
    }

    private CustomerPortalNotificationDTO toOrderNotification(CustomerOrder order) {
        CustomerPortalNotificationDTO dto = new CustomerPortalNotificationDTO();
        // Negative IDs distinguish orders from service notifications.
        dto.setId(-order.getId());
        dto.setOrderId(order.getId());
        dto.setOrderNo(order.getOrderNo());
        dto.setStatus(order.getStatus().name());
        dto.setChannel("CUSTOMER_ORDER");
        String note;
        if (order.getStatus() == CustomerOrderStatus.CANCELLED) {
            note = order.getOrderNo() + " အော်ဒါကို ဆိုင်မှ ပယ်ဖျက်လိုက်ပါသည်။";
            if (order.getDepositAmount() != null && order.getDepositAmount().signum() > 0
                    && java.util.Set.of("PAID", "DEPOSIT_PAID", "PROOF_SUBMITTED", "CHECKING", "REVIEW", "LATE_REVIEW").contains(
                    order.getPaymentState() == null ? "" : order.getPaymentState())) {
                note += " စရံလွှဲပြီးပါက စရံငွေ ဆုံးရှုံးမည်။ ပြန်အမ်းမည် မဟုတ်ပါ။";
            }
        } else if ("NOT_RECEIVED".equalsIgnoreCase(order.getCustomerReceiptState())) {
            note = order.getOrderNo() + " — ပစ္စည်း မရောက်သေးပါဟု ဖောက်သည်က ပြောပါသည်"
                    + (blank(order.getCustomerReceiptNote()) ? "" : " — " + order.getCustomerReceiptNote());
        } else if ("CONFIRMED".equalsIgnoreCase(order.getCustomerReceiptState())) {
            note = order.getOrderNo() + " — ပစ္စည်း လက်ထဲ ရောက်ကြောင်း ဖောက်သည် အတည်ပြုပြီးပါပြီ";
        } else if ("DELIVERY".equalsIgnoreCase(order.getOrderType()) && !blank(order.getDeliveryStatus())) {
            String ds = order.getDeliveryStatus().trim().toUpperCase();
            note = order.getOrderNo() + " — " + CustomerOrderDeliveryRules.label(ds);
            if ("OUT_FOR_DELIVERY".equals(ds) && !blank(order.getDeliveryPersonPhone())) {
                note += " (ပို့သူ " + order.getDeliveryPersonPhone() + ")";
            } else if ("IN_TRANSIT".equals(ds) && !blank(order.getDeliveryCurrentLocation())) {
                note += " · " + order.getDeliveryCurrentLocation();
            } else if ("DELIVERED".equals(ds) && !"CONFIRMED".equalsIgnoreCase(order.getCustomerReceiptState())) {
                note += "။ လက်ထဲ ရောက်ရင် app မှ အတည်ပြုပါ။";
            }
        } else if (order.getStatus() == CustomerOrderStatus.CONFIRMED) {
            note = order.getOrderNo() + " အော်ဒါကို ဆိုင်မှ လက်ခံလိုက်ပါပြီ။";
        } else {
            note = order.getOrderNo() + " အော်ဒါအခြေအနေ ပြောင်းလိုက်ပါပြီ။";
        }
        dto.setNote(note);
        dto.setNotifiedAt(order.getUpdatedAt() != null ? order.getUpdatedAt() : LocalDateTime.now());
        return dto;
    }

    private CustomerPortalOrderDTO toOrderDto(CustomerOrder order) {
        CustomerPortalOrderDTO dto = new CustomerPortalOrderDTO();
        dto.setId(order.getId());
        dto.setPaymentState(order.getPaymentState());
        dto.setPaymentChoice(order.getPaymentChoice());
        dto.setReservationActive(order.isReservationActive());
        dto.setReservationExpiresAt(order.getReservationExpiresAt());
        dto.setReservationExpiresAtEpochMillis(order.getReservationExpiresAt() == null ? null : order.getReservationExpiresAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());
        dto.setPaymentMethodId(order.getPaymentMethodId());
        if (order.getPaymentMethodId() != null) {
            paymentMethodRepository.findById(order.getPaymentMethodId()).ifPresent(pm -> {
                dto.setPaymentMethodName(pm.getMethodName());
                dto.setPayeeName(pm.getPayeeName());
                dto.setPayeeAccountNo(pm.getPayeeAccountNo());
            });
        }
        dto.setCollectionPaymentMethodId(order.getCollectionPaymentMethodId());
        dto.setCollectionAmount(order.getCollectionAmount());
        dto.setCollectionProofId(order.getCollectionProofId());
        dto.setCollectionReference(order.getCollectionReference());
        dto.setCollectionAt(order.getCollectionAt());
        if (order.getCollectionPaymentMethodId() != null) {
            paymentMethodRepository.findById(order.getCollectionPaymentMethodId())
                    .ifPresent(pm -> dto.setCollectionPaymentMethodName(pm.getMethodName()));
        }
        dto.setPaymentInstructions(order.getPaymentInstructions());
        dto.setPaymentReviewNote(order.getPaymentReviewNote());
        dto.setPaymentVerifiedBy(order.getPaymentVerifiedBy());
        dto.setPaymentVerifiedAt(order.getPaymentVerifiedAt());
        dto.setLatestProofId(order.getLatestProofId());
        dto.setCompletedSaleId(order.getCompletedSaleId());
        if (order.getCompletedSaleId() != null) {
            saleRepository.findById(order.getCompletedSaleId()).ifPresent(sale -> {
                if (!Boolean.TRUE.equals(sale.getVoided())) {
                    dto.setCompletedSale(toPurchaseDto(sale));
                }
            });
        }
        dto.setOrderNo(order.getOrderNo());
        dto.setCustomerId(order.getCustomer().getId());
        dto.setCustomerName(order.getCustomer().getName());
        dto.setCustomerPhone(order.getCustomer().getPhone());
        dto.setStatus(order.getStatus());
        dto.setNote(order.getNote());
        dto.setCreatedAt(order.getCreatedAt());
        dto.setOrderType(order.getOrderType());
        dto.setDeliveryLocationMode(order.getDeliveryLocationMode());
        dto.setDeliveryAddress(order.getDeliveryAddress());
        dto.setDeliveryPhone(order.getDeliveryPhone());
        dto.setTownshipId(order.getTownshipId());
        dto.setTownshipName(order.getTownshipName());
        dto.setWardId(order.getWardId());
        dto.setWardName(order.getWardName());
        dto.setDeliveryCharge(order.getDeliveryCharge() == null ? BigDecimal.ZERO : order.getDeliveryCharge());
        dto.setDeliveryHandler(order.getDeliveryHandler());
        dto.setFullPaymentRequired(order.isFullPaymentRequired());
        dto.setQuotedDeliveryCharge(order.getQuotedDeliveryCharge());
        dto.setShippingState(order.getShippingState());
        dto.setShippingVersion(order.getShippingVersion());
        dto.setShippingRenegotiated(order.isShippingRenegotiated());
        dto.setShippingWeightKg(order.getShippingWeightKg());
        dto.setShippingReason(order.getShippingReason());
        dto.setShippingSnapshot(order.getShippingSnapshot());
        dto.setDeliveryStatus(order.getDeliveryStatus());
        dto.setDeliveryCurrentLocation(order.getDeliveryCurrentLocation());
        dto.setDeliveryScheduledAt(order.getDeliveryScheduledAt());
        dto.setRequestedDeliveryAt(order.getRequestedDeliveryAt());
        dto.setDeliveryPersonPhone(order.getDeliveryPersonPhone());
        dto.setDeliveredAt(order.getDeliveredAt());
        dto.setCustomerReceiptState(order.getCustomerReceiptState() == null ? "NONE" : order.getCustomerReceiptState());
        dto.setCustomerReceivedAt(order.getCustomerReceivedAt());
        dto.setCustomerReceiptNote(order.getCustomerReceiptNote());
        dto.setAwaitingCustomerReceipt(awaitingCustomerReceipt(order));
        dto.setOrderLatitude(order.getOrderLatitude());
        dto.setOrderLongitude(order.getOrderLongitude());
        dto.setOrderLocationAccuracy(order.getOrderLocationAccuracy());
        dto.setOrderLocationAt(order.getOrderLocationAt());
        dto.setOrderLocationSource(order.getOrderLocationSource());
        if (order.getCustomer() != null) {
            dto.setProfileLatitude(order.getCustomer().getLatitude());
            dto.setProfileLongitude(order.getCustomer().getLongitude());
        }
        BigDecimal itemsTotal = order.getItemsTotal();
        List<CustomerPortalOrderDTO.Line> lines = new ArrayList<>();
        if (order.getLines() != null) {
            for (CustomerOrderLine line : order.getLines()) {
                CustomerPortalOrderDTO.Line ld = new CustomerPortalOrderDTO.Line();
                ld.setId(line.getId());
                ld.setProductId(line.getProduct().getId());
                ld.setProductName(line.getProductName());
                ld.setProductCode(line.getProduct() != null ? line.getProduct().getProductCode() : null);
                ld.setQty(line.getQty());
                ld.setUnitPrice(line.getUnitPrice());
                ld.setSubtotal(line.getSubtotal());
                ld.setHasSerial(line.getProduct() != null && !Boolean.FALSE.equals(line.getProduct().getHasSerial()));
                lines.add(ld);
            }
        }
        if (itemsTotal == null) {
            itemsTotal = BigDecimal.ZERO;
            for (CustomerPortalOrderDTO.Line line : lines) {
                if (line.getSubtotal() != null) itemsTotal = itemsTotal.add(line.getSubtotal());
            }
        }
        dto.setLines(lines);
        dto.setItemsTotal(itemsTotal);
        dto.setPromoCode(order.getPromoCode());
        dto.setPromoId(order.getPromoId());
        dto.setDiscountType(order.getDiscountType());
        dto.setDiscountValue(order.getDiscountValue());
        dto.setDiscountAmount(order.getDiscountAmount());
        dto.setEligibleSubtotal(order.getEligibleSubtotal());
        dto.setDepositPercent(order.getDepositPercent());
        dto.setDepositAmount(order.getDepositAmount());
        BigDecimal discount = order.getDiscountAmount() == null ? BigDecimal.ZERO : order.getDiscountAmount();
        BigDecimal billedItems = itemsTotal.subtract(discount).max(BigDecimal.ZERO);
        BigDecimal charge = order.getDeliveryCharge() == null ? BigDecimal.ZERO : order.getDeliveryCharge();
        dto.setTotal("NEEDS_QUOTE".equals(order.getShippingState()) ? null : billedItems.add(charge));
        if ("NEEDS_QUOTE".equals(order.getShippingState())) dto.setDeliveryCharge(null);
        if (order.getDepositAmount() != null) {
            BigDecimal billed = dto.getTotal() == null ? billedItems : dto.getTotal();
            dto.setRemainingAmount(billed.subtract(order.getDepositAmount()).max(BigDecimal.ZERO));
        } else {
            dto.setRemainingAmount(null);
        }
        dto.setSettlementAction(order.getSettlementAction());
        dto.setSettlementAmount(order.getSettlementAmount());
        dto.setSettlementKeptAmount(order.getSettlementKeptAmount());
        dto.setSettlementPaymentMethodId(order.getSettlementPaymentMethodId());
        dto.setSettlementReference(order.getSettlementReference());
        dto.setSettlementAt(order.getSettlementAt());
        dto.setSettlementRecordedBy(order.getSettlementRecordedBy());
        if (order.getSettlementPaymentMethodId() != null) {
            paymentMethodRepository.findById(order.getSettlementPaymentMethodId())
                    .ifPresent(pm -> dto.setSettlementPaymentMethodName(pm.getMethodName()));
        }
        orderRatings.attachToOrder(dto, order);
        return dto;
    }

    private CustomerPortalPurchaseDTO toPurchaseDto(Sale sale) {
        CustomerPortalPurchaseDTO dto = new CustomerPortalPurchaseDTO();
        dto.setId(sale.getId());
        dto.setSaleCode(sale.getSaleCode());
        dto.setSaleDate(sale.getSaleDate());
        dto.setNetAmount(sale.getNetAmount());
        dto.setPaymentStatus(sale.getPaymentStatus() != null ? sale.getPaymentStatus().name() : null);
        List<CustomerPortalPurchaseDTO.Line> lines = new ArrayList<>();
        if (sale.getDetails() != null) {
            for (SaleDetail d : sale.getDetails()) {
                CustomerPortalPurchaseDTO.Line line = new CustomerPortalPurchaseDTO.Line();
                line.setProductId(d.getProduct() != null ? d.getProduct().getId() : null);
                line.setProductName(d.getProduct() != null ? d.getProduct().getName() : null);
                line.setQty(d.getQty());
                line.setUnitPrice(d.getUnitPrice());
                line.setSubtotal(d.getSubtotal());
                line.setWarrantyMonths(d.getWarrantyMonths());
                line.setWarrantyStartDate(d.getWarrantyStartDate());
                line.setWarrantyExpiryDate(d.getWarrantyExpiryDate());
                line.setWarrantyStatus(org.sspd.servicemgmt.saleoptions.warranty.SaleWarrantyCalculator.status(
                        d.getWarrantyMonths(), d.getWarrantyExpiryDate(), java.time.LocalDate.now()));
                line.setWarrantyDaysRemaining(org.sspd.servicemgmt.saleoptions.warranty.SaleWarrantyCalculator.daysRemaining(
                        d.getWarrantyMonths(), d.getWarrantyExpiryDate(), java.time.LocalDate.now()));
                line.setSerialNumber(d.getSerialNumber());
                lines.add(line);
            }
        }
        dto.setLines(lines);
        return dto;
    }

    private CustomerPortalJobDTO toJobDto(ServiceJob job) {
        CustomerPortalJobDTO dto = new CustomerPortalJobDTO();
        dto.setId(job.getId());
        dto.setJobNo(job.getJobNo());
        dto.setStatus(job.getStatus() != null ? job.getStatus().name() : null);
        dto.setItemName(job.getItemName());
        dto.setDeviceType(job.getDeviceType());
        dto.setProblemDesc(job.getProblemDesc());
        dto.setReceivedDate(job.getReceivedDate());
        dto.setCompletedDate(job.getCompletedDate());
        dto.setDeliveredDate(job.getDeliveredDate());
        dto.setNetAmount(job.getNetAmount());
        dto.setPaymentStatus(job.getPaymentStatus() != null ? job.getPaymentStatus().name() : null);
        List<CustomerPortalJobDTO.ServiceLine> services = new ArrayList<>();
        if (job.getLines() != null) {
            for (ServiceJobLine line : job.getLines()) {
                CustomerPortalJobDTO.ServiceLine sl = new CustomerPortalJobDTO.ServiceLine();
                sl.setName(line.getServiceItem() != null ? line.getServiceItem().getItem() : null);
                sl.setQty(line.getQty());
                sl.setWarrantyMonths(line.getWarrantyMonths());
                sl.setWarrantyCovered(line.getWarrantyCovered());
                services.add(sl);
            }
        }
        dto.setServices(services);
        List<CustomerPortalJobDTO.PartLine> parts = new ArrayList<>();
        if (job.getProductParts() != null) {
            for (ServiceJobPart part : job.getProductParts()) {
                CustomerPortalJobDTO.PartLine pl = new CustomerPortalJobDTO.PartLine();
                pl.setProductName(part.getProduct() != null ? part.getProduct().getName() : null);
                pl.setQty(part.getQty());
                pl.setWarrantyCovered(part.getWarrantyCovered());
                parts.add(pl);
            }
        }
        dto.setParts(parts);
        return dto;
    }

    private void applyOrderFulfillment(CustomerOrder order, Customer customer, CustomerPortalOrderRequest req) {
        String type = blank(req.getOrderType()) ? "PICKUP" : req.getOrderType().trim().toUpperCase();
        if (!"DELIVERY".equals(type) && !"PICKUP".equals(type)) {
            throw new IllegalArgumentException("Order type မှန်ကန်စွာ ရွေးပါ (သွားပို့ / ဆိုင်လာယူ)");
        }
        order.setOrderType(type);

        if ("PICKUP".equals(type)) {
            order.setDeliveryLocationMode(null);
            order.setDeliveryAddress(null);
            order.setDeliveryPhone(null);
            order.setTownshipId(null);
            order.setTownshipName(null);
            order.setWardId(null);
            order.setWardName(null);
            order.setDeliveryCharge(BigDecimal.ZERO);
            order.setQuotedDeliveryCharge(BigDecimal.ZERO);
            order.setDeliveryHandler(null);
            order.setFullPaymentRequired(false);
            order.setDeliveryStatus(null);
            order.setDeliveryCurrentLocation(null);
            order.setDeliveryScheduledAt(null);
            order.setDeliveryPersonPhone(null);
            order.setDeliveredAt(null);
            return;
        }

        if (req.getTownshipId() == null) {
            throw new IllegalArgumentException("ပို့မည့် မြို့နယ် ရွေးပါ");
        }
        CustomerDeliveryTownship township = townshipRepository.findById(req.getTownshipId())
                .orElseThrow(() -> new ResourceNotFoundException("မြို့နယ် မတွေ့ပါ"));
        if (!Boolean.TRUE.equals(township.getActive())
                || township.getRegion() == null
                || !Boolean.TRUE.equals(township.getRegion().getActive())) {
            throw new IllegalArgumentException("ဤမြို့နယ်သို့ ယာယီ ပို့ဆောင်၍ မရပါ");
        }
        CustomerDeliveryWard ward = req.getWardId() == null ? null
                : wardRepository.findWithTownshipAndRegionById(req.getWardId())
                .orElseThrow(() -> new ResourceNotFoundException("ရပ်ကွက် မတွေ့ပါ"));
        if (ward != null && (!Boolean.TRUE.equals(ward.getActive())
                || ward.getTownship() == null || !township.getId().equals(ward.getTownship().getId()))) {
            throw new IllegalArgumentException("မြို့နယ် နှင့် ရပ်ကွက် ကိုက်ညီမှု မရှိပါ");
        }
        order.setTownshipId(township.getId());
        order.setTownshipName(township.getRegion().getName() + " · " + township.getName());
        order.setWardId(ward == null ? null : ward.getId());
        order.setWardName(ward == null ? null : ward.getName());
        BigDecimal areaCharge = ward == null ? township.getDeliveryCharge() : ward.getDeliveryCharge();
        order.setDeliveryCharge(areaCharge == null ? BigDecimal.ZERO : areaCharge);
        order.setQuotedDeliveryCharge(order.getDeliveryCharge());
        order.setDeliveryHandler(null);
        order.setFullPaymentRequired(false);
        order.setDeliveryStatus("PENDING");

        String mode = blank(req.getDeliveryLocationMode()) ? null : req.getDeliveryLocationMode().trim().toUpperCase();
        if (!"PROFILE".equals(mode) && !"OTHER".equals(mode)) {
            throw new IllegalArgumentException("ပို့မည့်နေရာ ရွေးပါ — သိမ်းထားသော လိပ်စာ သို့မဟုတ် အခြားနေရာ");
        }
        order.setDeliveryLocationMode(mode);

        if ("PROFILE".equals(mode)) {
            if (blank(customer.getAddress())) {
                throw new IllegalArgumentException("Profile လိပ်စာ မရှိသေးပါ — အရင် ဖြည့်ပါ");
            }
            order.setDeliveryAddress(customer.getAddress().trim());
            order.setDeliveryPhone(blank(customer.getPhone()) ? null : customer.getPhone().trim());
            if (customer.getLatitude() != null && customer.getLongitude() != null) {
                order.setOrderLatitude(customer.getLatitude());
                order.setOrderLongitude(customer.getLongitude());
                if (customer.getLocationAccuracy() != null) {
                    order.setOrderLocationAccuracy(customer.getLocationAccuracy().doubleValue());
                }
                order.setOrderLocationAt(LocalDateTime.now());
                order.setOrderLocationSource("PROFILE");
            } else {
                applyOrderLocation(order, req);
                if (order.getOrderLatitude() != null && blank(order.getOrderLocationSource())) {
                    order.setOrderLocationSource("APP");
                }
            }
            return;
        }

        if (blank(req.getDeliveryAddress())) {
            throw new IllegalArgumentException("ပို့မည့် လိပ်စာ ထည့်ပါ");
        }
        if (blank(req.getDeliveryPhone())) {
            throw new IllegalArgumentException("ပစ္စည်းလက်ခံမည့်သူ ဖုန်းနံပါတ် ထည့်ပါ");
        }
        order.setDeliveryAddress(req.getDeliveryAddress().trim());
        order.setDeliveryPhone(req.getDeliveryPhone().trim());
        applyOrderLocation(order, req);
        if (order.getOrderLatitude() != null) {
            order.setOrderLocationSource("OTHER");
        }
    }

    @Transactional(readOnly = true)
    public List<CustomerDeliveryRegionDTO> activeDeliveryLocations() {
        List<CustomerDeliveryRegion> regions = regionRepository.findByActiveTrueOrderBySortOrderAscNameAsc();
        List<CustomerDeliveryTownship> townships = townshipRepository
                .findByActiveTrueAndRegion_ActiveTrueOrderByRegion_SortOrderAscSortOrderAscNameAsc();
        List<CustomerDeliveryWard> wards = wardRepository
                .findByActiveTrueAndTownship_ActiveTrueAndTownship_Region_ActiveTrueOrderByTownship_Region_SortOrderAscTownship_SortOrderAscSortOrderAscNameAsc();
        java.util.Map<Integer, List<CustomerDeliveryWardDTO>> wardsByTownship = new java.util.LinkedHashMap<>();
        for (CustomerDeliveryWard ward : wards) {
            CustomerDeliveryWardDTO dto = toWardDto(ward);
            wardsByTownship.computeIfAbsent(ward.getTownship().getId(), k -> new java.util.ArrayList<>()).add(dto);
        }
        java.util.Map<Integer, List<CustomerDeliveryTownshipDTO>> townshipsByRegion = new java.util.LinkedHashMap<>();
        for (CustomerDeliveryTownship township : townships) {
            CustomerDeliveryTownshipDTO dto = toTownshipDto(township);
            dto.setWards(wardsByTownship.getOrDefault(township.getId(), java.util.List.of()));
            if (township.getRegion() != null) {
                townshipsByRegion.computeIfAbsent(township.getRegion().getId(), k -> new java.util.ArrayList<>()).add(dto);
            }
        }
        List<CustomerDeliveryRegionDTO> tree = new java.util.ArrayList<>();
        for (CustomerDeliveryRegion region : regions) {
            CustomerDeliveryRegionDTO dto = toRegionDto(region);
            dto.setTownships(townshipsByRegion.getOrDefault(region.getId(), java.util.List.of()));
            tree.add(dto);
        }
        return tree;
    }

    @Transactional(readOnly = true)
    public List<CustomerDeliveryWardDTO> shopWards() {
        return wardRepository.findAllByOrderByTownship_Region_SortOrderAscTownship_SortOrderAscSortOrderAscNameAsc()
                .stream()
                .map(this::toWardDto)
                .toList();
    }

    @Transactional
    public CustomerDeliveryWardDTO saveWard(CustomerDeliveryWardDTO req) {
        if (req == null || blank(req.getName())) {
            throw new IllegalArgumentException("ရပ်ကွက် အမည် လိုအပ်သည်");
        }
        if (req.getTownshipId() == null) {
            throw new IllegalArgumentException("မြို့နယ် ရွေးပါ");
        }
        CustomerDeliveryTownship township = townshipRepository.findById(req.getTownshipId())
                .orElseThrow(() -> new ResourceNotFoundException("Township not found"));
        String name = req.getName().trim();
        CustomerDeliveryWard entity;
        if (req.getId() == null) {
            if (wardRepository.existsByTownship_IdAndNameIgnoreCase(township.getId(), name)) {
                throw new IllegalArgumentException("ဤမြို့နယ်တွင် ရပ်ကွက် အမည် ရှိပြီးသား ဖြစ်သည်");
            }
            entity = CustomerDeliveryWard.builder().build();
        } else {
            entity = wardRepository.findById(req.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Ward not found"));
            if (wardRepository.existsByTownship_IdAndNameIgnoreCaseAndIdNot(township.getId(), name, req.getId())) {
                throw new IllegalArgumentException("ဤမြို့နယ်တွင် ရပ်ကွက် အမည် ရှိပြီးသား ဖြစ်သည်");
            }
        }
        BigDecimal charge = req.getDeliveryCharge() == null ? BigDecimal.ZERO : req.getDeliveryCharge();
        if (charge.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Delivery charge အနှုတ် မဖြစ်ရ");
        }
        entity.setTownship(township);
        entity.setName(name);
        entity.setDeliveryCharge(charge);
        entity.setActive(req.getActive() == null || Boolean.TRUE.equals(req.getActive()));
        entity.setSortOrder(req.getSortOrder() == null ? 0 : req.getSortOrder());
        return toWardDto(wardRepository.save(entity));
    }

    private CustomerDeliveryWardDTO toWardDto(CustomerDeliveryWard entity) {
        CustomerDeliveryWardDTO dto = new CustomerDeliveryWardDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setDeliveryCharge(entity.getDeliveryCharge());
        dto.setActive(entity.getActive());
        dto.setSortOrder(entity.getSortOrder());
        if (entity.getTownship() != null) {
            dto.setTownshipId(entity.getTownship().getId());
            dto.setTownshipName(entity.getTownship().getName());
            if (entity.getTownship().getRegion() != null) {
                dto.setRegionId(entity.getTownship().getRegion().getId());
                dto.setRegionName(entity.getTownship().getRegion().getName());
            }
        }
        return dto;
    }

    private CustomerDeliveryTownshipDTO toTownshipDto(CustomerDeliveryTownship entity) {
        CustomerDeliveryTownshipDTO dto = new CustomerDeliveryTownshipDTO();
        dto.setId(entity.getId());
        if (entity.getRegion() != null) {
            dto.setRegionId(entity.getRegion().getId());
            dto.setRegionName(entity.getRegion().getName());
            dto.setRegionKind(entity.getRegion().getKind());
        }
        dto.setName(entity.getName());
        dto.setDeliveryCharge(entity.getDeliveryCharge());
        dto.setActive(entity.getActive());
        dto.setSortOrder(entity.getSortOrder());
        return dto;
    }

    private CustomerDeliveryRegionDTO toRegionDto(CustomerDeliveryRegion entity) {
        CustomerDeliveryRegionDTO dto = new CustomerDeliveryRegionDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setKind(entity.getKind());
        dto.setActive(entity.getActive());
        dto.setSortOrder(entity.getSortOrder());
        return dto;
    }

    private void applyOrderLocation(CustomerOrder order, CustomerPortalOrderRequest req) {
        if (req == null || req.getLatitude() == null || req.getLongitude() == null) return;
        double lat = req.getLatitude().doubleValue();
        double lng = req.getLongitude().doubleValue();
        if (lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            throw new IllegalArgumentException("Order GPS တည်နေရာ မမှန်ကန်ပါ");
        }
        order.setOrderLatitude(req.getLatitude());
        order.setOrderLongitude(req.getLongitude());
        order.setOrderLocationAccuracy(req.getLocationAccuracy());
        order.setOrderLocationAt(LocalDateTime.now());
        String source = blank(req.getLocationSource()) ? "APP" : req.getLocationSource().trim().toUpperCase();
        order.setOrderLocationSource(source.length() > 20 ? source.substring(0, 20) : source);
    }

    private void recordDeliveryMilestone(Integer orderId, String fromStatus, String toStatus, String note) {
        if (orderId == null || deliveryMilestones == null || blank(toStatus)) return;
        String trimmed = note == null ? null : note.trim();
        if (trimmed != null && trimmed.isBlank()) trimmed = null;
        if (trimmed != null && trimmed.length() > 500) trimmed = trimmed.substring(0, 500);
        Actor actor = currentDeliveryActor();
        deliveryMilestones.save(CustomerDeliveryMilestone.builder()
                .orderId(orderId)
                .fromStatus(blank(fromStatus) ? null : fromStatus.trim().toUpperCase())
                .toStatus(toStatus.trim().toUpperCase())
                .actor(actor.name())
                .actorType(actor.type())
                .note(trimmed)
                .createdAt(LocalDateTime.now())
                .build());
    }

    private Actor currentDeliveryActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof org.sspd.servicemgmt.jwt.CustomerPortalUserDetails details) {
            String name = details.getName();
            if (blank(name)) name = details.getUsername();
            return new Actor(blank(name) ? "CUSTOMER" : name, "CUSTOMER");
        }
        if (auth != null && !blank(auth.getName()) && !"anonymousUser".equalsIgnoreCase(auth.getName())) {
            return new Actor(auth.getName(), "SHOP");
        }
        return new Actor("SYSTEM", "SYSTEM");
    }

    private CustomerPortalOrderDTO attachMilestones(CustomerPortalOrderDTO dto) {
        if (dto == null || dto.getId() == null || deliveryMilestones == null) return dto;
        dto.setDeliveryMilestones(deliveryMilestones.findByOrderIdOrderByIdAsc(dto.getId()).stream()
                .map(this::toMilestoneDto)
                .toList());
        return dto;
    }

    private List<CustomerPortalOrderDTO> attachMilestones(List<CustomerPortalOrderDTO> dtos) {
        if (dtos == null || dtos.isEmpty() || deliveryMilestones == null) return dtos;
        List<Integer> ids = dtos.stream().map(CustomerPortalOrderDTO::getId).filter(id -> id != null).toList();
        if (ids.isEmpty()) return dtos;
        Map<Integer, List<CustomerPortalOrderDTO.DeliveryMilestone>> grouped = new HashMap<>();
        for (CustomerDeliveryMilestone row : deliveryMilestones.findByOrderIdInOrderByIdAsc(ids)) {
            grouped.computeIfAbsent(row.getOrderId(), k -> new ArrayList<>()).add(toMilestoneDto(row));
        }
        for (CustomerPortalOrderDTO dto : dtos) {
            dto.setDeliveryMilestones(grouped.getOrDefault(dto.getId(), List.of()));
        }
        return dtos;
    }

    private CustomerPortalOrderDTO.DeliveryMilestone toMilestoneDto(CustomerDeliveryMilestone row) {
        CustomerPortalOrderDTO.DeliveryMilestone dto = new CustomerPortalOrderDTO.DeliveryMilestone();
        dto.setId(row.getId());
        dto.setFromStatus(row.getFromStatus());
        dto.setToStatus(row.getToStatus());
        dto.setActor(row.getActor());
        dto.setActorType(row.getActorType());
        dto.setNote(row.getNote());
        dto.setAt(row.getCreatedAt());
        return dto;
    }

    private record Actor(String name, String type) {}

    private static boolean blank(String v) {
        return v == null || v.isBlank();
    }
}
