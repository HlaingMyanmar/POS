package com.sspd.servicemgmt.feature.home

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sspd.servicemgmt.core.database.*
import com.sspd.servicemgmt.core.network.*
import com.sspd.servicemgmt.core.tracking.LocationClient
import com.sspd.servicemgmt.core.tracking.LocationPermission
import com.sspd.servicemgmt.core.ui.component.ErrorRetryBanner
import com.sspd.servicemgmt.core.ui.component.OrderSkeletonList
import com.sspd.servicemgmt.core.ui.component.SaleInvoiceViewerDialog
import com.sspd.servicemgmt.core.ui.component.UpdateDialog
import com.sspd.servicemgmt.feature.settings.VersionCheckViewModel
import com.sspd.servicemgmt.core.ui.theme.CardBg
import com.sspd.servicemgmt.core.ui.theme.Danger
import com.sspd.servicemgmt.core.ui.theme.OnPrimary
import com.sspd.servicemgmt.core.ui.theme.Primary
import com.sspd.servicemgmt.core.ui.theme.PrimaryDark
import com.sspd.servicemgmt.core.ui.theme.PrimaryLight
import com.sspd.servicemgmt.core.ui.theme.ScreenBg
import com.sspd.servicemgmt.core.ui.theme.Success
import com.sspd.servicemgmt.core.ui.theme.SuccessBg
import com.sspd.servicemgmt.core.ui.theme.SurfaceSoft
import com.sspd.servicemgmt.core.ui.theme.TextMain
import com.sspd.servicemgmt.core.ui.theme.TextMuted
import kotlin.math.abs
import com.sspd.servicemgmt.core.ui.theme.BorderColor
import com.sspd.servicemgmt.core.util.PreferenceManager
import com.sspd.servicemgmt.core.util.PreferenceManager.Companion.IDLE_TIMEOUT_MINUTES
import com.sspd.servicemgmt.core.util.SaleInvoiceOpener
import com.sspd.servicemgmt.core.feature.CustomerAppFeatures
import com.sspd.servicemgmt.feature.cart.CartIssue
import com.sspd.servicemgmt.feature.cart.CartStore
import com.sspd.servicemgmt.feature.profile.CustomerProfileScreen
import android.app.Application
import android.widget.Toast
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import retrofit2.Response

private fun responseMessage(response: Response<*>, fallback: String): String {
    response.body()?.let { body ->
        runCatching {
            val method = body.javaClass.methods.firstOrNull { it.name == "getMessage" && it.parameterCount == 0 }
            val value = method?.invoke(body)?.toString()?.trim()
            if (!value.isNullOrBlank()) return value
        }
    }
    val raw = runCatching { response.errorBody()?.string().orEmpty() }.getOrDefault("")
    if (raw.isNotBlank()) {
        runCatching { JSONObject(raw).optString("message").trim() }
            .getOrNull()?.takeIf { it.isNotBlank() }?.let { return it }
    }
    return fallback
}
class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = PreferenceManager(app)
    var products by mutableStateOf<List<CatalogProduct>>(emptyList()); private set
    var categories by mutableStateOf<List<CatalogOption>>(emptyList()); private set
    var brands by mutableStateOf<List<CatalogOption>>(emptyList()); private set
    var services by mutableStateOf<List<CatalogService>>(emptyList()); private set
    var bookings by mutableStateOf<List<BookingSummary>>(emptyList()); private set
    var orders by mutableStateOf<List<CustomerOrder>>(emptyList()); private set
    var purchases by mutableStateOf<List<CustomerPurchase>>(emptyList()); private set
    var jobs by mutableStateOf<List<CustomerJob>>(emptyList()); private set
    var ordersLoading by mutableStateOf(false); private set
    var ordersError by mutableStateOf<String?>(null); private set
    var purchasesError by mutableStateOf<String?>(null); private set
    var jobsError by mutableStateOf<String?>(null); private set
    var bookingsError by mutableStateOf<String?>(null); private set
    var notifications by mutableStateOf<List<CustomerNotification>>(emptyList()); private set
    var unreadNotificationCount by mutableStateOf(0); private set
    var loyaltyPoints by mutableStateOf<LoyaltyPoints?>(null); private set
    var biometricEnabled by mutableStateOf(prefs.biometricEnabled); private set
    var wishlist by mutableStateOf<List<CatalogProduct>>(emptyList()); private set
    var wishlistIds by mutableStateOf<Set<Int>>(emptySet()); private set
    var wishlistLoading by mutableStateOf(false); private set
    var profile by mutableStateOf(
        CustomerAuthResponse(
            name = prefs.displayName,
            phone = prefs.phone,
            address = prefs.address,
            email = prefs.email,
            customerId = prefs.customerId.takeIf { it > 0 }
        )
    ); private set
    var profileLoading by mutableStateOf(false); private set
    var profileSaving by mutableStateOf(false); private set
    var passwordSaving by mutableStateOf(false); private set
    var loading by mutableStateOf(false); private set
    var refreshing by mutableStateOf(false); private set
    var message by mutableStateOf<String?>(null)
    var cartIssues by mutableStateOf<List<CartIssue>>(emptyList()); private set
    var checkoutPlacing by mutableStateOf(false); private set
    var serviceRequestSubmitting by mutableStateOf(false); private set
    var openHistoryTab by mutableStateOf(false)
    var openBookingsTab by mutableStateOf(false)
    private var pendingCheckout: PendingCheckout? = null
    private var lastCheckoutKey: String? = null
    private var lastCheckoutStamp: String? = null

    private data class PendingCheckout(
        val note: String,
        val orderType: String,
        val deliveryLocationMode: String?,
        val deliveryAddress: String?,
        val deliveryPhone: String?,
        val townshipId: Int?,
        val wardId: Int?,
        val paymentChoice: String?,
        val requestedDeliveryAt: String?,
        val idempotencyKey: String,
        val promoCode: String?
    )

    private val db by lazy { AppDatabase.getDatabase(getApplication()) }

    init {
        CartStore.init(getApplication())
        viewModelScope.launch {
            bindCartAccount()
            runCatching { refreshCartFromCatalog() }
        }
    }

    var catalogLoading by mutableStateOf(false); private set
    var catalogError by mutableStateOf<String?>(null); private set
    var catalogHasNext by mutableStateOf(false); private set
    var catalogPage by mutableIntStateOf(0); private set
    var catalogTotal by mutableLongStateOf(0); private set
    private data class CatalogFilter(val q: String = "", val category: Int? = null, val brand: Int? = null,
                                     val type: String? = null, val sort: String = "name")
    private var catalogFilter = CatalogFilter()
    private var catalogJob: kotlinx.coroutines.Job? = null
    private var catalogGeneration = 0L

    fun searchCatalog(q: String, category: Int?, brand: Int?, type: String?, sort: String) {
        val next = CatalogFilter(q.take(120).trim(), category, brand, type, sort)
        if (next == catalogFilter && (catalogLoading || products.isNotEmpty())) return
        catalogFilter = next
        requestCatalogPage(0, debounce = true)
    }

    fun requestCatalogPage(page: Int = catalogPage, debounce: Boolean = false) {
        if (page < 0) return
        val generation = ++catalogGeneration
        catalogJob?.cancel()
        val filter = catalogFilter
        catalogLoading = true
        catalogError = null
        catalogPage = page
        catalogJob = viewModelScope.launch {
            try {
                if (debounce) delay(300)
                val response = ApiClient.service.catalogPage(page, 24, filter.q.ifBlank { null },
                    filter.category, filter.brand, filter.type, filter.sort)
                val body = response.body()
                val data = body?.data
                if (!response.isSuccessful || body?.success != true || data == null) {
                    // Attempt to load from cache if API fails
                    val cached = db.appDao().getAllProducts()
                    if (cached.isNotEmpty()) {
                        products = cached.map { CatalogProduct(id = it.id, name = it.name, productCode = it.productCode, sellingPrice = it.sellingPrice, thumbnailUrl = it.thumbnailUrl, categoryName = it.categoryName) }
                        catalogError = "Offline Mode: Showing cached products"
                    } else {
                        throw IllegalStateException(httpErrorMessage(response.code(), body?.message))
                    }
                } else {
                    if (generation != catalogGeneration) return@launch
                    products = if (page == 0) data.content else (products + data.content).distinctBy { it.id }
                    catalogPage = data.page
                    catalogTotal = data.totalElements
                    catalogHasNext = data.hasNext
                    // Cache the first page of products
                    if (page == 0) {
                        db.appDao().clearProducts()
                        db.appDao().insertProducts(data.content.map { ProductEntity(it.id, it.name, it.productCode, it.sellingPrice, it.thumbnailUrl, it.categoryName) })
                    }
                    catalogError = null
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation == catalogGeneration) {
                    // Fallback to cache on exception
                    val cached = db.appDao().getAllProducts()
                    if (cached.isNotEmpty()) {
                        products = cached.map { CatalogProduct(id = it.id, name = it.name, productCode = it.productCode, sellingPrice = it.sellingPrice, thumbnailUrl = it.thumbnailUrl, categoryName = it.categoryName) }
                        catalogError = "Offline Mode: Showing cached products"
                    } else {
                        catalogError = if (e is IllegalStateException) e.message ?: "Catalog ဖတ်မရပါ"
                        else networkErrorMessage(e)
                    }
                }
            } finally {
                if (generation == catalogGeneration) catalogLoading = false
            }
        }
    }

    private var chatSocket: CustomerChatSocket? = null
    var chatMessages by mutableStateOf<List<ChatMessage>>(emptyList()); private set
    private var orderSocket: CustomerOrderSocket? = null
    var orderSocketConnected by mutableStateOf(false); private set
    private var orderRefreshRunning = false
    private var orderRefreshAgain = false

    fun startOrderUpdates() {
        if (orderSocket != null) return
        orderSocket = CustomerOrderSocket(
            prefs,
            onUpdate = { notification -> receiveOrderNotification(notification) },
            onConnected = { orderSocketConnected = true; refreshOrderUpdates() },
            onDisconnected = { orderSocketConnected = false },
            onChatMessage = { msg ->
                chatMessages = (chatMessages.filter { it.id != msg.id } + msg).sortedBy { it.createdAt }
            }
        ).also { it.start() }
    }

    fun startChat() {
        if (chatSocket != null) return
        chatSocket = CustomerChatSocket(
            prefs,
            { msg ->
                chatMessages = (chatMessages.filter { it.id != msg.id } + msg).sortedBy { it.createdAt }
            },
            { loadChatHistory() },
            { /* Disconnected — socket retries automatically */ }
        ).also { it.start() }
        loadChatHistory()
    }

    fun stopChat() {
        chatSocket?.stop()
        chatSocket = null
    }

    fun sendMessage(text: String) {
        viewModelScope.launch {
            try {
                val response = ApiClient.service.sendChatMessage(auth(), ChatRequest(text))
                if (response.isSuccessful) {
                    response.body()?.data?.let { sent ->
                        chatMessages = (chatMessages.filter { it.id != sent.id } + sent).sortedBy { it.createdAt }
                    }
                } else {
                    message = "စာပို့၍မရပါ"
                }
            } catch (e: Exception) {
                message = e.message ?: "Network error"
            }
        }
    }

    fun loadChatHistory() {
        viewModelScope.launch {
            try {
                val response = ApiClient.service.chatHistory(auth())
                if (response.isSuccessful) {
                    chatMessages = response.body()?.data ?: emptyList()
                }
            } catch (e: Exception) {
                // Quietly fail or log
            }
        }
    }

    fun stopOrderUpdates() {
        orderSocket?.stop()
        orderSocket = null
        orderSocketConnected = false
    }

    fun refreshOrderUpdates() {
        if (orderRefreshRunning) { orderRefreshAgain = true; return }
        orderRefreshRunning = true
        viewModelScope.launch {
            try {
                do {
                    orderRefreshAgain = false
                    val response = ApiClient.service.myNotifications(auth())
                    if (response.isSuccessful) {
                        notifications = response.body()?.data ?: emptyList()
                        recomputeUnread()
                        // This response is notification history. Keep it for the inbox/badge only;
                        // replaying it through receiveOrderNotification() makes old alerts appear
                        // again whenever the socket connects or the app resumes.
                    }
                    val orderResponse = ApiClient.service.myOrders(auth())
                    if (orderResponse.isSuccessful) orders = orderResponse.body()?.data ?: emptyList()
                } while (orderRefreshAgain)
            } catch (_: Exception) {
                // Reconnect and foreground resume both retry synchronization.
            } finally { orderRefreshRunning = false }
        }
    }

    private fun receiveOrderNotification(notification: CustomerNotification) {
        val app = getApplication<Application>()
        val subject = runCatching {
            val payload = android.util.Base64.decode(prefs.authToken.split('.')[1], android.util.Base64.URL_SAFE)
            org.json.JSONObject(String(payload, Charsets.UTF_8)).optString("sub").ifBlank { "customer" }
        }.getOrNull() ?: "customer"
        val seen = app.getSharedPreferences("order_alerts", android.content.Context.MODE_PRIVATE)
        val key = prefs.serverUrl + ":" + subject + ":" + notification.id
        val version = (notification.status ?: "") + ":" + (notification.notifiedAt ?: "")
        notifications = (listOf(notification) + notifications.filter { it.id != notification.id })
            .sortedByDescending { it.notifiedAt }
        recomputeUnread()
        if (seen.getString(key, null) == version) return
        seen.edit().putString(key, version).apply()
        message = notification.note
        CustomerOrderAlerts.show(app, notification)
    }

    fun markNotificationsRead() {
        val prefsStore = getApplication<Application>()
            .getSharedPreferences("notif_read", android.content.Context.MODE_PRIVATE)
        val read = prefsStore.getStringSet("ids", emptySet())?.toMutableSet() ?: mutableSetOf()
        notifications.forEach { read.add(notificationReadKey(it)) }
        prefsStore.edit().putStringSet("ids", read).apply()
        unreadNotificationCount = 0
    }

    private fun recomputeUnread() {
        val read = getApplication<Application>()
            .getSharedPreferences("notif_read", android.content.Context.MODE_PRIVATE)
            .getStringSet("ids", emptySet()) ?: emptySet()
        unreadNotificationCount = notifications.count { notificationReadKey(it) !in read }
    }

    private fun notificationReadKey(notification: CustomerNotification): String =
        "${notification.id}:${notification.status.orEmpty()}:${notification.notifiedAt.orEmpty()}"

    override fun onCleared() { 
        stopOrderUpdates() 
        stopChat()
        super.onCleared() 
    }


    fun auth() = ApiClient.bearer(prefs.authToken)
    suspend fun b2ProductVideoUrl(productId: Int, videoId: Long): String {
        val response = ApiClient.service.b2ProductVideoPlayback(auth(), productId, videoId)
        val url = response.body()?.data?.url
        if (!response.isSuccessful || url.isNullOrBlank()) {
            throw IllegalStateException(response.body()?.message ?: "Video ဖွင့်မရပါ")
        }
        return url
    }

    @JvmName("updateBiometricEnabled")
    fun setBiometricEnabled(enabled: Boolean) {
        prefs.biometricEnabled = enabled
        biometricEnabled = enabled
    }
    fun name() = prefs.displayName
    fun phone() = prefs.phone
    fun logout() {
        stopOrderUpdates()
        stopChat()
        chatMessages = emptyList()
        getApplication<Application>()
            .getSharedPreferences("notif_read", android.content.Context.MODE_PRIVATE)
            .edit().clear().apply()
        unreadNotificationCount = 0
        notifications = emptyList()
        message = null
        prefs.clearSession()
        CartStore.detach()
        // Drop in-memory profile so a later session cannot flash old data
        profile = CustomerAuthResponse()
        orders = emptyList()
        purchases = emptyList()
        jobs = emptyList()
        bookings = emptyList()
        wishlist = emptyList()
        wishlistIds = emptySet()
    }

    fun loadProfile() {
        viewModelScope.launch {
            profileLoading = true
            try {
                fetchProfile()
                fetchLoyaltyPoints()
            } catch (e: Exception) {
                message = networkErrorMessage(e)
            } finally {
                profileLoading = false
            }
        }
    }
  
    private suspend fun fetchLoyaltyPoints() {
        val res = ApiClient.service.myLoyaltyPoints(auth())
        if (res.isSuccessful) {
            loyaltyPoints = res.body()?.data
        }
    }
  
    private suspend fun fetchProfile() {
        val sessionToken = prefs.authToken
        if (sessionToken.isBlank()) return
        val response = ApiClient.service.me(ApiClient.bearer(sessionToken))
        val data = response.body()?.data
        if (response.isSuccessful && data != null) syncProfile(data, sessionToken)
    }

    fun saveProfile(name: String, phone: String, address: String) {
        if (phone.trim().length < 6) {
            message = "ဖုန်းနံပါတ် မှန်ကန်စွာ ထည့်ပါ"
            return
        }
        if (address.isBlank()) {
            message = "လိပ်စာ ထည့်ပါ"
            return
        }
        viewModelScope.launch {
            profileSaving = true
            val sessionToken = prefs.authToken
            if (sessionToken.isBlank()) { profileSaving = false; return@launch }
            try {
                val response = ApiClient.service.updateProfile(
                    ApiClient.bearer(sessionToken),
                    ProfileUpdateRequest(name.trim().ifBlank { null }, phone.trim(), address.trim())
                )
                val body = response.body()
                val data = body?.data
                if (response.isSuccessful && body?.success == true && data != null) {
                    syncProfile(data, sessionToken)
                    message = "Profile ပြင်ဆင်ပြီးပါပြီ"
                } else {
                    message = body?.message ?: "Profile သိမ်းမရပါ"
                }
            } catch (e: Exception) {
                message = e.message ?: "Profile သိမ်းမရပါ"
            } finally {
                profileSaving = false
            }
        }
    }

    fun changePassword(currentPassword: String, newPassword: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            passwordSaving = true
            val sessionToken = prefs.authToken
            if (sessionToken.isBlank()) { passwordSaving = false; return@launch }
            try {
                val response = ApiClient.service.changePassword(
                    ApiClient.bearer(sessionToken),
                    ChangePasswordRequest(currentPassword, newPassword)
                )
                val body = response.body()
                val data = body?.data
                if (response.isSuccessful && body?.success == true && data != null) {
                    syncProfile(data, sessionToken)
                    message = "စကားဝှက် ပြောင်းပြီးပါပြီ"
                    onSuccess()
                } else {
                    val raw = response.errorBody()?.string().orEmpty()
                    message = runCatching { org.json.JSONObject(raw).optString("message") }
                        .getOrNull()
                        ?.takeIf { it.isNotBlank() }
                        ?: body?.message
                        ?: "စကားဝှက် ပြောင်းမရပါ"
                }
            } catch (e: Exception) {
                message = e.message ?: "စကားဝှက် ပြောင်းမရပါ"
            } finally {
                passwordSaving = false
            }
        }
    }

    private fun syncProfile(data: CustomerAuthResponse, sessionToken: String) {
        if (sessionToken.isBlank() || prefs.authToken != sessionToken) return
        profile = data
        data.accessToken?.takeIf { it.isNotBlank() }?.let { prefs.authToken = it }
        prefs.displayName = data.name.orEmpty()
        prefs.phone = data.phone.orEmpty()
        prefs.address = data.address.orEmpty()
        prefs.email = data.email.orEmpty()
        prefs.needsProfile = data.needsProfile == true
        data.customerId?.takeIf { it > 0 }?.let { prefs.customerId = it }
    }

    fun loadCatalog() {
        viewModelScope.launch {
            loading = true
            try {
                fetchCatalog()
            } finally {
                loading = false
            }
        }
    }

    private suspend fun fetchCatalog() {
        requestCatalogPage(0)
        runCatching {
            val res = ApiClient.service.catalogCategories()
            if (res.isSuccessful) categories = res.body()?.data ?: emptyList()
        }
        runCatching {
            val res = ApiClient.service.catalogBrands()
            if (res.isSuccessful) brands = res.body()?.data ?: emptyList()
        }
        runCatching {
            val res = ApiClient.service.catalogServices()
            if (res.isSuccessful) services = res.body()?.data ?: emptyList()
        }
    }

    fun loadMine() {
        viewModelScope.launch { fetchMine() }
    }

    fun retryOrders() { viewModelScope.launch { refreshOrders() } }
    fun retryPurchases() { viewModelScope.launch { refreshPurchases() } }
    fun retryJobs() { viewModelScope.launch { refreshJobs() } }
    fun retryBookings() { viewModelScope.launch { refreshBookings() } }

    private suspend fun fetchMine() {
        coroutineScope {
            launch { refreshOrders() }
            launch { refreshPurchases() }
            launch { refreshJobs() }
            launch { refreshBookings() }
            launch { refreshNotifications() }
            launch { refreshWishlist() }
        }
    }

    private suspend fun refreshOrders() {
        val showSkeleton = orders.isEmpty()
        if (showSkeleton) ordersLoading = true
        try {
            try {
                val response = ApiClient.service.myOrders(auth())
                val body = response.body()
                if (response.isSuccessful && body?.success == true) {
                    val fresh = body.data ?: emptyList()
                    orders = fresh
                    ordersError = null
                    db.appDao().clearOrders()
                    db.appDao().insertOrders(fresh.map { OrderEntity(it.id, it.orderNo, it.status, it.total, it.createdAt) })
                } else throw IllegalStateException(httpErrorMessage(response.code(), body?.message))
            } catch (e: Exception) {
                val cached = db.appDao().getAllOrders()
                if (cached.isNotEmpty()) {
                    orders = cached.map { CustomerOrder(id = it.id, orderNo = it.orderNo, status = it.status,
                        total = it.total, createdAt = it.createdAt) }
                    ordersError = "Offline Mode: Showing cached orders"
                } else {
                    ordersError = if (e is IllegalStateException) e.message else networkErrorMessage(e)
                }
            }
        } finally {
            if (showSkeleton) ordersLoading = false
        }
    }

    private suspend fun refreshPurchases() {
        refreshKeep(ordersError = { purchasesError = it }, apply = { purchases = it }) {
            ApiClient.service.myPurchases(auth())
        }
    }

    private suspend fun refreshJobs() {
        refreshKeep(ordersError = { jobsError = it }, apply = { jobs = it }) {
            ApiClient.service.myJobs(auth())
        }
    }

    private suspend fun refreshBookings() {
        refreshKeep(ordersError = { bookingsError = it }, apply = { bookings = it }) {
            ApiClient.service.myBookings(auth())
        }
    }

    private suspend fun refreshNotifications() {
        try {
            val response = ApiClient.service.myNotifications(auth())
            if (response.isSuccessful) {
                notifications = response.body()?.data ?: emptyList()
                recomputeUnread()
            }
        } catch (_: Exception) {
            /* keep previously loaded notifications */
        }
    }

    private suspend fun <T> refreshKeep(
        ordersError: (String?) -> Unit,
        apply: (List<T>) -> Unit,
        call: suspend () -> Response<ApiResponse<List<T>>>
    ) {
        try {
            val response = call()
            val body = response.body()
            if (response.isSuccessful && (body == null || body.success)) {
                apply(body?.data ?: emptyList())
                ordersError(null)
            } else {
                ordersError(httpErrorMessage(response.code(), body?.message))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ordersError(networkErrorMessage(e))
        }
    }

    fun upsertOrder(order: CustomerOrder) {
        orders = (listOf(order) + orders.filter { it.id != order.id }).sortedByDescending { it.id }
    }

    /** Put cancelled-order lines back into the cart so the customer can place a new order. */
    fun reorderCancelled(order: CustomerOrder) {
        viewModelScope.launch {
            val live = LinkedHashMap<Int, CatalogProduct>()
            products.forEach { live[it.id] = it }
            val lineIds = order.lines.orEmpty().mapNotNull { it.productId }
            if (lineIds.any { it !in live }) {
                runCatching { ApiClient.service.catalogProducts() }
                    .getOrNull()?.body()?.data.orEmpty()
                    .forEach { live[it.id] = it }
            }
            var restored = 0
            for (line in order.lines.orEmpty()) {
                val id = line.productId ?: continue
                val qty = (line.qty ?: 0).coerceAtLeast(1)
                val catalog = live[id] ?: continue
                CartStore.setQty(catalog, qty)
                restored++
            }
            if (restored == 0) {
                message = "ပြန်မှာယူရန် ပစ္စည်း စတော့မရှိတော့ပါ။ ပစ္စည်းစာရင်းမှ ပြန်ရွေးပါ။"
                return@launch
            }
            val issues = CartStore.reconcile(live)
            cartIssues = issues
            if (issues.isEmpty()) {
                message = "ခြင်းတောင်းထဲ ထည့်ပြီးပါပြီ။ အော်ဒါပြန်တင်ပါ။"
            }
        }
    }

    /** Pull-to-refresh for home tabs. */
    fun pullToRefresh(
        catalog: Boolean = false,
        mine: Boolean = false,
        profile: Boolean = false,
        wishlist: Boolean = false
    ) {
        if (refreshing) return
        viewModelScope.launch {
            refreshing = true
            try {
                if (catalog) runCatching { fetchCatalog() }
                if (mine) runCatching { fetchMine() }
                if (profile) runCatching { fetchProfile() }
                if (wishlist) runCatching { refreshWishlist() }
            } finally {
                refreshing = false
            }
        }
    }

    fun toggleWishlist(product: CatalogProduct) {
        if (!CustomerAppFeatures.WISHLIST) return
        viewModelScope.launch {
            try {
                val res = ApiClient.service.toggleWishlist(auth(), WishlistRequest(product.id))
                val status = res.body()?.data
                if (res.isSuccessful && res.body()?.success == true && status != null) {
                    applyWishlistStatus(product, status.wishlisted)
                    message = if (status.wishlisted) "${product.name.orEmpty()} အကြိုက်စာရင်းထဲ ထည့်ပြီးပါပြီ"
                    else "${product.name.orEmpty()} အကြိုက်စာရင်းမှ ဖယ်ပြီးပါပြီ"
                } else {
                    message = res.body()?.message ?: "အကြိုက်စာရင်း မပြင်နိုင်ပါ"
                }
            } catch (e: Exception) {
                message = networkErrorMessage(e)
            }
        }
    }

    private fun applyWishlistStatus(product: CatalogProduct, wishlisted: Boolean) {
        if (wishlisted) {
            wishlistIds = wishlistIds + product.id
            if (wishlist.none { it.id == product.id }) wishlist = listOf(product) + wishlist
        } else {
            wishlistIds = wishlistIds - product.id
            wishlist = wishlist.filter { it.id != product.id }
        }
    }

    private suspend fun refreshWishlist() {
        if (!CustomerAppFeatures.WISHLIST) return
        wishlistLoading = true
        try {
            val res = ApiClient.service.myWishlist(auth())
            if (res.isSuccessful && res.body()?.success == true) {
                wishlist = res.body()?.data.orEmpty()
                wishlistIds = wishlist.map { it.id }.toSet()
            }
        } finally {
            wishlistLoading = false
        }
    }

    fun requestService(request: ServiceRequestBody) {
        if (serviceRequestSubmitting) return
        viewModelScope.launch {
            serviceRequestSubmitting = true
            try {
                val res = ApiClient.service.requestService(auth(), request)
                if (res.isSuccessful && res.body()?.success == true) {
                    res.body()?.data?.let { created ->
                        bookings = listOf(created) + bookings.filterNot { it.id == created.id }
                    }
                    openBookingsTab = true
                    loadMine()
                } else {
                    message = res.body()?.message ?: "မအောင်မြင်ပါ"
                }
            } catch (e: Exception) {
                message = networkErrorMessage(e)
            } finally {
                serviceRequestSubmitting = false
            }
        }
    }

    private suspend fun bindCartAccount() {
        var id = prefs.customerId
        if (id <= 0) {
            val sessionToken = prefs.authToken
            if (sessionToken.isBlank()) return
            runCatching {
                val data = ApiClient.service.me(ApiClient.bearer(sessionToken)).body()?.data
                if (data != null && prefs.authToken == sessionToken) {
                    syncProfile(data, sessionToken)
                    id = data.customerId ?: 0
                }
            }
        }
        if (id > 0) CartStore.bindAccount(id)
    }

    suspend fun refreshCartFromCatalog(): List<CartIssue> {
        if (CartStore.items.value.isEmpty()) return emptyList()
        val response = ApiClient.service.catalogProducts()
        val body = response.body()
        if (!response.isSuccessful || body?.success != true) {
            throw IllegalStateException(body?.message ?: "ဈေး / စတော့ ပြန်စစ်မရပါ")
        }
        return CartStore.reconcile(body.data.orEmpty().associateBy { it.id })
    }

    fun refreshCartQuietly() {
        viewModelScope.launch {
            runCatching { refreshCartFromCatalog() }
        }
    }

    fun dismissCartIssues() {
        CartStore.acknowledgePrices()
        cartIssues = emptyList()
        pendingCheckout = null
    }

    fun continueCheckoutAfterIssues() {
        val pending = pendingCheckout
        CartStore.acknowledgePrices()
        cartIssues = emptyList()
        pendingCheckout = null
        if (pending == null || CartStore.items.value.isEmpty()) return
        checkout(
            note = pending.note,
            orderType = pending.orderType,
            deliveryLocationMode = pending.deliveryLocationMode,
            deliveryAddress = pending.deliveryAddress,
            deliveryPhone = pending.deliveryPhone,
            townshipId = pending.townshipId,
            wardId = pending.wardId,
            paymentChoice = pending.paymentChoice,
            requestedDeliveryAt = pending.requestedDeliveryAt,
            idempotencyKey = pending.idempotencyKey,
            promoCode = pending.promoCode
        )
    }

    private fun cartStamp(): String =
        CartStore.items.value.joinToString("|") { "${it.product.id}:${it.qty}" }

    fun checkout(
        note: String,
        orderType: String,
        deliveryLocationMode: String? = null,
        deliveryAddress: String? = null,
        deliveryPhone: String? = null,
        townshipId: Int? = null,
        wardId: Int? = null,
        paymentChoice: String? = "TRANSFER",
        requestedDeliveryAt: String? = null,
        idempotencyKey: String,
        promoCode: String? = null
    ) {
        viewModelScope.launch {
            if (checkoutPlacing) return@launch
            checkoutPlacing = true
            val stamp = cartStamp()
            val key = if (lastCheckoutKey != null && lastCheckoutStamp == stamp) {
                lastCheckoutKey!!
            } else {
                idempotencyKey
            }
            lastCheckoutKey = key
            lastCheckoutStamp = stamp
            try {
                val issues = try {
                    refreshCartFromCatalog()
                } catch (e: Exception) {
                    message = e.message ?: "ဈေး / စတော့ ပြန်စစ်မရပါ"
                    return@launch
                }
                if (issues.isNotEmpty()) {
                    pendingCheckout = PendingCheckout(
                        note, orderType, deliveryLocationMode, deliveryAddress, deliveryPhone,
                        townshipId, wardId, paymentChoice, requestedDeliveryAt, key, promoCode
                    )
                    cartIssues = issues
                    return@launch
                }
            val lines = CartStore.items.value.map { OrderLineRequest(it.product.id, it.qty) }
            if (lines.isEmpty()) { message = "ခြင်းတောင်း ဗလာဖြစ်နေသည်"; return@launch }
            val type = orderType.trim().uppercase()
            if (type != "DELIVERY" && type != "PICKUP") {
                message = "ပို့ဆောင်ပုံ ရွေးပါ"; return@launch
            }
            if (type == "DELIVERY") {
                if (townshipId == null || townshipId <= 0) {
                    message = "ပို့မည့် မြို့နယ် ရွေးပါ"; return@launch
                }
                val mode = deliveryLocationMode?.trim()?.uppercase()
                if (mode != "PROFILE" && mode != "OTHER") {
                    message = "ပို့မည့်နေရာ ရွေးပါ"; return@launch
                }
                if (mode == "OTHER") {
                    if (deliveryPhone.isNullOrBlank()) {
                        message = "လက်ခံမည့်သူ ဖုန်းနံပါတ် ထည့်ပါ"; return@launch
                    }
                    if (deliveryAddress.isNullOrBlank()) {
                        message = "ပို့မည့် လိပ်စာ ထည့်ပါ"; return@launch
                    }
                }
                if (mode == "PROFILE" && profile.address.isNullOrBlank()) {
                    message = "Profile လိပ်စာ မရှိသေးပါ — Profile တွင် ဖြည့်ပါ"; return@launch
                }
                val requested = requestedDeliveryAt?.trim().orEmpty()
                if (requested.length < 16) {
                    message = "ပို့မည့် ရက်နှင့် အချိန် ရွေးပါ"; return@launch
                }

            }

            val app = getApplication<Application>()
            var lat: Double? = null
            var lng: Double? = null
            var accuracy: Double? = null
            var gpsWarning: String? = null
            val needsGps = type == "DELIVERY" && deliveryLocationMode?.uppercase() == "OTHER"
            if (needsGps || type == "DELIVERY") {
                if (LocationPermission.granted(app)) {
                    try {
                        val fix = LocationClient(app).current()
                        lat = fix.latitude
                        lng = fix.longitude
                        accuracy = fix.accuracy
                    } catch (_: Exception) {
                        if (needsGps) gpsWarning = "GPS မရသော်လည်း အော်ဒါ တင်လိုက်ပါသည်"
                    }
                } else if (needsGps) {
                    gpsWarning = "တည်နေရာ ခွင့်ပြုချက် မရသော်လည်း အော်ဒါ တင်လိုက်ပါသည်"
                }
            }
            try {
                val mode = if (type == "DELIVERY") deliveryLocationMode?.trim()?.uppercase() else null
                val res = ApiClient.service.placeOrder(
                    auth(),
                    PlaceOrderRequest(
                        paymentChoice = if (type == "PICKUP") "TRANSFER" else null,
                        note = note,
                        lines = lines,
                        idempotencyKey = key,
                        orderType = type,
                        deliveryLocationMode = mode,
                        deliveryAddress = if (mode == "OTHER") deliveryAddress?.trim() else null,
                        deliveryPhone = if (mode == "OTHER") deliveryPhone?.trim() else null,
                        townshipId = if (type == "DELIVERY") townshipId else null,
                        wardId = if (type == "DELIVERY") wardId else null,
                        requestedDeliveryAt = if (type == "DELIVERY") requestedDeliveryAt?.trim() else null,
                        latitude = lat,
                        longitude = lng,
                        locationAccuracy = accuracy,
                        locationSource = when {
                            lat == null -> null
                            mode == "OTHER" -> "OTHER"
                            mode == "PROFILE" -> "APP"
                            else -> "APP"
                        },
                        promoCode = promoCode
                    )
                )
                if (res.isSuccessful && res.body()?.success == true) {
                    val placed = res.body()?.data
                    if (placed != null && placed.id > 0) {
                        PreferenceManager(getApplication()).focusOrderId = placed.id
                    }
                    CartStore.clearAfterOrder()
                    lastCheckoutKey = null
                    lastCheckoutStamp = null
                    message = listOfNotNull(
                        if (type == "PICKUP") "အော်ဒါ တင်ပြီးပါပြီ — စရံကြိုလွှဲပါ"
                        else "အော်ဒါ တင်ပြီးပါပြီ — ဆိုင်အတည်ပြုချက် စောင့်နေသည်",
                        gpsWarning
                    ).joinToString(" — ")
                    loadMine()
                    openHistoryTab = true
                } else message = responseMessage(res, "အော်ဒါ မတင်နိုင်ပါ")
            } catch (e: Exception) { message = e.message }
            } finally {
                checkoutPlacing = false
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScaffold(
    onLogout: () -> Unit,
    onIdleLogout: () -> Unit = onLogout
) {
    val vm: HomeViewModel = viewModel()
    val versionVm: VersionCheckViewModel = viewModel()
    val versionState by versionVm.state.collectAsState()
    val context = LocalContext.current
    val prefs = remember { PreferenceManager(context.applicationContext) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    LaunchedEffect(Unit) {
        if (!LocationPermission.granted(context)) {
            locationPermission.launch(LocationPermission.required)
        } else if (Build.VERSION.SDK_INT >= 33) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val latestOnLogout by rememberUpdatedState(onLogout)
    val latestOnIdleLogout by rememberUpdatedState(onIdleLogout)
    var sessionEnded by remember { mutableStateOf(false) }

    fun performLogout(idle: Boolean = false) {
        if (sessionEnded) return
        sessionEnded = true
        runCatching { vm.logout() }
        if (idle) {
            Toast.makeText(
                context,
                "အသုံးမပြုသဖြင့် $IDLE_TIMEOUT_MINUTES မိနစ်အကြာ အကောင့်မှ အလိုအလျောက် ထွက်လိုက်ပါသည်",
                Toast.LENGTH_LONG
            ).show()
            latestOnIdleLogout()
        } else {
            latestOnLogout()
        }
    }

    DisposableEffect(vm, lifecycleOwner) {
        vm.startOrderUpdates()
        vm.startChat()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (prefs.isSessionIdle()) {
                    performLogout(idle = true)
                } else {
                    prefs.touchSession()
                    vm.refreshOrderUpdates()
                    vm.refreshCartQuietly()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            vm.stopOrderUpdates()
            vm.stopChat()
        }
    }

    LaunchedEffect(vm.orderSocketConnected) {
        if (vm.orderSocketConnected) return@LaunchedEffect
        while (true) {
            delay(15_000)
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                vm.refreshOrderUpdates()
            }
        }
    }

    // Foreground idle watchdog (checks every 20s)
    LaunchedEffect(Unit) {
        while (true) {
            delay(20_000)
            if (prefs.isSessionIdle()) {
                performLogout(idle = true)
                break
            }
        }
    }

    var tab by remember { mutableIntStateOf(0) }
    var showProducts by remember { mutableStateOf(false) }
    var serviceSection by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        if (prefs.consumeOpenOrdersTab() || prefs.focusOrderId > 0) {
            tab = 3
            showProducts = false
        }
    }
    LaunchedEffect(vm.openHistoryTab) {
        if (vm.openHistoryTab) {
            tab = 3
            showProducts = false
            vm.openHistoryTab = false
        }
    }
    LaunchedEffect(vm.openBookingsTab) {
        if (vm.openBookingsTab) {
            tab = 1
            showProducts = false
            serviceSection = 1
            vm.openBookingsTab = false
        }
    }
    var showNotifications by remember { mutableStateOf(false) }
    var returnToProductsAfterCart by remember { mutableStateOf(false) }
    var exitToastAt by remember { mutableLongStateOf(0L) }

    fun goHome() {
        tab = 0
        showProducts = false
        returnToProductsAfterCart = false
    }

    fun openCart(fromProducts: Boolean) {
        returnToProductsAfterCart = fromProducts
        showProducts = false
        tab = 2
    }

    fun handleSystemBack(): Boolean {
        return when {
            showNotifications -> {
                vm.markNotificationsRead()
                showNotifications = false
                true
            }
            showProducts -> {
                showProducts = false
                true
            }
            tab == 2 && returnToProductsAfterCart -> {
                tab = 0
                showProducts = true
                returnToProductsAfterCart = false
                true
            }
            tab != 0 -> {
                goHome()
                true
            }
            else -> false
        }
    }

    // Nested screens: step back. Home root: double-back to exit app (session stays until idle/logout).
    BackHandler(enabled = true) {
        if (!handleSystemBack()) {
            val now = System.currentTimeMillis()
            if (now - exitToastAt < 2000L) {
                (context as? android.app.Activity)?.finish()
            } else {
                exitToastAt = now
                Toast.makeText(context, "ထပ်နှိပ်ပြီး App ပိတ်မည်", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(Unit) {
        versionVm.check()
        vm.loadCatalog(); vm.loadMine(); vm.loadProfile(); vm.refreshCartQuietly()
    }
    if (versionState.showDialog) {
        versionState.availableUpdate?.let { update ->
            UpdateDialog(
                update = update,
                downloadProgress = versionState.downloadProgress,
                apkFile = versionState.apkFile,
                downloadError = versionState.downloadError,
                onDownload = versionVm::downloadAndInstall,
                onInstall = { versionVm.triggerInstall(context) },
                onDismiss = versionVm::dismiss
            )
        }
    }
    val cartItems by CartStore.items.collectAsState()
    val cartCount = cartItems.sumOf { it.qty }
    vm.message?.let { msg ->
        AlertDialog(onDismissRequest = { vm.message = null }, confirmButton = { TextButton(onClick = { vm.message = null }) { Text("OK") } }, text = { Text(msg) })
    }
    if (vm.cartIssues.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { vm.dismissCartIssues() },
            title = { Text("ခြင်းတောင်း ပြောင်းလဲချက်") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "လက်ရှိဈေးနှင့် စတော့ကို ပြန်စစ်ပြီးပါပြီ။ အောက်ပါအချက်များကို ဖတ်ပြီးမှ အော်ဒါဆက်တင်ပါ။",
                        style = MaterialTheme.typography.bodySmall
                    )
                    vm.cartIssues.forEach { issue ->
                        Text("• ${issue.detail}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.continueCheckoutAfterIssues() }) {
                    Text("နားလည်ပါပြီ · ဆက်လုပ်မည်")
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.dismissCartIssues() }) {
                    Text("ခြင်းတောင်း ပြန်ကြည့်မည်")
                }
            }
        )
    }

        Scaffold(
            topBar = {
                val showOuterTopBar = (tab != 0 || showProducts) && tab != 1 && tab != 2 && tab != 4 && tab != 6
                if (showOuterTopBar) {
                    Surface(
                        color = CardBg,
                        border = BorderStroke(1.dp, BorderColor.copy(alpha = 0.5f)),
                        shadowElevation = 1.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .height(38.dp)
                                .padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val showBack = showProducts || tab != 0
                            if (showBack) {
                                IconButton(
                                    onClick = { handleSystemBack() },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.ArrowBack,
                                        contentDescription = "နောက်သို့",
                                        modifier = Modifier.size(18.dp),
                                        tint = TextMain
                                    )
                                }
                                Spacer(Modifier.width(4.dp))
                            }
                            Text(
                                text = when {
                                    showProducts -> "ပစ္စည်းများ"
                                    tab == 1 -> "Service"
                                    tab == 2 -> "ခြင်းတောင်း"
                                    tab == 3 -> "မှတ်တမ်း"
                                    tab == 4 -> "အကောင့်"
                                    tab == 5 -> "အကြိုက်စာရင်း"
                                    tab == 6 -> "ဆိုင်နှင့် စကားပြော"
                                    else -> "SSPD Customer"
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = TextMain,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        ) { pad ->

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = pad.calculateTopPadding())
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                Crossfade(
                    targetState = if (showProducts) 100 else tab,
                    animationSpec = tween(durationMillis = 220),
                    label = "tabCrossfade"
                ) { currentTab ->
                    when (currentTab) {
                        0 -> PullToRefreshBox(
                            isRefreshing = vm.refreshing,
                            onRefresh = {
                                vm.pullToRefresh(catalog = true, mine = true, profile = true)
                            },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            CustomerDashboardScreen(
                                profile = vm.profile,
                                products = vm.products,
                                jobs = vm.jobs,
                                notifications = vm.notifications,
                                unreadNotificationCount = vm.unreadNotificationCount,
                                orderCount = vm.orders.count { it.status !in listOf("COMPLETED", "CANCELLED") },
                                serviceCount = activeServiceCount(vm.jobs, vm.bookings),
                                completedCount = vm.purchases.size + vm.jobs.count { it.status in listOf("COMPLETED", "DELIVERED") },
                                onProducts = { showProducts = true },
                                onServices = { tab = 1; returnToProductsAfterCart = false },
                                onCart = { openCart(fromProducts = false) },
                                onHistory = { tab = 3; returnToProductsAfterCart = false },
                                onProfile = { tab = 4; returnToProductsAfterCart = false },
                                onNotifications = {
                                    vm.markNotificationsRead()
                                    showNotifications = true
                                },
                                onAddToCart = {
                                    CartStore.add(it)
                                    vm.message = "${it.name.orEmpty()} ခြင်းတောင်းထဲ ထည့်ပြီးပါပြီ"
                                }
                            )
                        }
                        1 -> PullToRefreshBox(
                            isRefreshing = vm.refreshing,
                            onRefresh = { vm.pullToRefresh(catalog = true) },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            ServiceTab(vm, serviceSection, onSectionChange = { serviceSection = it })
                        }
                        2 -> CartTab(
                            vm = vm,
                            onBrowseProducts = {
                                tab = 0
                                showProducts = true
                                returnToProductsAfterCart = false
                            }
                        )
                        3 -> PullToRefreshBox(
                            isRefreshing = vm.refreshing,
                            onRefresh = { vm.pullToRefresh(mine = true) },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            ActivityTab(
                                vm = vm,
                                onReorder = { order ->
                                    vm.reorderCancelled(order)
                                    openCart(fromProducts = false)
                                },
                                onRequestNewService = { tab = 1 }
                            )
                        }
                        4 -> PullToRefreshBox(
                            isRefreshing = vm.refreshing,
                            onRefresh = { vm.pullToRefresh(mine = true, profile = true) },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            AccountTab(
                                vm = vm,
                                availableUpdate = versionState.availableUpdate,
                                onOpenUpdate = versionVm::openUpdateDialog,
                                onLogout = { performLogout(idle = false) },
                                onNavigateToOrders = { tab = 3; showProducts = false; returnToProductsAfterCart = false },
                                onNavigateToBookings = { tab = 1; serviceSection = 1; showProducts = false },
                                onNavigateToWishlist = { if (CustomerAppFeatures.WISHLIST) { tab = 5; showProducts = false; returnToProductsAfterCart = false } },
                                onNavigateToChat = { tab = 6; showProducts = false; returnToProductsAfterCart = false }
                            )
                        }
                        5 -> if (CustomerAppFeatures.WISHLIST) {
                            PullToRefreshBox(
                                isRefreshing = vm.refreshing,
                                onRefresh = { vm.pullToRefresh(wishlist = true) },
                                modifier = Modifier.fillMaxSize()
                            ) {
                                CustomerWishlistScreen(
                                    wishlistItems = vm.wishlist,
                                    isLoading = vm.wishlistLoading,
                                    onToggleFavorite = { vm.toggleWishlist(it) },
                                    onAddToCart = { product ->
                                        CartStore.add(product)
                                        vm.message = "${product.name.orEmpty()} ခြင်းတောင်းထဲ ထည့်ပြီးပါပြီ"
                                    }
                                )
                            }
                        }
                        6 -> {
                            LaunchedEffect(Unit) {
                                vm.loadChatHistory()
                                while (true) {
                                    delay(8_000)
                                    vm.loadChatHistory()
                                }
                            }
                            CustomerChatScreen(
                                messages = vm.chatMessages,
                                onSendMessage = { text -> vm.sendMessage(text) },
                                onBack = { tab = 0 }
                            )
                        }
                        7 -> PullToRefreshBox(
                            isRefreshing = vm.refreshing,
                            onRefresh = { vm.pullToRefresh(mine = true) },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            SaleHistoryTab(vm)
                        }
                        8, 9 -> PullToRefreshBox(
                            isRefreshing = vm.refreshing,
                            onRefresh = { vm.pullToRefresh(mine = true) },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            CustomerServiceJobsScreen(
                                jobs = vm.jobs,
                                error = vm.jobsError,
                                onRetry = { vm.loadMine() },
                                onRequestNewService = { tab = 1 }
                            )
                        }
                        100 -> PullToRefreshBox(
                            isRefreshing = vm.refreshing,
                            onRefresh = { vm.pullToRefresh(catalog = true) },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            ProductTab(vm, onOpenCart = { openCart(fromProducts = true) })
                        }
                        else -> {
                            showProducts = true
                            tab = 0
                        }
                    }
                }

            val showNav = tab != 1 && tab != 4 && tab != 6 && (tab != 2 || cartCount == 0)
            if (showNav) {
                CustomerBottomNav(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    homeSelected = tab == 0 && !showProducts,
                    wishlistSelected = CustomerAppFeatures.WISHLIST && tab == 5,
                    serviceSelected = tab == 1,
                    productsSelected = showProducts,
                    cartSelected = tab == 2,
                    profileSelected = tab == 4,
                    cartCount = cartCount,
                    onHome = { goHome() },
                    onService = {
                        tab = 1; showProducts = false; returnToProductsAfterCart = false
                    },
                    onProducts = {
                        tab = 0; showProducts = true; returnToProductsAfterCart = false
                    },
                    onWishlist = {
                        if (!CustomerAppFeatures.WISHLIST) return@CustomerBottomNav
                        tab = 5; showProducts = false; returnToProductsAfterCart = false
                    },
                    onCart = { openCart(fromProducts = showProducts) },
                    onProfile = {
                        tab = 4; showProducts = false; returnToProductsAfterCart = false
                    }
                )
            }
        }
    }

    if (showNotifications) {
        CustomerNotificationsDialog(
            notifications = vm.notifications,
            onDismiss = {
                vm.markNotificationsRead()
                showNotifications = false
            },
            onOpenHistory = {
                vm.markNotificationsRead()
                showNotifications = false
                tab = 3
                returnToProductsAfterCart = false
            }
        )
    }
}
}

@Composable
private fun ProductTab(vm: HomeViewModel, onOpenCart: () -> Unit) {
    val cartItems by CartStore.items.collectAsState()
    CustomerProductScreen(
        products = vm.products,
        categoryOptions = vm.categories,
        brandOptions = vm.brands.mapNotNull { it.name?.takeIf(String::isNotBlank) },
        loading = vm.catalogLoading,
        page = vm.catalogPage,
        total = vm.catalogTotal,
        hasNext = vm.catalogHasNext,
        error = vm.catalogError,
        onPage = { vm.requestCatalogPage(it) },
        onCatalogQuery = { q, category, brand, type, sort ->
            vm.searchCatalog(q, category, vm.brands.firstOrNull { it.name == brand }?.id, type, sort)
        },
        cartCount = cartItems.sumOf { it.qty },
        cartQtyByProductId = cartItems.associate { it.product.id to it.qty },
        onChangeQty = { product, qty -> CartStore.setQty(product, qty) },
        onOpenCart = onOpenCart,
        wishlistIds = vm.wishlistIds,
        onToggleFavorite = { vm.toggleWishlist(it) },
        onLoadB2Playback = vm::b2ProductVideoUrl
    )
}

@Composable
private fun ServiceTab(vm: HomeViewModel, section: Int, onSectionChange: (Int) -> Unit) {
    var bookingService by remember { mutableStateOf<CatalogService?>(null) }
    LaunchedEffect(section) {
        if (section == 1) bookingService = null
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = section == 0,
                onClick = { onSectionChange(0) },
                label = { Text("Booking တင်မည်") }
            )
            FilterChip(
                selected = section == 1,
                onClick = { vm.retryBookings(); onSectionChange(1) },
                label = { Text("Booking များ") }
            )
            FilterChip(
                selected = section == 2,
                onClick = { onSectionChange(2) },
                label = { Text("ဝန်ဆောင်မှုနှုန်းထားများ") }
            )
        }
        when (section) {
            1 -> CustomerBookingsScreen(
                bookings = vm.bookings,
                error = vm.bookingsError,
                onRetry = vm::retryBookings,
                modifier = Modifier.weight(1f)
            )
            2 -> CustomerServiceRatesScreen(
                services = vm.services,
                onRetry = { vm.pullToRefresh(catalog = true) },
                onBookService = { service ->
                    bookingService = service
                    onSectionChange(0)
                },
                modifier = Modifier.weight(1f)
            )
            else -> CustomerServiceBookingForm(
                services = vm.services,
                defaultAddress = vm.profile.address.orEmpty(),
                submitting = vm.serviceRequestSubmitting,
                onSubmit = vm::requestService,
                modifier = Modifier.weight(1f),
                showBottomNav = false,
                initialService = bookingService,
                onClearSelectedService = { bookingService = null }
            )
        }
    }
}

@Composable
private fun CartTab(vm: HomeViewModel, onBrowseProducts: () -> Unit) {
    val items by CartStore.items.collectAsState()
    var note by remember { mutableStateOf("") }
    var orderType by remember { mutableStateOf("DELIVERY") }
    var paymentChoice by remember { mutableStateOf("TRANSFER") }
    var pickupDepositPercent by remember { mutableStateOf(30.0) }
    var deliveryEnabled by remember { mutableStateOf(true) }
    var deliveryHours by remember { mutableStateOf(DeliveryHoursConfig()) }
    var requestedDeliveryAt by remember { mutableStateOf<String?>(null) }
    var appliedPromo by remember { mutableStateOf<String?>(null) }
    var deliveryMode by remember { mutableStateOf("PROFILE") }
    var deliveryPhone by remember { mutableStateOf("") }
    var deliveryAddress by remember { mutableStateOf("") }
    var regionId by remember { mutableStateOf<Int?>(null) }
    var townshipId by remember { mutableStateOf<Int?>(null) }
    var wardId by remember { mutableStateOf<Int?>(null) }
    var locations by remember { mutableStateOf<List<DeliveryRegion>>(emptyList()) }

    LaunchedEffect(Unit) {
        vm.refreshCartQuietly()
        runCatching {
            val res = ApiClient.service.deliveryLocations()
            if (res.isSuccessful && res.body()?.success == true) {
                locations = res.body()?.data.orEmpty()
            }
        }
        runCatching {
            val res = ApiClient.service.branding()
            val data = res.body()?.data
            val pct = data?.pickupDepositPercent
            if (res.isSuccessful && pct != null && pct in 1.0..100.0) {
                pickupDepositPercent = pct
            }
            if (res.isSuccessful) {
                deliveryHours = DeliveryHoursConfig.from(data)
            }
            if (res.isSuccessful && data?.deliveryEnabled == false) {
                deliveryEnabled = false
                orderType = "PICKUP"
            }
        }
    }

    LaunchedEffect(orderType) {
        if (orderType == "PICKUP") paymentChoice = "TRANSFER"
    }

    val cartStamp = items.joinToString("|") { "${it.product.id}:${it.qty}" }
    val checkoutKey = remember(cartStamp) { java.util.UUID.randomUUID().toString() }
    var deliveryQuote by remember { mutableStateOf<DeliveryQuote?>(null) }
    var quoteLoading by remember { mutableStateOf(false) }
    var quoteError by remember { mutableStateOf<String?>(null) }

    var quoteRetry by remember { mutableIntStateOf(0) }

    LaunchedEffect(orderType, townshipId, wardId, cartStamp, quoteRetry) {
        deliveryQuote = null
        quoteError = null
        if (orderType != "DELIVERY" || townshipId == null || items.isEmpty()) {
            quoteLoading = false
            return@LaunchedEffect
        }
        quoteLoading = true
        kotlinx.coroutines.delay(300)
        try {
            val res = ApiClient.service.deliveryQuote(
                vm.auth(),
                DeliveryQuoteRequest(
                    orderType = "DELIVERY",
                    townshipId = townshipId,
                    wardId = wardId,
                    lines = items.map { OrderLineRequest(it.product.id, it.qty) }
                )
            )
            val body = res.body()
            if (res.isSuccessful && body?.success == true) {
                deliveryQuote = body.data
                quoteError = null
            } else {
                quoteError = body?.message ?: "ပို့ခတွက်မရပါ"
            }
        } catch (e: Exception) {
            quoteError = e.message ?: "ပို့ခတွက်မရပါ"
        } finally {
            quoteLoading = false
        }
    }

    CustomerCartScreen(
        items = items,
        note = note,
        onNoteChange = { note = it },
        onChangeQty = { product, qty -> CartStore.setQty(product, qty) },
        onRemove = { product -> CartStore.setQty(product, 0) },
        onCheckout = {
            vm.checkout(
                note = note,
                orderType = orderType,
                deliveryLocationMode = if (orderType == "DELIVERY") deliveryMode else null,
                deliveryAddress = deliveryAddress,
                deliveryPhone = deliveryPhone,
                townshipId = if (orderType == "DELIVERY") townshipId else null,
                wardId = if (orderType == "DELIVERY") wardId else null,
                paymentChoice = paymentChoice,
                requestedDeliveryAt = requestedDeliveryAt,
                idempotencyKey = checkoutKey,
                promoCode = appliedPromo
            )
        },
        onBrowseProducts = onBrowseProducts,
        paymentChoice = paymentChoice,
        onPaymentChoiceChange = { paymentChoice = it },
        checkoutEnabled = items.isNotEmpty() && !vm.checkoutPlacing,
        orderType = orderType,
        onOrderTypeChange = { orderType = it },
        deliveryMode = deliveryMode,
        onDeliveryModeChange = { deliveryMode = it },
        deliveryPhone = deliveryPhone,
        onDeliveryPhoneChange = { deliveryPhone = it },
        deliveryAddress = deliveryAddress,
        onDeliveryAddressChange = { deliveryAddress = it },
        profileAddress = vm.profile.address,
        profilePhone = vm.profile.phone,
        locations = locations,
        selectedRegionId = regionId,
        onRegionChange = {
            regionId = it
            townshipId = null
            wardId = null
        },
        selectedTownshipId = townshipId,
        onTownshipChange = {
            townshipId = it
            wardId = null
        },
        selectedWardId = wardId,
        onWardChange = { wardId = it },
        pickupDepositPercent = pickupDepositPercent,
        checkoutPlacing = vm.checkoutPlacing,
        quotedDeliveryCharge = deliveryQuote?.deliveryCharge,
        quoteState = deliveryQuote?.state,
        quoteLoading = quoteLoading,
        quoteReason = deliveryQuote?.reason,
        quoteError = quoteError,
        onRetryQuote = { quoteRetry++ },
        deliveryEnabled = deliveryEnabled,
        deliveryHours = deliveryHours,
        requestedDeliveryAt = requestedDeliveryAt,
        onRequestedDeliveryAtChange = { requestedDeliveryAt = it },
        onAppliedPromoChange = { appliedPromo = it }
    )
}

private fun money(amount: Double): String {
    val safe = if (amount.isFinite()) amount else 0.0
    val absValue = abs(safe).toLong()
    val formatted = absValue.toString().reversed().chunked(3).joinToString(",").reversed()
    return if (safe < 0) "-$formatted Ks" else "$formatted Ks"
}

@Composable
private fun SaleHistoryTab(vm: HomeViewModel) {
    var searchQuery by remember { mutableStateOf("") }
    var showInvoiceDialog by remember { mutableStateOf<CustomerPurchase?>(null) }

    val purchases = vm.purchases.filter { p ->
        val q = searchQuery.trim().lowercase()
        q.isBlank() || listOf(
            p.saleCode,
            p.paymentStatus,
            p.lines?.joinToString { line -> line.productName.orEmpty() }
        ).any { it.orEmpty().lowercase().contains(q) }
    }.sortedByDescending { it.id }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Sale History (ဝယ်ယူမှု မှတ်တမ်းများ)",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                color = PrimaryDark
            )
            Text(
                "ဆိုင်မှ ဝယ်ယူခဲ့သော ဘောင်ချာများနှင့် ပစ္စည်းစာရင်းများ (${vm.purchases.size} ခု)",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Sale Code / ပစ္စည်းအမည် ရှာရန်...") },
            leadingIcon = { Icon(Icons.Outlined.Search, null, tint = TextMuted) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Outlined.Close, contentDescription = "ရှင်းမည်", tint = TextMuted)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        if (vm.ordersLoading && vm.purchases.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (purchases.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = CardBg,
                border = BorderStroke(1.dp, BorderColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Outlined.Payments, null, tint = TextMuted, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("ဝယ်ယူမှု မှတ်တမ်း မရှိသေးပါ", fontWeight = FontWeight.Bold, color = TextMain)
                    Text("ဆိုင်မှ ဝယ်ယူထားသော ဘောင်ချာများကို ဤနေရာတွင် ကြည့်နိုင်ပါသည်", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(purchases, key = { index, p -> "p-${p.id}-$index" }) { _, purchase ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = CardBg,
                        border = BorderStroke(1.dp, BorderColor),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(PrimaryLight),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Outlined.Payments, null, tint = Primary, modifier = Modifier.size(20.dp))
                                    }
                                    Column {
                                        Text(
                                            purchase.saleCode.orEmpty().ifBlank { "Sale #${purchase.id}" },
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = PrimaryDark
                                        )
                                        purchase.saleDate?.takeIf { it.isNotBlank() }?.let {
                                            Text(it.replace('T', ' ').take(16), style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                        }
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = SuccessBg,
                                    border = BorderStroke(1.dp, Success.copy(alpha = 0.2f))
                                ) {
                                    Text(
                                        purchase.paymentStatus ?: "PAID",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        color = Success,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            HorizontalDivider(color = BorderColor.copy(alpha = 0.5f))

                            val lines = purchase.lines.orEmpty()
                            lines.forEach { line ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(line.productName.orEmpty().ifBlank { "Product" }, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall, color = TextMain)
                                        line.serialNumber?.takeIf { it.isNotBlank() }?.let { sn ->
                                            Text("S/N · $sn", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                        }
                                    }
                                    Text("× ${line.qty ?: 1}", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                    Spacer(Modifier.width(12.dp))
                                    Text(money(line.subtotal ?: 0.0), fontWeight = FontWeight.Bold, color = PrimaryDark, style = MaterialTheme.typography.bodySmall)
                                }
                            }

                            HorizontalDivider(color = BorderColor.copy(alpha = 0.5f))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = { showInvoiceDialog = purchase },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("PDF ဘောင်ချာ ဖွင့်", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text("စုစုပေါင်း", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                    Text(money(purchase.netAmount ?: 0.0), fontWeight = FontWeight.ExtraBold, color = Primary, style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    showInvoiceDialog?.let { p ->
        SaleInvoiceViewerDialog(
            purchaseSaleId = p.id,
            saleCode = p.saleCode,
            onDismiss = { showInvoiceDialog = null }
        )
    }
}

@Composable
private fun ActivityTab(
    vm: HomeViewModel,
    onReorder: (CustomerOrder) -> Unit,
    onRequestNewService: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember { PreferenceManager(context.applicationContext) }
    var proofPickerKey by remember { mutableStateOf<String?>(null) }
    var pickedProofUri by remember { mutableStateOf<Uri?>(null) }
    val proofPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) pickedProofUri = uri
    }
    var focusOrderId by remember { mutableStateOf(prefs.focusOrderId) }
    var selectedOrderId by remember { mutableStateOf<Int?>(null) }
    var historySubTab by remember { mutableIntStateOf(0) }

    BackHandler(enabled = selectedOrderId != null) {
        selectedOrderId = null
    }
    LaunchedEffect(Unit) {
        // Re-read after notification / place order.
        focusOrderId = prefs.focusOrderId
    }
    LaunchedEffect(vm.openHistoryTab, vm.orders.size) {
        if (prefs.focusOrderId > 0) focusOrderId = prefs.focusOrderId
    }
    val focusIndex = vm.orders.indexOfFirst { it.id == focusOrderId }.takeIf { it >= 0 }
    val focusOrder = focusIndex?.let { vm.orders.getOrNull(it) }
    val waitingShopOrProof = vm.orders.any { order ->
        val ship = order.shippingState?.trim()?.uppercase()
        val pay = order.paymentState?.trim()?.uppercase()
        ("DELIVERY".equals(order.orderType, ignoreCase = true) && ship in setOf("AWAITING_SHOP", "NEEDS_QUOTE"))
            || pay in setOf("PROOF_SUBMITTED", "CHECKING", "REVIEW", "LATE_REVIEW", "AWAITING_PAYMENT")
            || order.awaitingCustomerReceipt == true
            || ((pay.isNullOrBlank() || pay == "NONE") && order.status.equals("PENDING", ignoreCase = true))
    }
    LaunchedEffect(Unit) { vm.loadMine() }
    LaunchedEffect(waitingShopOrProof) {
        if (!waitingShopOrProof) return@LaunchedEffect
        while (isActive) {
            delay(2500)
            vm.loadMine()
        }
    }
    LaunchedEffect(focusOrderId) {
        if (focusOrderId <= 0) return@LaunchedEffect
        try {
            val res = ApiClient.service.myOrder(ApiClient.bearer(prefs.authToken), focusOrderId)
            val updated = res.body()?.data
            if (res.isSuccessful && updated != null) vm.upsertOrder(updated)
        } catch (_: Exception) { /* list poll still works */ }
    }
    val selectedOrder = vm.orders.find { it.id == selectedOrderId }
    if (selectedOrderId != null && selectedOrder == null) {
        selectedOrderId = null
    }
    val displayOrders = if (selectedOrderId != null && selectedOrder != null) {
        listOf(selectedOrder)
    } else {
        vm.orders
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Sub-Tab Switcher
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceSoft)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Surface(
                onClick = { historySubTab = 0 },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                color = if (historySubTab == 0) Primary else Color.Transparent
            ) {
                Text(
                    "App အော်ဒါများ (${vm.orders.size})",
                    modifier = Modifier.padding(vertical = 8.dp),
                    textAlign = TextAlign.Center,
                    color = if (historySubTab == 0) OnPrimary else TextMain,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Surface(
                onClick = { historySubTab = 1 },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                color = if (historySubTab == 1) Primary else Color.Transparent
            ) {
                Text(
                    "Service Job များ (${vm.jobs.size})",
                    modifier = Modifier.padding(vertical = 8.dp),
                    textAlign = TextAlign.Center,
                    color = if (historySubTab == 1) OnPrimary else TextMain,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }

        if (historySubTab == 1) {
            CustomerServiceJobsScreen(
                jobs = vm.jobs,
                error = vm.jobsError,
                onRetry = { vm.loadMine() },
                onRequestNewService = onRequestNewService
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        "Order History",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = PrimaryDark
                    )
                    Text(
                        "နှိပ်ပြီး အသေးစိတ် ကြည့်ပါ · တစ်ကဒ် ရွေးကြည့်လျှင် အခြားကဒ်များ ခေတ္တဖျောက်ထားပါမည်",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
                if (selectedOrderId != null && selectedOrder != null) {
                    item {
                        Surface(
                            onClick = { selectedOrderId = null },
                            shape = RoundedCornerShape(12.dp),
                            color = PrimaryLight,
                            border = BorderStroke(1.dp, Primary.copy(alpha = 0.2f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Outlined.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Primary
                                )
                                Text(
                                    "အော်ဒါအားလုံး ပြန်ကြည့်မည်",
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryDark,
                                    fontSize = 14.sp
                                )
                                Spacer(Modifier.weight(1f))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Primary.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        "၁ ခု ရွေးထားသည်",
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = PrimaryDark
                                    )
                                }
                            }
                        }
                    }
                }
                if (focusOrder != null &&
                    focusOrder.status.equals("PENDING", ignoreCase = true) &&
                    (focusOrder.paymentState.isNullOrBlank() || focusOrder.paymentState.equals("NONE", true)) &&
                    (focusOrder.shippingState == null || focusOrder.shippingState in setOf("AWAITING_SHOP", "NEEDS_QUOTE", "LEGACY"))
                ) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = PrimaryLight,
                            border = BorderStroke(1.dp, Primary.copy(alpha = 0.25f))
                        ) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("ဆိုင်အတည်ပြုချက် စောင့်နေသည်", fontWeight = FontWeight.Bold, color = PrimaryDark)
                                Text(focusOrder.orderNo ?: "Order", fontWeight = FontWeight.SemiBold)
                                focusOrder.requestedDeliveryAt?.takeIf { it.isNotBlank() }?.let {
                                    Text("တောင်းဆိုချိန် · ${it.replace('T', ' ').take(16)}", style = MaterialTheme.typography.bodySmall)
                                }
                                Text(
                                    "ဆိုင်မှ စစ်ဆေးနေသည်။ အတည်ပြုပြီးလျှင် အသိပေးပါမည် — app ပိတ်ထားနိုင်ပါသည်။",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMuted
                                )
                                TextButton(onClick = {
                                    prefs.clearFocusOrderId()
                                    focusOrderId = 0
                                }) { Text("ဤအသိပေးချက် ပိတ်မည်") }
                            }
                        }
                    }
                }
                vm.ordersError?.let { err ->
                    item { ErrorRetryBanner(err, onRetry = vm::retryOrders) }
                }
                when {
                    vm.ordersLoading && vm.orders.isEmpty() -> {
                        item { OrderSkeletonList() }
                    }
                    vm.orders.isEmpty() && vm.ordersError == null -> {
                        item { CustomerOrderHistoryEmpty() }
                    }
                    else -> {
                        itemsIndexed(displayOrders, key = { index, order -> "o-${order.id}-$index" }) { index, order ->
                            val itemKey = "o-${order.id}-$index"
                            val isSelected = selectedOrderId == order.id
                            CustomerOrderHistoryCard(
                                order,
                                onOrderUpdated = { updated ->
                                    vm.upsertOrder(updated)
                                    if (updated.id == focusOrderId &&
                                        (updated.shippingState == "QUOTED" ||
                                            updated.paymentState in setOf("AWAITING_PAYMENT", "PROOF_SUBMITTED", "PAID", "FULFILLED") ||
                                            updated.status.equals("CANCELLED", true))
                                    ) {
                                        prefs.clearFocusOrderId()
                                        focusOrderId = 0
                                    }
                                },
                                onReorder = onReorder,
                                expanded = isSelected,
                                pickedImageUri = pickedProofUri.takeIf { proofPickerKey == itemKey },
                                onPickImage = {
                                    proofPickerKey = itemKey
                                    pickedProofUri = null
                                    proofPicker.launch("image/*")
                                },
                                onCardClick = {
                                    if (selectedOrderId == order.id) {
                                        selectedOrderId = null
                                    } else {
                                        selectedOrderId = order.id
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountTab(
    vm: HomeViewModel,
    availableUpdate: com.sspd.servicemgmt.core.network.AppVersionDTO? = null,
    onOpenUpdate: () -> Unit = {},
    onLogout: () -> Unit,
    onNavigateToOrders: () -> Unit,
    onNavigateToBookings: () -> Unit,
    onNavigateToWishlist: () -> Unit,
    onNavigateToChat: () -> Unit
) {
    var confirmLogout by remember { mutableStateOf(false) }
    val doLogout by rememberUpdatedState(onLogout)
    LaunchedEffect(Unit) { vm.loadProfile() }
    CustomerProfileScreen(
        profile = vm.profile,
        loyaltyPoints = vm.loyaltyPoints,
        orderCount = vm.orders.size + vm.purchases.size,
        serviceCount = vm.bookings.size + vm.jobs.size,
        purchases = vm.purchases,
        jobs = vm.jobs,
        orders = vm.orders,
        bookings = vm.bookings,
        loading = vm.profileLoading,
        saving = vm.profileSaving,
        passwordSaving = vm.passwordSaving,
        biometricEnabled = vm.biometricEnabled,
        onBiometricEnabledChange = vm::setBiometricEnabled,
        onNavigateToOrders = onNavigateToOrders,
        onNavigateToBookings = onNavigateToBookings,
        onNavigateToWishlist = onNavigateToWishlist,
        onNavigateToChat = onNavigateToChat,
        onSave = vm::saveProfile,
        onChangePassword = vm::changePassword,
        availableUpdate = availableUpdate,
        onOpenUpdate = onOpenUpdate,
        onLogout = { confirmLogout = true }
    )
    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("အကောင့်မှ ထွက်မည်", fontWeight = FontWeight.ExtraBold) },
            text = {
                Text("Back ခလုတ်ဖြင့် အဆင့်ဆင့် ပြန်ရန် မလိုပါ။ ချက်ချင်း Login စာမျက်နှာသို့ ထွက်ပါမည်။")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmLogout = false
                    doLogout()
                }) { Text("ချက်ချင်း ထွက်မည်", fontWeight = FontWeight.Bold, color = Danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogout = false }) { Text("မလုပ်တော့ပါ") }
            }
        )
    }
}
