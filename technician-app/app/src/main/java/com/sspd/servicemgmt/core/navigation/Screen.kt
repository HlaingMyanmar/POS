package com.sspd.servicemgmt.core.navigation

import android.net.Uri

sealed class Screen(val route: String) {
    object Login        : Screen("login")
    object Home         : Screen("home")
    object Sales        : Screen("sales")
    object Products     : Screen("products")
    object InventorySetup : Screen("inventory_setup")
    object Purchases    : Screen("purchases")
    object Customers    : Screen("customers")
    object CustomerHistory : Screen("customer_history")
    object CreditDesk   : Screen("credit_desk")
    object ServiceJobs  : Screen("service_jobs")
    object Bookings     : Screen("bookings")
    object StaffReport  : Screen("staff_report")
    object Expenses     : Screen("expenses")
    object SalesRanking : Screen("sales_ranking")
    object AuditLog     : Screen("audit_log")
    object JournalEntries : Screen("journal_entries")
    object Chat         : Screen("chat")
    object Account      : Screen("account")
    object About          : Screen("about")
    object SoftwareUpdate : Screen("software_update")
    object Videos         : Screen("videos")
    object VideoPlayer    : Screen("video_player/{youtubeId}?title={title}&description={description}&category={category}") {
        fun createRoute(
            youtubeId: String,
            title: String? = null,
            description: String? = null,
            category: String? = null
        ): String {
            val id = Uri.encode(youtubeId)
            val t = Uri.encode(title.orEmpty().take(200))
            val d = Uri.encode(description.orEmpty().take(800))
            val c = Uri.encode(category.orEmpty().take(80))
            return "video_player/$id?title=$t&description=$d&category=$c"
        }
    }
    object ProductDetail  : Screen("product_detail/{productId}?serial={serialNumber}") {
        fun createRoute(id: Int, serial: String? = null) =
            if (serial != null) "product_detail/$id?serial=${Uri.encode(serial)}"
            else "product_detail/$id"
    }
    object NewProduct     : Screen("new_product")
    object EditProduct    : Screen("edit_product/{productId}") {
        fun createRoute(id: Int) = "edit_product/$id"
    }
    object SaleDetail     : Screen("sale_detail/{saleId}") {
        fun createRoute(id: Int) = "sale_detail/$id"
    }
    object NewSale         : Screen("new_sale")
    object PurchaseDetail  : Screen("purchase_detail/{purchaseId}") {
        fun createRoute(id: Int) = "purchase_detail/$id"
    }
    object NewPurchase     : Screen("new_purchase")
    object PurchaseOrders  : Screen("purchase_orders")
    object SupplierPayments : Screen("supplier_payments")
    object PurchaseReturns  : Screen("purchase_returns")
    object PurchaseReturnDetail : Screen("purchase_return_detail/{returnId}") {
        fun createRoute(id: Int) = "purchase_return_detail/$id"
    }
    object NewPurchaseReturn : Screen("new_purchase_return")
    object SalePrint       : Screen("sale_print/{saleId}") {
        fun createRoute(id: Int) = "sale_print/$id"
    }
    object ServiceMgmt     : Screen("service_mgmt")
    object ShelfLocations  : Screen("shelf_locations")
    object ServiceJobDetail : Screen("service_job_detail/{jobId}") {
        fun createRoute(id: Int) = "service_job_detail/$id"
    }
    object BookingDetail   : Screen("booking_detail/{bookingId}") {
        fun createRoute(id: Int) = "booking_detail/$id"
    }
    object BookingPrint    : Screen("booking_print/{bookingId}") {
        fun createRoute(id: Int) = "booking_print/$id"
    }
    object NewBooking      : Screen("new_booking")
    object EditBooking     : Screen("edit_booking/{bookingId}") {
        fun createRoute(id: Int) = "edit_booking/$id"
    }
    object SaleReturns     : Screen("sale_returns")
    object SaleReturnDetail : Screen("sale_return_detail/{returnId}") {
        fun createRoute(id: Int) = "sale_return_detail/$id"
    }
    object NewSaleReturn   : Screen("new_sale_return")
    object EditSaleReturn  : Screen("edit_sale_return/{returnId}") {
        fun createRoute(id: Int) = "edit_sale_return/$id"
    }
    object NewExpenseEntry : Screen("new_expense_entry/{type}") {
        fun createRoute(type: String) = "new_expense_entry/$type"
    }
    object Report          : Screen("report")
    object IncomeReport    : Screen("income_report")
    object EditServiceJob  : Screen("edit_service_job/{jobId}") {
        fun createRoute(id: Int) = "edit_service_job/$id"
    }
    object ServiceJobPrint : Screen("service_job_print/{jobId}") {
        fun createRoute(id: Int) = "service_job_print/$id"
    }
    object StockAdjustments : Screen("stock_adjustments")
    object StockAdjDetail   : Screen("stock_adj_detail/{adjId}") {
        fun createRoute(id: Int) = "stock_adj_detail/$id"
    }
    object NewStockAdj      : Screen("new_stock_adj")
    object SerialRegistry   : Screen("serial_registry")
    object OpeningBalance   : Screen("opening_balance")
    object Transfer         : Screen("transfer")
    object WebModule        : Screen("web_module/{title}/{endpoint}") {
        fun createRoute(title: String, endpoint: String) = "web_module/${Uri.encode(title)}/${Uri.encode(endpoint)}"
    }
}

const val AUTH_GRAPH = "auth_graph"
const val MAIN_GRAPH = "main_graph"
