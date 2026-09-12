package org.sspd.servicemgmt.customerportaloptions.support;

import org.sspd.servicemgmt.customerportaloptions.model.CustomerOrder;
import org.sspd.servicemgmt.customerportaloptions.model.CustomerOrderStatus;

import java.util.List;
import java.util.Set;

public final class CustomerOrderDeliveryRules {

    public static final List<String> STATUSES = List.of(
            "PENDING",
            "PACKING",
            "PACKED",
            "HANDED_TO_RIDER",
            "OUT_FOR_DELIVERY",
            "IN_TRANSIT",
            "DELIVERED");

    /** Full payment, or deposit already verified — remainder collected when the goods arrive. */
    private static final Set<String> DISPATCH_PAYMENTS = Set.of("PAID", "FULFILLED", "DEPOSIT_PAID");
    private static final Set<String> SHIPPING_READY = Set.of("LEGACY", "ACCEPTED");
    private static final Set<String> AWAITING_RECEIPT = Set.of(
            "HANDED_TO_RIDER", "OUT_FOR_DELIVERY", "IN_TRANSIT", "DELIVERED");

    private CustomerOrderDeliveryRules() {}

    public static String normalize(String status) {
        if (status == null || status.isBlank()) return "PENDING";
        return status.trim().toUpperCase();
    }

    public static int index(String status) {
        int i = STATUSES.indexOf(normalize(status));
        return i < 0 ? 0 : i;
    }

    public static boolean paymentReady(CustomerOrder order) {
        String pay = order.getPaymentState() == null ? "" : order.getPaymentState().trim().toUpperCase();
        return DISPATCH_PAYMENTS.contains(pay);
    }

    public static boolean dispatchReady(CustomerOrder order) {
        if (order.getStatus() == CustomerOrderStatus.CANCELLED) return false;
        if (order.getStatus() != CustomerOrderStatus.CONFIRMED
                && order.getCompletedSaleId() == null) return false;
        String shipping = order.getShippingState() == null ? "LEGACY" : order.getShippingState().trim().toUpperCase();
        if (shipping.isBlank()) shipping = "LEGACY";
        return paymentReady(order) && SHIPPING_READY.contains(shipping);
    }

    public static boolean awaitingCustomerReceipt(String deliveryStatus) {
        return AWAITING_RECEIPT.contains(normalize(deliveryStatus));
    }

    /** Pickup: collect remainder at the shop. Delivery: collect remainder when the rider has the goods. */
    public static boolean canCollectRemainder(CustomerOrder order) {
        if (order == null) return false;
        String pay = order.getPaymentState() == null ? "" : order.getPaymentState().trim().toUpperCase();
        if (!"DEPOSIT_PAID".equals(pay)) return false;
        if (!"DELIVERY".equalsIgnoreCase(order.getOrderType())) return true;
        return awaitingCustomerReceipt(order.getDeliveryStatus());
    }

    public static String label(String status) {
        return switch (normalize(status)) {
            case "PACKING" -> "ထုပ်ပိုးနေသည်";
            case "PACKED" -> "ထုပ်ပိုးပြီး";
            case "HANDED_TO_RIDER" -> "Rider ထံ အပ်ပြီး";
            case "OUT_FOR_DELIVERY" -> "လာပို့နေပြီ";
            case "IN_TRANSIT" -> "လမ်းမှာ";
            case "DELIVERED" -> "ဆိုင်က ရောက်သည်ဟု မှတ်ထား";
            default -> "မပို့သေး";
        };
    }

    public static void assertMutable(CustomerOrder order) {
        if (order.getStatus() == CustomerOrderStatus.CANCELLED) {
            throw new IllegalStateException("ပယ်ဖျက်ပြီး အော်ဒါ၏ ပို့ဆောင်မှုကို ပြင်မရပါ");
        }
    }

    public static void assertStatusTransition(CustomerOrder order, String requested) {
        String next = normalize(requested);
        if (!STATUSES.contains(next)) {
            throw new IllegalArgumentException("Delivery status မမှန်ကန်ပါ");
        }
        assertMutable(order);
        String current = normalize(order.getDeliveryStatus());
        if (!STATUSES.contains(current)) current = "PENDING";
        if (current.equals(next)) return;

        String receipt = order.getCustomerReceiptState() == null ? "NONE" : order.getCustomerReceiptState().trim().toUpperCase();
        if ("CONFIRMED".equals(receipt)) {
            throw new IllegalStateException("ဖောက်သည် လက်ခံအတည်ပြုပြီးသားကို ပို့ဆောင်မှု အဆင့် ပြန်ပြင်မရပါ");
        }

        int from = STATUSES.indexOf(current);
        int to = STATUSES.indexOf(next);
        if ("HANDOFF".equalsIgnoreCase(order.getDeliveryHandler() == null ? "" : order.getDeliveryHandler().trim())
                && to > STATUSES.indexOf("HANDED_TO_RIDER")) {
            throw new IllegalStateException("External delivery tracking ends at rider handoff.");
        }
        if (Math.abs(to - from) != 1) {
            throw new IllegalStateException("ပို့ဆောင်မှု အဆင့်ကို တစ်ဆင့်ချင်းသာ ပြောင်းပါ (ကျော်၍ မရပါ)");
        }
        if (to > from && !dispatchReady(order)) {
            throw new IllegalStateException("စရံရပြီး သို့မဟုတ် ငွေအပြည့်ရပြီး ပို့ရန်အသင့်ဖြစ်မှသာ ထုပ်ပိုး/ပို့ဆောင်မှု စနိုင်သည်");
        }
    }
}
