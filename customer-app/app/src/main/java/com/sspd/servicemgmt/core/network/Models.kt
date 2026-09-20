package com.sspd.servicemgmt.core.network

data class ApiResponse<T>(
    val success: Boolean = false,
    val message: String = "",
    val data: T? = null
)

data class AppVersionDTO(
    val versionCode: Int = 0,
    val versionName: String = "",
    val forceUpdate: Boolean = false,
    val changelog: String = "",
    val downloadUrl: String = ""
)

data class OrderPaymentProof(
    val id: Int? = null,
    val reference: String? = null,
    val amount: Double? = null,
    val submittedAt: String? = null,
    val image: String? = null,
    val reviewState: String? = null
)

data class OrderPaymentProofs(
    val deposit: OrderPaymentProof? = null,
    val remainder: OrderPaymentProof? = null
)

data class CustomerAuthResponse(
    val accessToken: String? = null,
    val customerId: Int? = null,
    val name: String? = null,
    val phone: String? = null,
    val address: String? = null,
    val email: String? = null,
    val needsProfile: Boolean? = false,
    val resetSent: Boolean? = false,
    val creditAllowed: Boolean? = false,
    val creditLimit: Double? = 0.0,
    val creditDays: Int? = 0,
    val creditHold: Boolean? = false,
    val creditStatusReason: String? = null
)

data class GoogleLoginRequest(val idToken: String)

data class ProfileUpdateRequest(
    val name: String? = null,
    val phone: String,
    val address: String
)

data class RegisterRequest(
    val name: String,
    val phone: String,
    val password: String,
    val address: String,
    val email: String
)

data class LoginRequest(
    val phone: String? = null,
    val login: String? = null,
    val password: String
)

data class ForgotPasswordRequest(val email: String)

data class ResetPasswordRequest(val token: String, val password: String, val googleIdToken: String? = null)

data class CustomerBranding(
    val companyName: String? = null,
    val taglineMm: String? = null,
    val logoBase64: String? = null,
    val hasLogo: Boolean? = null,
    val logoUrl: String? = null,
    val pickupDepositPercent: Double? = 30.0,
    val deliveryEnabled: Boolean? = true,
    val deliveryOpensAt: String? = "09:00",
    val deliveryClosesAt: String? = "18:00",
    val deliveryDays: String? = "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY,SUNDAY",
    val deliveryWeekdays: List<DeliveryWeekdayHours>? = null,
    val deliveryClosedDates: List<DeliveryClosedDate>? = null,
    val deliveryMinLeadDays: Int? = 1,
    val outdoorTransportationNotice: String? = null,
    val outdoorTransportationFee: Double? = null,
    val bookingRejectionMessage: String? = null,
    val outdoorBookingEnabled: Boolean? = true,
    val outdoorBookingDisabledReason: String? = null
)

data class DeliveryWeekdayHours(
    val day: String? = null,
    val open: Boolean? = true,
    val opensAt: String? = "09:00",
    val closesAt: String? = "18:00"
)

data class DeliveryClosedDate(
    val date: String? = null,
    val reason: String? = null
)

data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String
)

data class CatalogProduct(
    val id: Int = 0,
    val name: String? = null,
    val productCode: String? = null,
    val categoryId: Int? = null,
    val categoryName: String? = null,
    val parentCategoryId: Int? = null,
    val parentCategoryName: String? = null,
    val brandName: String? = null,
    val productType: String? = "New",
    val sellingPrice: Double? = 0.0,
    val warrantyMonths: Int? = 0,
    val warrantyTerms: String? = null,
    val remark: String? = null,
    val inStock: Boolean? = true,
    val stockQty: Int? = 0,
    val thumbnailUrl: String? = null,
    val photoUrls: List<String>? = emptyList()
)

data class CatalogPage(
    val content: List<CatalogProduct> = emptyList(),
    val page: Int = 0,
    val size: Int = 24,
    val totalElements: Long = 0,
    val hasNext: Boolean = false
)

data class CatalogOption(
    val id: Int = 0,
    val name: String? = null,
    val parentId: Int? = null,
    val parentName: String? = null
)

data class CatalogService(
    val id: Int = 0,
    val name: String? = null,
    val serviceTypeName: String? = null,
    val price: Double? = 0.0,
    val warrantyMonths: Int? = 0,
    val description: String? = null
)

data class ServiceRequestBody(
    val serviceName: String? = null,
    val requestType: String? = "DIAGNOSIS",
    val deviceCategory: String? = null,
    val deviceName: String? = null,
    val problem: String? = null,
    val serviceMode: String? = "UNDECIDED",
    val serviceAddress: String? = null,
    val urgency: String? = "NORMAL",
    val contactPreference: String? = "PHONE",
    val appointmentDate: String? = null,
    val serviceDate: String? = null,
    val arrivalWindowId: Int? = null,
    val preferredTime: String? = null,
    val preferredAnytime: Boolean? = true,
    val customerPreferenceNote: String? = null,
    val remark: String? = "CUSTOMER_APP",
    val photos: List<BookingRequestPhotoBody> = emptyList()
)

data class BookingAvailabilityDate(
    val date: String? = null,
    val available: Boolean? = false,
    val reason: String? = null
)

data class BookingAvailabilityWindow(
    val windowId: Int? = null,
    val name: String? = null,
    val startTime: String? = null,
    val endTime: String? = null,
    val capacity: Int? = 0,
    val booked: Int? = 0,
    val remaining: Int? = 0,
    val state: String? = null
)

data class BookingRequestPhotoBody(
    val slot: Int,
    val fileName: String? = null,
    val contentType: String = "image/jpeg",
    val dataUrl: String
)

data class BookingRequestPhotoSummary(
    val id: Int? = null,
    val slot: Int? = null,
    val imagePath: String? = null,
    val thumbnailPath: String? = null
)

data class BookingSummary(
    val id: Int = 0,
    val bookingNo: String? = null,
    val status: String? = null,
    val complaintNote: String? = null,
    val appointmentDate: String? = null,
    val source: String? = null,
    val requestedServiceName: String? = null,
    val requestType: String? = null,
    val deviceCategory: String? = null,
    val deviceName: String? = null,
    val requestedServiceMode: String? = null,
    val serviceAddress: String? = null,
    val urgency: String? = null,
    val contactPreference: String? = null,
    val serviceDate: String? = null,
    val arrivalWindowId: Int? = null,
    val preferredTime: String? = null,
    val preferredAnytime: Boolean? = null,
    val customerPreferenceNote: String? = null,
    val requestPhotos: List<BookingRequestPhotoSummary> = emptyList()
)

data class OrderLineRequest(val productId: Int, val qty: Int)

data class CustomerPaymentChannel(
    val id: Int = 0,
    val methodName: String? = null,
    val payeeName: String? = null,
    val payeeAccountNo: String? = null,
    val payeeHint: String? = null
)

data class PaymentChannelRequest(val paymentMethodId: Int)

data class DeliveryQuoteRequest(val orderType: String, val townshipId: Int?, val wardId: Int?, val lines: List<OrderLineRequest>)
data class DeliveryQuote(val state: String? = null, val weightKg: Double? = null, val baseCharge: Double? = null,
    val extraCharge: Double? = null, val deliveryCharge: Double? = null, val reason: String? = null)
data class ShippingDecision(
    val version: Int?,
    val accept: Boolean,
    val scheduledAt: String? = null,
    val reason: String? = null,
    val amount: Double? = null,
    val handler: String? = null,
    val fullPaymentRequired: Boolean? = null
)

data class OrderTimelineEvent(
    val action: String? = null,
    val actor: String? = null,
    val details: String? = null,
    val at: String? = null,
    val version: Int? = null
)

data class OrderDeliveryMilestone(
    val id: Int? = null,
    val fromStatus: String? = null,
    val toStatus: String? = null,
    val actor: String? = null,
    val actorType: String? = null,
    val note: String? = null,
    val at: String? = null
)

data class ReceiptConfirmRequest(val received: Boolean, val note: String? = null)

data class ReturnLineRequest(
    val productId: Int,
    val qty: Int,
    val serialNumber: String? = null
)

data class ReturnRequest(
    val reason: String,
    val note: String? = null,
    val saleId: Int? = null,
    val lines: List<ReturnLineRequest>
)

data class OrderRatingRequest(
    val productRating: Int,
    val serviceRating: Int,
    val comment: String? = null
)

data class OrderRating(
    val id: Int = 0,
    val productRating: Int? = null,
    val serviceRating: Int? = null,
    val rating: Int? = null,
    val comment: String? = null,
    val review: String? = null,
    val hidden: Boolean? = false,
    val editable: Boolean? = false,
    val editableUntil: String? = null
)

data class ProductReturn(
    val id: Int = 0,
    val returnNo: String? = null,
    val status: String? = null,
    val deliveryStatus: String? = null,
    val deliveryNote: String? = null,
    val inventoryApplied: Boolean = false,
    val accountingPosted: Boolean = false,
    val reason: String? = null,
    val refundAmount: Double? = null,
    val refundReference: String? = null,
    val lines: List<ProductReturnLine> = emptyList()
)

data class ProductReturnLine(
    val id: Int = 0,
    val productId: Int = 0,
    val productName: String? = null,
    val qty: Int = 0,
    val serialNumber: String? = null,
    val disposition: String? = null,
    val replacementProductId: Int? = null,
    val replacementSerialNumber: String? = null
)

data class WishlistRequest(
    val productId: Int
)

data class WishlistStatus(
    val productId: Int = 0,
    val wishlisted: Boolean = false
)

data class PromoCodeRequest(
    val code: String,
    val lines: List<OrderLineRequest> = emptyList()
)

data class PromoCodeResponse(
    val discountAmount: Double = 0.0,
    val discountPercent: Double = 0.0,
    val discountType: String? = null,
    val discountValue: Double = 0.0,
    val eligibleSubtotal: Double = 0.0,
    val itemsTotal: Double = 0.0,
    val promoCode: String? = null,
    val maxDiscount: Double? = null,
    val minOrderAmount: Double? = null
)

data class LoyaltyPoints(
    val currentPoints: Int = 0,
    val totalEarned: Int = 0,
    val pointsToNextTier: Int = 0,
    val tierName: String? = "Bronze"
)

data class PlaceOrderRequest(
    val paymentChoice: String? = null,
    val note: String? = null,
    val lines: List<OrderLineRequest>,
    val idempotencyKey: String,
    /** DELIVERY or PICKUP */
    val orderType: String,
    /** PROFILE or OTHER — when DELIVERY */
    val deliveryLocationMode: String? = null,
    val deliveryAddress: String? = null,
    val deliveryPhone: String? = null,
    val townshipId: Int? = null,
    val wardId: Int? = null,
    val requestedDeliveryAt: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationAccuracy: Double? = null,
    val locationSource: String? = null,
    val promoCode: String? = null
)

data class DeliveryRegion(
    val id: Int = 0,
    val name: String? = null,
    val kind: String? = null,
    val active: Boolean? = true,
    val sortOrder: Int? = 0,
    val townships: List<DeliveryTownship>? = emptyList()
) {
    fun townshipsOrEmpty(): List<DeliveryTownship> = townships.orEmpty()
}

data class DeliveryTownship(
    val id: Int = 0,
    val regionId: Int? = null,
    val regionName: String? = null,
    val regionKind: String? = null,
    val name: String? = null,
    val deliveryCharge: Double? = 0.0,
    val active: Boolean? = true,
    val sortOrder: Int? = 0,
    val wards: List<DeliveryWard>? = emptyList()
) {
    fun wardsOrEmpty(): List<DeliveryWard> = wards.orEmpty()
}

data class DeliveryWard(
    val id: Int = 0,
    val townshipId: Int? = null,
    val townshipName: String? = null,
    val regionId: Int? = null,
    val regionName: String? = null,
    val name: String? = null,
    val deliveryCharge: Double? = 0.0,
    val active: Boolean? = true,
    val sortOrder: Int? = 0
)

data class OrderLine(
    val productId: Int? = 0,
    val productName: String? = null,
    val qty: Int? = 0,
    val unitPrice: Double? = 0.0,
    val subtotal: Double? = 0.0,
    val discountAmount: Double? = 0.0
) {
    fun lineDiscount(): Double = discountAmount?.takeIf { it > 0.0 } ?: 0.0
}

data class CustomerOrder(
    val id: Int = 0,
    val orderNo: String? = null,
    val status: String? = null,
    val note: String? = null,
    val total: Double? = 0.0,
    val createdAt: String? = null,
    val lines: List<OrderLine>? = emptyList(),
    val orderType: String? = null,
    val deliveryLocationMode: String? = null,
    val deliveryAddress: String? = null,
    val deliveryPhone: String? = null,
    val townshipId: Int? = null,
    val townshipName: String? = null,
    val wardId: Int? = null,
    val wardName: String? = null,
    val deliveryCharge: Double? = 0.0,
    val deliveryHandler: String? = null,
    val fullPaymentRequired: Boolean? = false,
    val quotedDeliveryCharge: Double? = 0.0,
    val shippingState: String? = null,
    val shippingVersion: Int? = null,
    val shippingWeightKg: Double? = null,
    val shippingReason: String? = null,
    val itemsTotal: Double? = 0.0,
    val depositPercent: Double? = null,
    val depositAmount: Double? = null,
    val remainingAmount: Double? = null,
    val promoCode: String? = null,
    val discountAmount: Double? = 0.0,
    val deliveryStatus: String? = null,
    val deliveryCurrentLocation: String? = null,
    val deliveryScheduledAt: String? = null,
    val requestedDeliveryAt: String? = null,
    val deliveryPersonPhone: String? = null,
    val deliveredAt: String? = null,
    val customerReceiptState: String? = null,
    val customerReceivedAt: String? = null,
    val customerReceiptNote: String? = null,
    val awaitingCustomerReceipt: Boolean? = false,
    val canRate: Boolean? = false,
    val canEditRating: Boolean? = false,
    val rating: OrderRating? = null,
    val orderLatitude: Double? = null,
    val orderLongitude: Double? = null,
    val orderLocationAccuracy: Double? = null,
    val orderLocationAt: String? = null,
    val orderLocationSource: String? = null,
    val profileLatitude: Double? = null,
    val profileLongitude: Double? = null,
    val paymentState: String? = null,
    val paymentChoice: String? = null,
    val paymentMethodName: String? = null,
    val payeeName: String? = null,
    val payeeAccountNo: String? = null,
    val paymentInstructions: String? = null,
    val reservationExpiresAtEpochMillis: Long? = null,
    val paymentMethodId: Int? = null,
    val latestProofId: Int? = null,
    val collectionPaymentMethodId: Int? = null,
    val collectionPaymentMethodName: String? = null,
    val collectionAmount: Double? = null,
    val collectionProofId: Int? = null,
    val collectionReference: String? = null,
    val paymentReviewNote: String? = null,
    val completedSaleId: Int? = null,
    val completedSale: CustomerPurchase? = null,
    val timeline: List<OrderTimelineEvent>? = null,
    val deliveryMilestones: List<OrderDeliveryMilestone>? = null
)

data class PurchaseLine(
    val productName: String? = null,
    val qty: Int? = 0,
    val unitPrice: Double? = 0.0,
    val discountAmount: Double? = 0.0,
    val subtotal: Double? = 0.0,
    val warrantyMonths: Int? = 0,
    val warrantyStartDate: String? = null,
    val warrantyExpiryDate: String? = null,
    val warrantyStatus: String? = null,
    val warrantyDaysRemaining: Long? = null,
    val serialNumber: String? = null
) {
    fun lineDiscount(): Double = discountAmount?.takeIf { it > 0.0 } ?: 0.0
}

data class CustomerPurchase(
    val id: Int = 0,
    val saleCode: String? = null,
    val saleDate: String? = null,
    val netAmount: Double? = 0.0,
    val totalAmount: Double? = 0.0,
    val discountAmount: Double? = 0.0,
    val paymentStatus: String? = null,
    val lines: List<PurchaseLine>? = emptyList()
) {
    fun overallDiscount(): Double = discountAmount?.takeIf { it > 0.0 } ?: 0.0
    fun lineDiscountTotal(): Double = lines.orEmpty().sumOf { it.lineDiscount() }
}

data class JobServiceLine(
    val name: String? = null,
    val qty: Int? = 0,
    val unitPrice: Double? = null,
    val price: Double? = null,
    val discountAmount: Double? = null,
    val subtotal: Double? = null,
    val warrantyMonths: Int? = 0,
    val warrantyCovered: Boolean? = false,
    val warrantyStartDate: String? = null,
    val warrantyExpiryDate: String? = null,
    val warrantyStatus: String? = null
) {
    fun chargedAmount(): Double {
        if (warrantyCovered == true) return 0.0
        subtotal?.takeIf { it > 0.0 }?.let { return it }
        return (chargedUnit() * (qty ?: 1).coerceAtLeast(1) - lineDiscount()).coerceAtLeast(0.0)
    }

    fun chargedUnit(): Double {
        unitPrice?.takeIf { it > 0.0 }?.let { return it }
        price?.takeIf { it > 0.0 }?.let { return it }
        return 0.0
    }

    fun lineDiscount(): Double = discountAmount?.takeIf { it > 0.0 } ?: 0.0
}

data class JobPartLine(
    val productName: String? = null,
    val qty: Int? = 0,
    val unitPrice: Double? = null,
    val price: Double? = null,
    val discountAmount: Double? = null,
    val subtotal: Double? = null,
    val warrantyMonths: Int? = 0,
    val warrantyCovered: Boolean? = false,
    val warrantyStartDate: String? = null,
    val warrantyExpiryDate: String? = null,
    val warrantyStatus: String? = null,
    val serialNumber: String? = null
) {
    fun chargedAmount(): Double {
        if (warrantyCovered == true) return 0.0
        subtotal?.takeIf { it > 0.0 }?.let { return it }
        return (chargedUnit() * (qty ?: 1).coerceAtLeast(1) - lineDiscount()).coerceAtLeast(0.0)
    }

    fun chargedUnit(): Double {
        unitPrice?.takeIf { it > 0.0 }?.let { return it }
        price?.takeIf { it > 0.0 }?.let { return it }
        return 0.0
    }

    fun lineDiscount(): Double = discountAmount?.takeIf { it > 0.0 } ?: 0.0
}

data class CustomerJob(
    val id: Int = 0,
    val jobNo: String? = null,
    val status: String? = null,
    val itemName: String? = null,
    val deviceType: String? = null,
    val problemDesc: String? = null,
    val serviceMode: String? = null,
    val bookingId: Int? = null,
    val bookingNo: String? = null,
    val receivedDate: String? = null,
    val appointmentDate: String? = null,
    val workStartedAt: String? = null,
    val completedDate: String? = null,
    val deliveredDate: String? = null,
    val assignedStaffName: String? = null,
    val helperStaffName: String? = null,
    val hasHelper: Boolean? = false,
    val technicians: List<JobCrewMember>? = emptyList(),
    val totalAmount: Double? = 0.0,
    val discountAmount: Double? = 0.0,
    val netAmount: Double? = 0.0,
    val paymentStatus: String? = null,
    val completedSaleId: Int? = null,
    val saleCode: String? = null,
    val completedSale: CustomerPurchase? = null,
    val paidAmount: Double? = 0.0,
    val dueAmount: Double? = 0.0,
    val laborNetAmount: Double? = 0.0,
    val partsNetAmount: Double? = 0.0,
    val services: List<JobServiceLine>? = emptyList(),
    val parts: List<JobPartLine>? = emptyList()
) {
    fun canOpenServiceInvoice(): Boolean {
        val s = status?.uppercase().orEmpty()
        return s in setOf("COMPLETED", "DELIVERED", "READY_FOR_DELIVERY")
                || !paymentStatus.isNullOrBlank()
                || (netAmount ?: 0.0) > 0.0
    }

    fun overallDiscount(): Double = discountAmount?.takeIf { it > 0.0 } ?: 0.0

    fun lineDiscountTotal(): Double =
        services.orEmpty().sumOf { it.lineDiscount() } + parts.orEmpty().sumOf { it.lineDiscount() }

    fun isOutdoor(): Boolean = serviceMode?.equals("OUTDOOR", ignoreCase = true) == true

    fun serviceModeLabel(): String = if (isOutdoor()) "Outdoor Job" else "Indoor Job"

    fun crewMembers(): List<JobCrewMember> {
        val fromApi = technicians.orEmpty().filter { !it.name.isNullOrBlank() }
        if (fromApi.isNotEmpty()) return fromApi
        val fallback = mutableListOf<JobCrewMember>()
        assignedStaffName?.takeIf { it.isNotBlank() }?.let {
            fallback += JobCrewMember(name = it, role = "TECHNICIAN", roleLabel = "Technician")
        }
        helperStaffName?.takeIf { it.isNotBlank() }?.let {
            fallback += JobCrewMember(name = it, role = "HELPER", roleLabel = "Helper")
        }
        return fallback
    }
}

data class JobCrewMember(
    val name: String? = null,
    val role: String? = null,
    val roleLabel: String? = null
) {
    fun isHelper(): Boolean = role?.equals("HELPER", ignoreCase = true) == true
}

data class CustomerNotification(
    val id: Int = 0,
    val jobId: Int? = null,
    val jobNo: String? = null,
    val channel: String? = null,
    val note: String? = null,
    val notifiedAt: String? = null,
    val orderId: Int? = null,
    val orderNo: String? = null,
    val status: String? = null
)

// Added PaymentChoiceRequest for resolving unresolved reference error
data class PaymentChoiceRequest(
    val paymentChoice: String
)

data class ChatMessage(
    val id: Int = 0,
    val senderId: Int = 0,
    val text: String = "",
    val createdAt: String? = null,
    val isFromAdmin: Boolean = false
)

data class ChatRequest(
    val text: String
)
