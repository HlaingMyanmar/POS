package com.sspd.servicemgmt.core.network

// ─── Generic wrappers ────────────────────────────────────────────────────────

data class ApiResponse<T>(
    val success: Boolean = false,
    val message: String = "",
    val data: T? = null
)

data class AppVersionDTO(
    val versionCode: Int     = 0,
    val versionName: String  = "",
    val forceUpdate: Boolean = false,
    val changelog:   String  = "",
    val downloadUrl: String  = ""
)

data class PagedResponse<T>(
    val content: List<T> = emptyList(),
    val currentPage: Int = 0,
    val totalPages: Int = 0,
    val totalElements: Long = 0
)

// ─── Auth ────────────────────────────────────────────────────────────────────

data class LoginRequest(val usernameOremail: String, val password: String)

data class AuthResponse(
    val accessToken: String,
    val refreshToken: String? = null,
    val username: String,
    val name: String? = null,
    val phone: String? = null,
    val staffId: Int? = null,
    val roles: List<String> = emptyList(),
    val permissions: List<String> = emptyList()
)

// ─── Dashboard ───────────────────────────────────────────────────────────────

data class DashboardStats(
    val todaySalesAmount: Long? = null,
    val todaySalesCount: Long? = null,
    val lowStockCount: Long? = null,
    val pendingServiceJobs: Long? = null,
    val totalOverdueAR: Long? = null,
    val overdueARCount: Long? = null,
    val totalPendingAR: Long? = null,
    val pendingARCount: Long? = null,
    val lowStockProducts: List<String>? = null,
    val recentSales: List<RecentSaleDTO>? = null
)

data class RecentSaleDTO(
    val id: Int? = null,
    val saleCode: String? = null,
    val customerName: String? = null,
    val amount: Long? = null,
    val date: String? = null,
    val status: String? = null
)

// ─── Products / Stock ────────────────────────────────────────────────────────

data class ProductDTO(
    val id: Int = 0,
    val productCode: String = "",
    val name: String = "",
    val stockQty: Int = 0,
    val quarantinedQty: Int? = null,
    val availableSerialCount: Int? = null,
    val productType: String = "",
    val sellingPrice: Double = 0.0,
    val costPrice: Double? = null,
    val lastPurchaseCost: Double? = null,
    val categoryId: Int? = null,
    val categoryName: String? = null,
    val brandId: Int? = null,
    val brandName: String? = null,
    val unitId: Int? = null,
    val unitName: String? = null,
    val reorderLevel: Int? = null,
    val shortageQty: Int? = null,
    val hasSerial: Boolean? = null,
    val warrantyMonths: Int? = null,
    val warrantyTerms: String? = null,
    val remark: String? = null,
    val photoBase64: String? = null
)

data class BrandDTO(
    val id: Int? = null,
    val name: String = "",
    val isActive: Boolean = true
)

data class CategoryDTO(
    val id: Int? = null,
    val name: String = "",
    val parentId: Int? = null,
    val isActive: Boolean = true,
    val children: List<CategoryDTO>? = null
)

data class UnitDTO(
    val id: Int? = null,
    val name: String = "",
    val unitName: String? = null,
    val symbol: String? = null,
    val description: String? = null,
    val isActive: Boolean = true
)

data class AssignSerialsRequest(
    val serialNumbers: List<String>,
    val warrantyMonths: Int? = null
)

data class ProductSerialDTO(
    val id: Int? = null,
    val serialNumber: String = "",
    val status: String? = null,
    val productId: Int? = null,
    val productCode: String? = null,
    val productName: String? = null,
    val warrantyMonths: Int? = null,
    val warrantyStartDate: String? = null,
    val warrantyEndDate: String? = null,
    val condition: String? = null,
    val photoBase64: String? = null
)

// ─── Sale Returns ─────────────────────────────────────────────────────────────

data class SaleReturnDetailDTO(
    val id:            Int?          = null,
    val returnId:      Int?          = null,
    val productId:     Int?          = null,
    val productName:   String?       = null,
    val qty:           Int?          = null,
    val unitPrice:     Double?       = null,
    val subtotal:      Double?       = null,
    val serialNumbers: List<String>? = null
)

data class SaleReturnDTO(
    val id:                Int?                    = null,
    val saleId:            Int?                    = null,
    val saleCode:          String?                 = null,
    val customerId:        Int?                    = null,
    val customerName:      String?                 = null,
    val staffId:           Int?                    = null,
    val returnCode:        String?                 = null,
    val returnDate:        String?                 = null,
    val totalReturnAmount: Double?                 = null,
    val refundAmount:      Double?                 = null,
    val paymentMethodId:   Int?                    = null,
    val paymentMethodName: String?                 = null,
    val transactionNo:     String?                 = null,
    val payments:          List<PaymentTransactionDTO>? = null,
    val reason:            String?                 = null,
    val details:           List<SaleReturnDetailDTO>? = null
)

// ─── Sales ───────────────────────────────────────────────────────────────────

data class PaymentTransactionDTO(
    val id: Int? = null,
    val referenceId: Int? = null,
    val referenceType: String? = null,
    val paymentMethodId: Int? = null,
    val paymentMethodName: String? = null,
    val amount: Double? = null,
    val transactionNo: String? = null,
    val paymentDate: String? = null,
    val referenceCode: String? = null,
    val entityName: String? = null,
    val reversed: Boolean? = null,
    val reversedAt: String? = null,
    val reversedBy: String? = null,
    val reversalReason: String? = null
)

data class SalePaymentRequest(
    val paidAmount:      Double,
    val paymentMethodId: Int? = null,
    val paymentAccountId: Int? = null,
    val arAccountId:     Int? = null,
    val note:            String? = null,
    val staffId:         Int?    = null,
    val transactionNo:   String? = null,
    val payments:        List<PaymentTransactionDTO>? = null
)

data class SaleItemDTO(
    val productId: Int? = null,
    val productName: String? = null,
    val qty: Int? = null,
    val unitPrice: Double? = null,
    val customVoucherPrice: Double? = null,
    val customerMargin: Double? = null,
    val subtotal: Double? = null,
    val discountAmount: Double? = null,
    val foc: Boolean? = null,
    val warrantyMonths: Int? = null,
    val warrantyExpiryDate: String? = null,
    val serialNumbers: List<String>? = null
)

data class CustomerCreditTermDTO(
    val id: Int? = null,
    val customerId: Int? = null,
    val creditLimit: Double? = null,
    val creditDays: Int? = null,
    val creditAllowed: Boolean? = null,
    val customerName: String? = null
)

data class PaymentMethodDTO(
    val id: Int = 0,
    val methodName: String = "",
    val active: Boolean = true
)

data class SaleDTO(
    val id: Int? = null,
    val saleCode: String? = null,
    val customerId: Int? = null,
    val customerName: String? = null,
    val staffId: Int? = null,
    val staffName: String? = null,
    val saleDate: String? = null,
    val totalAmount: Double? = null,
    val discountAmount: Double? = null,
    val taxAmount: Double? = null,
    val foc: Boolean? = null,
    val netAmount: Double? = null,
    val paidAmount: Double? = null,
    val dueAmount: Double? = null,
    val paymentStatus: String? = null,
    val creditStatus: String? = null,
    val voided: Boolean? = null,
    val voidReason: String? = null,
    val voidedBy: String? = null,
    val voidedAt: String? = null,
    val quotationId: Int? = null,
    val quotationCode: String? = null,
    val managerOverride: Boolean? = null,
    val managerId: Int? = null,
    val overrideNote: String? = null,
    val paymentAccountId: Int? = null,
    val paymentMethodId: Int? = null,
    val paymentMethodName: String? = null,
    val payments: List<PaymentTransactionDTO>? = null,
    val transactionNo: String? = null,
    val arAccountId: Int? = null,
    val serviceJobSale: Boolean = false,
    val dueDate: String? = null,
    val remark: String? = null,
    val details: List<SaleItemDTO>? = null
)

// ─── Bookings ────────────────────────────────────────────────────────────────

data class BookingDTO(
    val id: Int? = null,
    val invoiceNo: String? = null,
    val customerId: Int? = null,
    val customerName: String? = null,
    val customerPhone: String? = null,
    val staffId: Int? = null,
    val staffName: String? = null,
    val paymentMethodId: Int? = null,
    val paymentMethodName: String? = null,
    val bookingDate: String? = null,
    val appointmentDate: String? = null,
    val status: String? = null,
    val totalAmount: Double? = null,
    val depositAmount: Double? = null,
    val signatureData: String? = null,
    val brand: String? = null,
    val model: String? = null,
    val deviceType: String? = null,
    val serialNumber: String? = null,
    val color: String? = null,
    val accessories: String? = null,
    val problemDesc: String? = null,
    val shelfLocation: String? = null,
    val remark: String? = null,
    val devices: List<BookingDeviceDTO>? = null,
    val details: List<BookingDetailItemDTO>? = null,
    val deviceInfos: List<BookingDeviceInfoDTO>? = null,
    val attachments: List<BookingAttachmentDTO>? = null
)

// ─── Service Jobs ────────────────────────────────────────────────────────────

data class ServiceTypeDTO(
    val id: Int? = null,
    val name: String = "",
    val description: String? = null,
    val isActive: Boolean = true
)

data class SubServiceTypeDTO(
    val id: Int? = null,
    val name: String = "",
    val description: String? = null,
    val isActive: Boolean = true,
    val serviceTypeId: Int? = null,
    val serviceTypeName: String? = null
)

data class ServiceItemDTO(
    val id: Int? = null,
    val code: String? = null,
    val item: String = "",
    val price: Double = 0.0,
    val costPrice: Double? = null,
    val minPrice: Double? = null,
    val maxPrice: Double? = null,
    val warrantyMonths: Int? = null,
    val durationMinutes: Int? = null,
    val description: String? = null,
    val focDefault: Boolean? = null,
    val taxRate: Double? = null,
    val skillRequired: String? = null,
    val commissionPercent: Double? = null,
    val supportedDeviceTypes: String? = null,
    val defaultRequiredParts: String? = null,
    val isActive: Boolean = true,
    val serviceTypeId: Int? = null,
    val serviceTypeName: String? = null,
    val subServiceTypeId: Int? = null,
    val subServiceTypeName: String? = null
)

data class ServicePriceHistoryDTO(
    val id: Int? = null,
    val serviceItemId: Int? = null,
    val oldPrice: Double? = null,
    val newPrice: Double? = null,
    val oldCost: Double? = null,
    val newCost: Double? = null,
    val changedBy: String? = null,
    val changedAt: String? = null
)

data class ShelfLocationDTO(
    val id: Int? = null,
    val code: String = "",
    val label: String? = null,
    val active: Boolean = true
)

data class BookingDeviceDTO(
    val id: Int? = null,
    val deviceType: String? = null,
    val brand: String? = null,
    val model: String? = null,
    val serialNumber: String? = null,
    val color: String? = null,
    val accessories: String? = null,
    val problemDesc: String? = null,
    val deviceConditions: String? = null
)

data class BookingDeviceInfoDTO(
    val id: Int? = null,
    val name: String? = null,
    val description: String? = null,
    val status: String? = null,
    val notice: String? = null
)

data class BookingAttachmentDTO(
    val id: Int? = null,
    val attachmentType: String? = null,
    val fileName: String? = null,
    val contentType: String? = null,
    val dataUrl: String? = null,
    val uploadedBy: String? = null,
    val uploadedAt: String? = null
)

data class BookingDetailItemDTO(
    val id: Int? = null,
    val serviceId: Int? = null,
    val serviceName: String? = null,
    val qty: Int? = null,
    val price: Double? = null,
    val subtotal: Double? = null
)

data class ServiceJobLineDTO(
    val id: Int? = null,
    val serviceItemId: Int? = null,
    val serviceItemName: String? = null,
    val qty: Int? = null,
    val catalogPrice: Double? = null,
    val estimatedPrice: Double? = null,
    val approvedPrice: Double? = null,
    val billedPrice: Double? = null,
    val price: Double? = null,
    val subtotal: Double? = null,
    val minPrice: Double? = null,
    val maxPrice: Double? = null,
    val priceChangeReason: String? = null,
    val priceOverrideApproved: Boolean? = null,
    val priceOverrideApprovedBy: String? = null,
    val warrantyMonths: Int? = null,
    val warrantyCovered: Boolean? = null,
    val confirmationStatus: String? = null
)

data class ServiceJobPartDTO(
    val id: Int? = null,
    val productId: Int? = null,
    val productName: String? = null,
    val productCode: String? = null,
    val qty: Int? = null,
    val unitPrice: Double? = null,
    val discountAmount: Double? = null,
    val subtotal: Double? = null,
    val serialNumbers: List<String>? = null,
    val warrantyCovered: Boolean? = null
)

data class SettleJobRequest(
    val finalCost:        Double,
    val discountAmount:   Double  = 0.0,
    val foc:              Boolean = false,
    val paidAmount:       Double,
    val paymentMethodId:  Int?    = null,
    val transactionNo:    String? = null,
    val payments:         List<PaymentTransactionDTO>? = null,
    val dueDate:          String? = null
)

data class ServiceJobPayDueRequest(
    val paidAmount:      Double,
    val paymentMethodId: Int? = null,
    val transactionNo:   String? = null,
    val payments:        List<PaymentTransactionDTO>? = null,
    val note:            String? = null
)

data class ReworkRequestDTO(
    val reworkType: String,
    val problemDesc: String? = null,
    val assignedStaffId: Int? = null,
    val replacementItemName: String? = null,
    val replacementSerialNo: String? = null,
    val replacementReason: String? = null,
    val resolutionMode: String? = "SERVICE_ONLY",
    val originalPartId: Int? = null,
    val oldPartDisposition: String? = null,
    val replacementProductId: Int? = null,
    val replacementQty: Int? = null,
    val replacementSerialNumbers: List<String>? = null,
    val warrantyCredit: Double? = null,
    val refundAmount: Double? = null,
    val refundPaymentMethodId: Int? = null,
    val refundTransactionNo: String? = null
)

data class ServiceJobAttachmentDTO(
    val id: Int? = null,
    val attachmentType: String? = null,
    val fileName: String? = null,
    val contentType: String? = null,
    val dataUrl: String? = null,
    val uploadedBy: String? = null,
    val uploadedAt: String? = null
)

data class ServiceJobActivityDTO(
    val id: Int? = null,
    val eventType: String? = null,
    val fromStatus: String? = null,
    val toStatus: String? = null,
    val note: String? = null,
    val actor: String? = null,
    val occurredAt: String? = null
)

data class ServiceJobNotificationDTO(
    val id: Int? = null,
    val channel: String? = null,
    val note: String? = null,
    val actor: String? = null,
    val notifiedAt: String? = null
)

data class CustomerCreditApplyRequest(
    val customerId: Int,
    val saleId: Int? = null,
    val serviceJobId: Int? = null,
    val staffId: Int,
    val amount: Double,
    val reason: String? = null
)

data class CustomerCreditSummaryDTO(
    val advanceBalance: Double? = null,
    val availableCredit: Double? = null
)

data class CustomerCreditApplyResultDTO(
    val applicationNo: String? = null,
    val amount: Double? = null,
    val remainingDue: Double? = null,
    val advanceBalance: Double? = null
)

data class ServiceJobDTO(
    val id: Int? = null,
    val jobNo: String? = null,
    val customerId: Int? = null,
    val customerName: String? = null,
    val customerPhone: String? = null,
    val assignedStaffId: Int? = null,
    val assignedStaffName: String? = null,
    val itemName: String? = null,
    val deviceType: String? = null,
    val itemCondition: String? = null,
    val deviceConditions: String? = null,
    val partRequests: String? = null,
    val problemDesc: String? = null,
    val diagnosisNotes: String? = null,
    val accessories: String? = null,
    val estimatedCost: Double? = null,
    val finalCost: Double? = null,
    val discountAmount: Double? = null,
    val foc: Boolean? = null,
    val status: String? = null,
    val netAmount: Double? = null,
    val paidAmount: Double? = null,
    val dueAmount: Double? = null,
    val dueDate: String? = null,
    val paymentStatus: String? = null,
    val paymentMethodId: Int? = null,
    val paymentMethodName: String? = null,
    val payments: List<PaymentTransactionDTO>? = null,
    val receivedDate: String? = null,
    val estimatedCompletion: String? = null,
    val completedDate: String? = null,
    val deliveredDate: String? = null,
    val rework: Boolean? = null,
    val parentJobId: Int? = null,
    val parentJobNo: String? = null,
    val reworkType: String? = null,
    val replacementItemName: String? = null,
    val replacementSerialNo: String? = null,
    val replacementReason: String? = null,
    val resolutionMode: String? = null,
    val oldPartDisposition: String? = null,
    val originalPartName: String? = null,
    val originalPartCode: String? = null,
    val originalPartSerialNumbers: List<String>? = null,
    val replacementProductName: String? = null,
    val replacementProductCode: String? = null,
    val replacementPartSerialNumbers: List<String>? = null,
    val replacementQty: Int? = null,
    val warrantyCredit: Double? = null,
    val replacementPrice: Double? = null,
    val customerCharge: Double? = null,
    val refundAmount: Double? = null,
    val refundPaymentMethodName: String? = null,
    val refundTransactionNo: String? = null,
    val refundDate: String? = null,
    val bookingId: Int? = null,
    val bookingNo: String? = null,
    val serialNo: String? = null,
    val color: String? = null,
    val shelfLocationId: Int? = null,
    val shelfLocationCode: String? = null,
    val shelfLocationLabel: String? = null,
    val remark: String? = null,
    val voided: Boolean? = null,
    val voidReason: String? = null,
    val estimateApproved: Boolean? = null,
    val estimateApprovedAt: String? = null,
    val estimateApprovedBy: String? = null,
    val priority: String? = null,
    val helperStaffId: Int? = null,
    val helperStaffName: String? = null,
    val holdReason: String? = null,
    val workStartedAt: String? = null,
    val lastNotifiedAt: String? = null,
    val modifiedBy: String? = null,
    val modifiedAt: String? = null,
    val technicianMinutes: Long? = null,
    val overdue: Boolean? = null,
    val lines: List<ServiceJobLineDTO>? = null,
    val productParts: List<ServiceJobPartDTO>? = null,
    val activities: List<ServiceJobActivityDTO>? = null,
    val attachments: List<ServiceJobAttachmentDTO>? = null,
    val notifications: List<ServiceJobNotificationDTO>? = null
)

data class TechnicianVisitDTO(
    val id: Long? = null,
    val staffId: Int? = null,
    val staffName: String? = null,
    val jobId: Int? = null,
    val jobNo: String? = null,
    val customerId: Int? = null,
    val customerName: String? = null,
    val status: String? = null,
    val motionStatus: String? = null,
    val needsReason: Boolean? = null,
    val startedAt: String? = null,
    val arrivedAt: String? = null,
    val endedAt: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracy: Double? = null,
    val recordedAt: String? = null,
    val customerLatitude: Double? = null,
    val customerLongitude: Double? = null,
    val distanceMeters: Double? = null
)

data class LocationPingRequest(
    val clientPingId: String,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Double? = null,
    val recordedAt: String
)

data class VisitReasonRequest(
    val reasonCode: String,
    val note: String? = null
)

data class CustomerLocationRequest(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Double? = null,
    val source: String? = null
)

// ─── Staff ───────────────────────────────────────────────────────────────────

data class StaffDTO(
    val id: Int = 0,
    val name: String = "",
    val phone: String? = null,
    val role: String = "",
    val isActive: Boolean = true,
    val basicSalary: Long? = null
)

data class StaffReportDTO(
    val staffId: Int = 0,
    val staffName: String = "",
    val staffRole: String = "",
    val salesCount: Int = 0,
    val salesAmount: Long = 0,
    val serviceJobsCount: Int = 0,
    val completedJobsCount: Int = 0,
    val cancelledJobsCount: Int = 0,
    val reworkJobsCount: Int = 0,
    val inProgressJobsCount: Int = 0,
    val serviceJobsAmount: Long = 0,
    val completionRate: Double = 0.0
)

// ─── Customers ───────────────────────────────────────────────────────────────

data class CustomerDTO(
    val id: Int? = null,
    val name: String = "",
    val phone: String? = null,
    val address: String? = null,
    val creditHold: Boolean = false,
    val blacklisted: Boolean = false,
    val creditHoldReason: String? = null,
    val blacklistReason: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null
)

// ─── Suppliers ───────────────────────────────────────────────────────────────

data class SupplierDTO(
    val id: Int = 0,
    val code: String? = null,
    val name: String = "",
    val phone: String? = null,
    val address: String? = null,
    val openingBalance: Double? = null,
    val currentBalance: Double? = null,
    val defaultCreditDays: Int? = null,
    val creditLimit: Double? = null
)

// ─── Purchases ───────────────────────────────────────────────────────────────

data class PurchaseDTO(
    val id: Int? = null,
    val purchaseCode: String? = null,
    val supplierId: Int? = null,
    val supplierName: String? = null,
    val staffId: Int? = null,
    val staffName: String? = null,
    val purchaseDate: String? = null,
    val dueDate: String? = null,
    val paymentTermDays: Int? = null,
    val totalAmount: Double? = null,
    val discountAmount: Double? = null,
    val paidAmount: Double? = null,
    val returnAmount: Double? = null,
    val refundAmount: Double? = null,
    val netAmount: Double? = null,
    val supplierCreditAmount: Double? = null,
    val dueAmount: Double? = null,
    val paymentStatus: String? = null,
    val remark: String? = null,
    val details: List<PurchaseItemDTO>? = null,
    val paymentMethodId: Int? = null,
    val paymentMethodName: String? = null,
    val payments: List<PaymentTransactionDTO>? = null,
    val transactionNo: String? = null,
    val status: String? = null,
    val taxAmount: Double? = null,
    val taxMode: String? = null,
    val taxRate: Double? = null,
    val withholdingTaxAmount: Double? = null,
    val otherCharges: Double? = null,
    val landedCostAllocationMethod: String? = null,
    val currencyCode: String? = null,
    val exchangeRate: Double? = null,
    val foreignNetAmount: Double? = null,
    val attachmentName: String? = null,
    val attachmentData: String? = null,
    val poId: Int? = null,
    val poCode: String? = null,
    val supplierInvoiceNo: String? = null,
    val warehouseName: String? = null,
    val cancelReason: String? = null,
    val cancelledBy: String? = null,
    val cancelledAt: String? = null,
    val creditLimitOverride: Boolean? = null,
    val creditOverrideReason: String? = null,
    val creditOverrideBy: String? = null,
    val creditOverrideAt: String? = null
)

data class ReorderSuggestionDTO(
    val productId: Int? = null,
    val productName: String? = null,
    val productCode: String? = null,
    val hasSerial: Boolean? = null,
    val stockQty: Int? = null,
    val reorderLevel: Int? = null,
    val suggestedQty: Int? = null,
    val lastCost: Double? = null
)

data class PurchaseOrderDetailDTO(
    val id: Int? = null, val productId: Int? = null, val productName: String? = null,
    val hasSerial: Boolean? = null,
    val qty: Int? = null, val receivedQty: Int? = null, val unitCost: Double? = null,
    val subtotal: Double? = null, val warrantyMonths: Int? = null,
    val itemWarranties: List<Int>? = null,
    val serialNumbers: List<String>? = null, val serialConditions: List<String>? = null,
    val serialPhotos: List<String>? = null,
    val batchNumber: String? = null,
    val expiryDate: String? = null
)

data class PurchaseOrderDTO(
    val id: Int? = null, val poCode: String? = null, val supplierId: Int? = null,
    val supplierName: String? = null, val staffId: Int? = null, val staffName: String? = null,
    val orderDate: String? = null, val expectedDate: String? = null, val status: String? = null,
    val totalAmount: Double? = null, val remark: String? = null,
    val details: List<PurchaseOrderDetailDTO>? = null
)

data class PurchaseOrderReceiveLineDTO(
    val detailId: Int? = null,
    val qty: Int? = null,
    val warrantyMonths: Int? = null,
    val itemWarranties: List<Int>? = null,
    val serialNumbers: List<String>? = null,
    val serialConditions: List<String>? = null,
    val serialPhotos: List<String>? = null,
    val batchNumber: String? = null,
    val expiryDate: String? = null
)

data class PurchaseOrderReceiveRequest(
    val staffId: Int? = null, val lines: List<PurchaseOrderReceiveLineDTO>? = null,
    val dueDate: String? = null, val discountAmount: Double? = null,
    val taxAmount: Double? = null, val otherCharges: Double? = null,
    val paymentMethodId: Int? = null, val transactionNo: String? = null,
    val payments: List<PaymentTransactionDTO>? = null, val remark: String? = null,
    val supplierInvoiceNo: String? = null
)

data class PurchaseOrderReceiveResultDTO(
    val order: PurchaseOrderDTO? = null,
    val purchase: PurchaseDTO? = null
)

data class PurchaseOrderRejectRequest(
    val reason: String? = null
)

data class SupplierPayable(
    val purchaseId: Int? = null,
    val purchaseCode: String? = null,
    val purchaseDate: String? = null,
    val dueDate: String? = null,
    val netAmount: Double? = null,
    val paidAmount: Double? = null,
    val dueAmount: Double? = null
)

data class SupplierPaymentAllocationDTO(
    val purchaseId: Int? = null,
    val purchaseCode: String? = null,
    val amount: Double? = null,
    val remainingDue: Double? = null
)

data class SupplierPaymentDTO(
    val id: Int? = null,
    val paymentNo: String? = null,
    val supplierId: Int? = null,
    val supplierName: String? = null,
    val paymentMethodId: Int? = null,
    val paymentMethodName: String? = null,
    val totalAmount: Double? = null,
    val allocatedAmount: Double? = null,
    val advanceAmount: Double? = null,
    val paymentDate: String? = null,
    val transactionNo: String? = null,
    val paidBy: String? = null,
    val remark: String? = null,
    val allocations: List<SupplierPaymentAllocationDTO>? = null
)

data class SupplierPaymentRequest(
    val supplierId: Int,
    val staffId: Int,
    val paymentMethodId: Int,
    val amount: Double,
    val transactionNo: String? = null,
    val remark: String? = null,
    val allocations: List<SupplierPaymentAllocationRequest>? = null
)

data class SupplierPaymentAllocationRequest(
    val purchaseId: Int,
    val amount: Double
)

data class SupplierCreditSummaryDTO(
    val advanceBalance: Double? = null,
    val returnCreditBalance: Double? = null,
    val availableCredit: Double? = null
)

data class SupplierCreditApplyRequest(
    val supplierId: Int,
    val purchaseId: Int,
    val staffId: Int,
    val amount: Double,
    val reason: String? = null
)

data class SupplierCreditApplyResultDTO(
    val applicationNo: String? = null,
    val amount: Double? = null,
    val remainingDue: Double? = null
)

data class PurchaseItemDTO(
    val id: Int? = null,
    val productId: Int? = null,
    val productName: String? = null,
    val qty: Int? = null,
    val unitCost: Double? = null,
    val subtotal: Double? = null,
    val batchNumber: String? = null,
    val expiryDate: String? = null,
    val warrantyMonths: Int? = null,
    val itemWarranties: List<Int>? = null,
    val serialNumbers: List<String>? = null,
    val serialConditions: List<String>? = null,
    val serialPhotos: List<String>? = null,
    val allocatedLandedCost: Double? = null
)

data class PurchaseReturnDetailDTO(
    val id: Int? = null,
    val returnId: Int? = null,
    val productId: Int? = null,
    val productName: String? = null,
    val qty: Int? = null,
    val unitPrice: Double? = null,
    val subtotal: Double? = null,
    val allocatedShippingCost: Double? = null,
    val serialNumbers: List<String>? = null,
    val reasonId: Int? = null,
    val reasonCode: String? = null,
    val reasonName: String? = null,
    val quarantinedQty: Int? = null,
    val dispatchedQty: Int? = null
)

data class PurchaseReturnReasonDTO(
    val id: Int? = null,
    val code: String = "",
    val name: String = "",
    val description: String? = null,
    val active: Boolean = true
)

data class PurchaseReturnDTO(
    val id: Int? = null,
    val purchaseId: Int? = null,
    val purchaseCode: String? = null,
    val supplierName: String? = null,
    val returnNo: String? = null,
    val returnDate: String? = null,
    val totalReturnAmount: Double? = null,
    val refundAmount: Double? = null,
    val paymentMethodId: Int? = null,
    val paymentMethodName: String? = null,
    val transactionNo: String? = null,
    val payments: List<PaymentTransactionDTO>? = null,
    val status: String? = null,
    val version: Long? = null,
    val voidedAt: String? = null,
    val voidReason: String? = null,
    val reason: String? = null,
    val submittedBy: String? = null,
    val submittedAt: String? = null,
    val approvedBy: String? = null,
    val approvedAt: String? = null,
    val approvalNote: String? = null,
    val carrier: String? = null,
    val trackingNo: String? = null,
    val dispatchedAt: String? = null,
    val supplierReceivedAt: String? = null,
    val deliveryProof: String? = null,
    val shippingCostAmount: Double? = null,
    val shippingPayerResponsibility: String? = null,
    val companyShippingPortion: Double? = null,
    val supplierShippingPortion: Double? = null,
    val shippingAllocationMethod: String? = null,
    val shippingPaymentMethodId: Int? = null,
    val shippingPaymentMethodName: String? = null,
    val shippingTransactionReference: String? = null,
    val shippingPostedAt: String? = null,
    val shippingPaymentTransaction: PaymentTransactionDTO? = null,
    val settlementType: String? = null,
    val expectedCreditAmount: Double? = null,
    val supplierCreditNoteNo: String? = null,
    val supplierCreditNoteAmount: Double? = null,
    val creditVariance: Double? = null,
    val creditVarianceReason: String? = null,
    val settledAt: String? = null,
    val settlementReference: String? = null,
    val details: List<PurchaseReturnDetailDTO>? = null
)

// ─── Expenses / Income ───────────────────────────────────────────────────────

data class ChartOfAccountDTO(
    val id:          Int?    = null,
    val accountName: String? = null,
    val accountType: String? = null,
    val code:        String? = null
)

data class ExpenseDTO(
    val id:                Int?    = null,
    val expenseCode:       String? = null,
    val expenseDate:       String? = null,
    val accountId:         Int?    = null,
    val accountName:       String? = null,
    val paymentMethodId:   Int?    = null,
    val paymentMethodName: String? = null,
    val amount:            Long    = 0,
    val description:       String? = null,
    val staffId:           Int?    = null,
    val staffName:         String? = null
)

data class IncomeDTO(
    val id:                Int?    = null,
    val incomeCode:        String? = null,
    val incomeDate:        String? = null,
    val accountId:         Int?    = null,
    val accountName:       String? = null,
    val paymentMethodId:   Int?    = null,
    val paymentMethodName: String? = null,
    val amount:            Long    = 0,
    val description:       String? = null,
    val staffId:           Int?    = null,
    val staffName:         String? = null
)

// ─── Reports ─────────────────────────────────────────────────────────────────

data class JournalDetailDTO(
    val accountId:   Int?    = null,
    val accountName: String? = null,
    val debit:       Double? = null,
    val credit:      Double? = null
)

data class JournalEntryDTO(
    val id:          Int?    = null,
    val entryDate:   String? = null,
    val referenceNo: String? = null,
    val description: String? = null,
    val staffId:     Int?    = null,
    val staffName:   String? = null,
    val details:     List<JournalDetailDTO>? = null
)

data class SalesRankingDTO(
    val staffId: Int? = null,
    val staffName: String? = null,
    val salesCount: Int? = null,
    val totalAmount: Long? = null,
    val rank: Int? = null
)

// ─── Audit Log ───────────────────────────────────────────────────────────────

data class AuditLogDTO(
    val id: Long? = null,
    val actor: String? = null,
    val actorRole: String? = null,
    val action: String? = null,
    val module: String? = null,
    val resourceId: String? = null,
    val description: String? = null,
    val ipAddress: String? = null,
    val deviceType: String? = null,
    val createdAt: String? = null
)

// ─── Payment Transfer ────────────────────────────────────────────────────────

data class AccountTransferRequest(
    val fromPaymentMethodId: Int,
    val toPaymentMethodId:   Int,
    val amount:              Double,
    val staffId:             Int?    = null,
    val transactionNo:       String? = null,
    val description:         String? = null
)

// ─── Account Balances ────────────────────────────────────────────────────────

data class AccountBalanceDTO(
    val id:             Int?    = null,
    val accountId:      Int?    = null,
    val accountName:    String? = null,
    val accountType:    String? = null,
    val accountCode:    String? = null,
    val fiscalYear:     String? = null,
    val openingBalance: Double? = null,
    val currentBalance: Double? = null,
    val lastUpdated:    String? = null
)

// ─── Stock Adjustments ───────────────────────────────────────────────────────

data class StockAdjItemDTO(
    val id:          Int?    = null,
    val productId:   Int?    = null,
    val productName: String? = null,
    val productCode: String? = null,
    val qty:         Int?    = null,
    val type:        String? = null,   // "GAIN" | "LOSS"
    val remark:      String? = null
)

data class StockAdjustmentDTO(
    val id:        Int?                   = null,
    val adjCode:   String?                = null,
    val adjDate:   String?                = null,
    val reason:    String?                = null,
    val staffId:   Int?                   = null,
    val staffName: String?                = null,
    val items:     List<StockAdjItemDTO>? = null
)

// ─── Print ───────────────────────────────────────────────────────────────────

data class PrintPreviewRequest(
    val documentType: String,
    val documentId:   Int,
    val paperSize:    String = "A4"
)

// ─── Chat ────────────────────────────────────────────────────────────────────

data class ChatMessageDTO(
    val id: Long? = null,
    val senderUsername: String = "",
    val senderName: String? = null,
    val senderRole: String? = null,
    val content: String = "",
    val sentAt: String? = null
)

data class SendMessageRequest(val content: String)

// ─── Real-time data events ────────────────────────────────────────────────────

/** Emitted by the backend on /topic/data-events after every mutating service call. */
data class DataEvent(
    val entity:     String  = "",
    val action:     String  = "",
    val resourceId: String? = null,
)

// ─── Users (RBAC) ────────────────────────────────────────────────────────────

data class UserDTO(
    val id: Long = 0,
    val username: String = "",
    val email: String? = null,
    val name: String? = null,
    val phone: String? = null,
    val isActive: Boolean = true,
    val roles: List<String> = emptyList()
)

// ─── Income & Profit Report ───────────────────────────────────────────────────

data class PeriodSummaryDTO(
    val saleCount:            Long?   = null,
    val saleRevenue:          Double? = null,
    val saleReturnAmount:     Double? = null,
    val netSaleRevenue:       Double? = null,
    val saleProfit:           Double? = null,
    val serviceRevenue:       Double? = null,
    val otherIncome:          Double? = null,
    val totalIncome:          Double? = null,
    val purchaseAmount:       Double? = null,
    val purchaseReturnAmount: Double? = null,
    val netPurchaseCost:      Double? = null,
    val stockAdjLoss:         Double? = null,
    val totalExpenses:        Double? = null,
    val grossProfit:          Double? = null,
    val netProfit:            Double? = null
)

data class MonthlyDataDTO(
    val month:                Int?    = null,
    val label:                String? = null,
    val saleCount:            Long?   = null,
    val saleRevenue:          Double? = null,
    val saleReturnAmount:     Double? = null,
    val netSaleRevenue:       Double? = null,
    val saleProfit:           Double? = null,
    val serviceRevenue:       Double? = null,
    val otherIncome:          Double? = null,
    val totalIncome:          Double? = null,
    val purchaseAmount:       Double? = null,
    val purchaseReturnAmount: Double? = null,
    val netPurchaseCost:      Double? = null,
    val stockAdjLoss:         Double? = null,
    val totalExpenses:        Double? = null,
    val grossProfit:          Double? = null,
    val netProfit:            Double? = null
)

data class YearlySummaryDTO(
    val year:                       Int?                  = null,
    val months:                     List<MonthlyDataDTO>? = null,
    val totalSaleRevenue:           Double?               = null,
    val totalSaleReturnAmount:      Double?               = null,
    val totalNetSaleRevenue:        Double?               = null,
    val totalServiceRevenue:        Double?               = null,
    val totalOtherIncome:           Double?               = null,
    val totalIncome:                Double?               = null,
    val totalPurchaseAmount:        Double?               = null,
    val totalPurchaseReturnAmount:  Double?               = null,
    val totalNetPurchaseCost:       Double?               = null,
    val totalStockAdjLoss:          Double?               = null,
    val totalExpenses:              Double?               = null,
    val totalNetProfit:             Double?               = null
)

data class ProfitLossLineItemDTO(
    val accountCode: String? = null,
    val accountName: String? = null,
    val amount:      Double? = null
)

data class ProfitLossReportDTO(
    val from:             String?                    = null,
    val to:               String?                    = null,
    val grossSales:       Double?                    = null,
    val salesReturns:     Double?                    = null,
    val netRevenue:       Double?                    = null,
    val purchases:        Double?                    = null,
    val purchaseReturns:  Double?                    = null,
    val netPurchases:     Double?                    = null,
    val grossProfit:      Double?                    = null,
    val otherIncomeItems: List<ProfitLossLineItemDTO>? = null,
    val totalOtherIncome: Double?                    = null,
    val expenseItems:     List<ProfitLossLineItemDTO>? = null,
    val totalExpenses:    Double?                    = null,
    val netProfit:        Double?                    = null
)
