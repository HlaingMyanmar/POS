package org.sspd.servicemgmt.customerportaloptions.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.categoryoptions.model.Category;
import org.sspd.servicemgmt.categoryoptions.repository.CategoryRepository;
import org.sspd.servicemgmt.customeroptions.model.Customer;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerPromoCodeDTO;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerPromoQuoteDTO;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerPromoValidateRequest;
import org.sspd.servicemgmt.customerportaloptions.model.CustomerOrder;
import org.sspd.servicemgmt.customerportaloptions.model.CustomerOrderLine;
import org.sspd.servicemgmt.customerportaloptions.model.CustomerPromoCode;
import org.sspd.servicemgmt.customerportaloptions.model.CustomerPromoRedemption;
import org.sspd.servicemgmt.customerportaloptions.repository.CustomerPromoCodeRepository;
import org.sspd.servicemgmt.customerportaloptions.repository.CustomerPromoRedemptionRepository;
import org.sspd.servicemgmt.customerportaloptions.support.CustomerPortalAuth;
import org.sspd.servicemgmt.exceptionhandler.ResourceNotFoundException;
import org.sspd.servicemgmt.stockoptions.productoptions.model.Product;
import org.sspd.servicemgmt.stockoptions.productoptions.repository.ProductRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CustomerPromoService {

    private final CustomerPromoCodeRepository promos;
    private final CustomerPromoRedemptionRepository redemptions;
    private final ProductRepository products;
    private final CategoryRepository categories;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<CustomerPromoCodeDTO> shopList() {
        return promos.findAllByOrderByIdDesc().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public CustomerPromoCodeDTO shopGet(Integer id) {
        return toDto(promos.findWithScopeById(id).orElseThrow(() -> new ResourceNotFoundException("Promo not found")));
    }

    @Transactional
    public CustomerPromoCodeDTO save(CustomerPromoCodeDTO req) {
        if (req == null) throw new IllegalArgumentException("Promo လိုအပ်သည်");
        String code = normalizeCode(req.getCode());
        if (code.isBlank()) throw new IllegalArgumentException("Promo code လိုအပ်သည်");
        if (blank(req.getName())) throw new IllegalArgumentException("Promo အမည် လိုအပ်သည်");
        if (req.getStartsAt() == null || req.getEndsAt() == null) {
            throw new IllegalArgumentException("စတင်/ပြီးဆုံး ရက်ချိန် လိုအပ်သည်");
        }
        if (!req.getEndsAt().isAfter(req.getStartsAt())) {
            throw new IllegalArgumentException("ပြီးဆုံးချိန်သည် စတင်ချိန် နောက်မှ ဖြစ်ရမည်");
        }
        String type = normalizeType(req.getDiscountType());
        BigDecimal value = money(req.getDiscountValue(), "လျှော့ဈေး တန်ဖိုး");
        if ("PERCENT".equals(type) && (value.compareTo(BigDecimal.ZERO) <= 0 || value.compareTo(new BigDecimal("100")) > 0)) {
            throw new IllegalArgumentException("ရာခိုင်နှုန်း 0 ထက်ကြီးပြီး 100 ထက် မကျော်ရ");
        }
        if ("FIXED".equals(type) && value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("လျှော့ဈေး ပမာဏ 0 ထက် ကြီးရမည်");
        }
        CustomerPromoCode entity = req.getId() == null ? CustomerPromoCode.builder().createdAt(LocalDateTime.now()).build()
                : promos.findWithScopeById(req.getId()).orElseThrow(() -> new ResourceNotFoundException("Promo not found"));
        promos.findByCode(code).ifPresent(existing -> {
            if (req.getId() == null || !existing.getId().equals(req.getId())) {
                throw new IllegalArgumentException("ဤ Promo code ရှိပြီးသား ဖြစ်သည်");
            }
        });
        entity.setCode(code);
        entity.setName(req.getName().trim());
        entity.setDescription(blank(req.getDescription()) ? null : req.getDescription().trim());
        entity.setActive(req.isActive());
        entity.setStartsAt(req.getStartsAt());
        entity.setEndsAt(req.getEndsAt());
        entity.setDiscountType(type);
        entity.setDiscountValue(value);
        entity.setMaxDiscount(optionalMoney(req.getMaxDiscount()));
        entity.setMinOrderAmount(optionalMoney(req.getMinOrderAmount()));
        entity.setUsageLimit(nonNegative(req.getUsageLimit(), "အသုံးပြုနိုင်သော အကြိမ်"));
        entity.setPerCustomerLimit(nonNegative(req.getPerCustomerLimit(), "ဖောက်သည်တစ်ဦးချင်း ကန့်သတ်"));
        entity.setUpdatedAt(LocalDateTime.now());
        replaceScope(entity, req.getProductIds(), req.getCategoryIds());
        return toDto(promos.saveAndFlush(entity));
    }

    @Transactional(readOnly = true)
    public CustomerPromoQuoteDTO preview(CustomerPromoValidateRequest request) {
        int customerId = CustomerPortalAuth.require().getCustomerId();
        if (request == null || blank(request.getCode())) throw new IllegalArgumentException("Promo code ထည့်ပါ");
        if (request.getLines() == null || request.getLines().isEmpty()) {
            throw new IllegalArgumentException("ခြင်းတောင်း ပစ္စည်းများ လိုအပ်သည်");
        }
        CustomerPromoCode promo = promos.findByCode(normalizeCode(request.getCode()))
                .orElseThrow(() -> new IllegalArgumentException("Promo code မမှန်ပါ"));
        hydrateScope(promo);
        List<CustomerOrderLine> lines = linesFromRequest(request.getLines());
        Quote quote = quote(promo, customerId, lines, false);
        return toQuoteDto(quote, lines);
    }

    @Transactional
    public void apply(CustomerOrder order, String rawCode) {
        if (order == null || blank(rawCode)) return;
        int customerId = order.getCustomer().getId();
        CustomerPromoCode promo = promos.findLockedByCode(normalizeCode(rawCode))
                .orElseThrow(() -> new IllegalArgumentException("Promo code မမှန်ပါ"));
        hydrateScope(promo);
        Quote quote = quote(promo, customerId, order.getLines(), true);
        order.setPromoId(promo.getId());
        order.setPromoCode(promo.getCode());
        order.setDiscountType(promo.getDiscountType());
        order.setDiscountValue(promo.getDiscountValue());
        order.setDiscountAmount(quote.discount);
        order.setDiscountMax(promo.getMaxDiscount());
        order.setEligibleSubtotal(quote.eligible);
        order.setPromoSnapshot(snapshot(quote));
        if (redemptions.findByOrderId(order.getId()).isPresent()) return;
        redemptions.saveAndFlush(CustomerPromoRedemption.builder()
                .promo(promo)
                .customer(order.getCustomer())
                .orderId(order.getId())
                .discountAmount(quote.discount)
                .released(false)
                .createdAt(LocalDateTime.now())
                .build());
    }

    @Transactional
    public void release(Integer orderId) {
        if (orderId == null) return;
        redemptions.findByOrderId(orderId).ifPresent(row -> {
            if (!row.isReleased()) {
                row.setReleased(true);
                redemptions.save(row);
            }
        });
    }

    private Quote quote(CustomerPromoCode promo, int customerId, List<CustomerOrderLine> lines, boolean locking) {
        LocalDateTime now = LocalDateTime.now();
        if (!promo.isActive()) throw new IllegalStateException("ဤ Promo ကို ပိတ်ထားသည်");
        if (now.isBefore(promo.getStartsAt())) throw new IllegalStateException("Promo စတင်ချိန် မရောက်သေးပါ");
        if (now.isAfter(promo.getEndsAt())) throw new IllegalStateException("Promo သက်တမ်းကုန်ပါပြီ");
        long used = redemptions.countByPromo_IdAndReleasedFalse(promo.getId());
        if (promo.getUsageLimit() != null && used >= promo.getUsageLimit()) {
            throw new IllegalStateException("Promo အသုံးပြုခွင့် ပြည့်ပါပြီ");
        }
        Integer perCustomer = promo.getPerCustomerLimit();
        if (perCustomer != null) {
            long mine = redemptions.countByPromo_IdAndCustomer_IdAndReleasedFalse(promo.getId(), customerId);
            if (mine >= perCustomer) throw new IllegalStateException("ဤ Promo ကို သင့်အကောင့် အသုံးပြုပြီးပါပြီ");
        }
        BigDecimal eligible = BigDecimal.ZERO;
        for (CustomerOrderLine line : lines) {
            if (line == null || line.getSubtotal() == null) continue;
            if (inScope(promo, line.getProduct())) eligible = eligible.add(line.getSubtotal());
        }
        eligible = eligible.setScale(2, RoundingMode.HALF_UP);
        if (promo.getMinOrderAmount() != null && eligible.compareTo(promo.getMinOrderAmount()) < 0) {
            throw new IllegalStateException("အနည်းဆုံး မှာယူရမည့် ပမာဏ " + promo.getMinOrderAmount() + " Ks");
        }
        if (eligible.signum() <= 0) throw new IllegalStateException("ဤခြင်းတောင်းတွင် Promo သက်ရောက်မည့် ပစ္စည်း မရှိပါ");
        BigDecimal discount;
        if ("PERCENT".equals(promo.getDiscountType())) {
            discount = eligible.multiply(promo.getDiscountValue()).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        } else {
            discount = promo.getDiscountValue().setScale(2, RoundingMode.HALF_UP);
        }
        if (promo.getMaxDiscount() != null && discount.compareTo(promo.getMaxDiscount()) > 0) {
            discount = promo.getMaxDiscount().setScale(2, RoundingMode.HALF_UP);
        }
        if (discount.compareTo(eligible) > 0) discount = eligible;
        if (discount.signum() <= 0) throw new IllegalStateException("လျှော့ဈေး တွက်မရပါ");
        return new Quote(promo, eligible, discount, locking);
    }

    private boolean inScope(CustomerPromoCode promo, Product product) {
        boolean productScoped = promo.getProducts() != null && !promo.getProducts().isEmpty();
        boolean categoryScoped = promo.getCategories() != null && !promo.getCategories().isEmpty();
        if (!productScoped && !categoryScoped) return product != null;
        if (product == null) return false;
        if (productScoped) {
            for (Product allowed : promo.getProducts()) {
                if (allowed != null && allowed.getId().equals(product.getId())) return true;
            }
        }
        if (categoryScoped && product.getCategory() != null) {
            Integer catId = product.getCategory().getId();
            Integer parentId = product.getCategory().getParent() == null ? null : product.getCategory().getParent().getId();
            for (Category allowed : promo.getCategories()) {
                if (allowed == null) continue;
                if (allowed.getId() == catId) return true;
                if (parentId != null && allowed.getId() == parentId) return true;
            }
        }
        return false;
    }

    private List<CustomerOrderLine> linesFromRequest(List<CustomerPromoValidateRequest.Line> wanted) {
        java.util.ArrayList<CustomerOrderLine> lines = new java.util.ArrayList<>();
        for (CustomerPromoValidateRequest.Line row : wanted) {
            if (row.getProductId() == null || row.getQty() == null || row.getQty() < 1) {
                throw new IllegalArgumentException("ပစ္စည်းနှင့် အရေအတွက် မှန်ကန်ရမည်");
            }
            Product product = products.findWithDetailsById(row.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
            if (Boolean.TRUE.equals(product.getArchived())) continue;
            BigDecimal price = product.getSellingPrice() == null ? BigDecimal.ZERO : product.getSellingPrice();
            lines.add(CustomerOrderLine.builder()
                    .product(product)
                    .productName(product.getName())
                    .qty(row.getQty())
                    .unitPrice(price)
                    .subtotal(price.multiply(BigDecimal.valueOf(row.getQty())))
                    .build());
        }
        return lines;
    }

    private void replaceScope(CustomerPromoCode entity, List<Integer> productIds, List<Integer> categoryIds) {
        Set<Product> nextProducts = new LinkedHashSet<>();
        if (productIds != null) {
            for (Integer id : productIds) {
                if (id == null) continue;
                nextProducts.add(products.findById(id).orElseThrow(() -> new IllegalArgumentException("Product မရှိပါ: " + id)));
            }
        }
        Set<Category> nextCategories = new LinkedHashSet<>();
        if (categoryIds != null) {
            for (Integer id : categoryIds) {
                if (id == null) continue;
                nextCategories.add(categories.findById(id.longValue())
                        .orElseThrow(() -> new IllegalArgumentException("Category မရှိပါ: " + id)));
            }
        }
        entity.getProducts().clear();
        entity.getProducts().addAll(nextProducts);
        entity.getCategories().clear();
        entity.getCategories().addAll(nextCategories);
    }

    private void hydrateScope(CustomerPromoCode promo) {
        if (promo.getProducts() != null) promo.getProducts().size();
        if (promo.getCategories() != null) promo.getCategories().size();
        if (promo.getCategories() != null) {
            for (Category category : promo.getCategories()) {
                if (category != null && category.getParent() != null) category.getParent().getId();
            }
        }
    }

    private CustomerPromoCodeDTO toDto(CustomerPromoCode entity) {
        CustomerPromoCodeDTO dto = new CustomerPromoCodeDTO();
        dto.setId(entity.getId());
        dto.setCode(entity.getCode());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        dto.setActive(entity.isActive());
        dto.setStartsAt(entity.getStartsAt());
        dto.setEndsAt(entity.getEndsAt());
        dto.setDiscountType(entity.getDiscountType());
        dto.setDiscountValue(entity.getDiscountValue());
        dto.setMaxDiscount(entity.getMaxDiscount());
        dto.setMinOrderAmount(entity.getMinOrderAmount());
        dto.setUsageLimit(entity.getUsageLimit());
        dto.setPerCustomerLimit(entity.getPerCustomerLimit());
        dto.setUsedCount(entity.getId() == null ? 0 : redemptions.countByPromo_IdAndReleasedFalse(entity.getId()));
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        if (entity.getProducts() != null) {
            dto.setProductIds(entity.getProducts().stream().map(Product::getId).toList());
        }
        if (entity.getCategories() != null) {
            dto.setCategoryIds(entity.getCategories().stream().map(c -> c.getId()).toList());
        }
        return dto;
    }

    private CustomerPromoQuoteDTO toQuoteDto(Quote quote, List<CustomerOrderLine> lines) {
        BigDecimal items = lines.stream()
                .map(CustomerOrderLine::getSubtotal)
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        CustomerPromoQuoteDTO dto = new CustomerPromoQuoteDTO();
        dto.setPromoCode(quote.promo.getCode());
        dto.setDiscountType(quote.promo.getDiscountType());
        dto.setDiscountValue(quote.promo.getDiscountValue());
        dto.setDiscountAmount(quote.discount);
        if ("PERCENT".equals(quote.promo.getDiscountType())) dto.setDiscountPercent(quote.promo.getDiscountValue());
        dto.setMaxDiscount(quote.promo.getMaxDiscount());
        dto.setMinOrderAmount(quote.promo.getMinOrderAmount());
        dto.setEligibleSubtotal(quote.eligible);
        dto.setItemsTotal(items);
        return dto;
    }

    private String snapshot(Quote quote) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("code", quote.promo.getCode());
        map.put("type", quote.promo.getDiscountType());
        map.put("value", quote.promo.getDiscountValue());
        map.put("maxDiscount", quote.promo.getMaxDiscount());
        map.put("minOrderAmount", quote.promo.getMinOrderAmount());
        map.put("eligibleSubtotal", quote.eligible);
        map.put("discountAmount", quote.discount);
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String normalizeCode(String code) {
        return code == null ? "" : code.trim().toUpperCase();
    }

    private static String normalizeType(String type) {
        String value = type == null ? "" : type.trim().toUpperCase();
        if (!value.equals("PERCENT") && !value.equals("FIXED")) {
            throw new IllegalArgumentException("discountType သည် PERCENT သို့မဟုတ် FIXED ဖြစ်ရမည်");
        }
        return value;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static BigDecimal money(BigDecimal value, String label) {
        if (value == null || value.signum() < 0 || value.scale() > 2) {
            throw new IllegalArgumentException(label + " မှန်ကန်ရမည်");
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal optionalMoney(BigDecimal value) {
        if (value == null) return null;
        if (value.signum() <= 0) return null;
        return money(value, "ကန့်သတ်ပမာဏ");
    }

    private static Integer nonNegative(Integer value, String label) {
        if (value == null) return null;
        if (value < 1) throw new IllegalArgumentException(label + " 1 နှင့်အထက် ဖြစ်ရမည်");
        return value;
    }

    private record Quote(CustomerPromoCode promo, BigDecimal eligible, BigDecimal discount, boolean locking) {}
}
