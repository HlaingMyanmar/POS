package com.sspd.servicemgmt.core.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Streaming

interface ApiService {
    @POST("customer-portal/push-devices")
    suspend fun registerPushDevice(@Header("Authorization") auth: String, @Body body: PushTokenRequest): Response<ApiResponse<Unit>>
    @POST("customer-portal/push-devices/unregister")
    suspend fun unregisterPushDevice(@Header("Authorization") auth: String, @Body body: PushTokenRequest): Response<ApiResponse<Unit>>
    @POST("customer-portal/delivery-quote")
    suspend fun deliveryQuote(@Header("Authorization") auth: String, @Body body: DeliveryQuoteRequest): Response<ApiResponse<DeliveryQuote>>
    @POST("customer-portal/orders/{id}/shipping-decision")
    suspend fun shippingDecision(@Header("Authorization") auth: String, @retrofit2.http.Path("id") id: Int,
        @Body body: ShippingDecision): Response<ApiResponse<CustomerOrder>>

    @GET("customer-portal/payment-channels")
    suspend fun paymentChannels(
        @Header("Authorization") auth: String
    ): Response<ApiResponse<List<CustomerPaymentChannel>>>

    @POST("customer-portal/orders/{id}/payment-choice")
    suspend fun choosePayment(
        @Header("Authorization") auth: String,
        @retrofit2.http.Path("id") id: Int,
        @Body body: PaymentChoiceRequest
    ): Response<ApiResponse<CustomerOrder>>

    @POST("customer-portal/orders/{id}/payment-channel")
    suspend fun choosePaymentChannel(
        @Header("Authorization") auth: String,
        @retrofit2.http.Path("id") id: Int,
        @Body body: PaymentChannelRequest
    ): Response<ApiResponse<CustomerOrder>>

    @GET("customer-portal/orders/{id}/payment-proofs")
    suspend fun orderPaymentProofs(
        @Header("Authorization") auth: String,
        @retrofit2.http.Path("id") id: Int
    ): Response<ApiResponse<OrderPaymentProofs>>

    @retrofit2.http.Multipart
    @POST("customer-portal/orders/{id}/payment-proof")
    suspend fun submitOrderPayment(
        @Header("Authorization") auth: String,
        @retrofit2.http.Path("id") id: Int,
        @retrofit2.http.Part("reference") reference: okhttp3.RequestBody,
        @retrofit2.http.Part("amount") amount: okhttp3.RequestBody,
        @retrofit2.http.Part("paymentMethodId") paymentMethodId: okhttp3.RequestBody?,
        @retrofit2.http.Part image: okhttp3.MultipartBody.Part
    ): Response<ApiResponse<CustomerOrder>>

    @POST("customer-portal/auth/register")
    suspend fun register(@Body body: RegisterRequest): Response<ApiResponse<CustomerAuthResponse>>

    @POST("customer-portal/auth/login")
    suspend fun login(@Body body: LoginRequest): Response<ApiResponse<CustomerAuthResponse>>

    @POST("customer-portal/auth/google")
    suspend fun googleLogin(@Body body: GoogleLoginRequest): Response<ApiResponse<CustomerAuthResponse>>

    @POST("customer-portal/auth/forgot")
    suspend fun forgotPassword(@Body body: ForgotPasswordRequest): Response<ApiResponse<CustomerAuthResponse>>

    @POST("customer-portal/auth/reset")
    suspend fun resetPassword(@Body body: ResetPasswordRequest): Response<ApiResponse<CustomerAuthResponse>>

    @GET("customer-portal/me")
    suspend fun me(@Header("Authorization") auth: String): Response<ApiResponse<CustomerAuthResponse>>

    @PUT("customer-portal/me")
    suspend fun updateProfile(
        @Header("Authorization") auth: String,
        @Body body: ProfileUpdateRequest
    ): Response<ApiResponse<CustomerAuthResponse>>

    @PUT("customer-portal/me/password")
    suspend fun changePassword(
        @Header("Authorization") auth: String,
        @Body body: ChangePasswordRequest
    ): Response<ApiResponse<CustomerAuthResponse>>

    @GET("customer-portal/catalog/products/page")
    suspend fun catalogPage(
        @retrofit2.http.Query("page") page: Int = 0,
        @retrofit2.http.Query("size") size: Int = 24,
        @retrofit2.http.Query("q") query: String? = null,
        @retrofit2.http.Query("categoryId") categoryId: Int? = null,
        @retrofit2.http.Query("brandId") brandId: Int? = null,
        @retrofit2.http.Query("productType") productType: String? = null,
        @retrofit2.http.Query("sort") sort: String = "name"
    ): Response<ApiResponse<CatalogPage>>

    @GET("customer-portal/catalog/products")
    suspend fun catalogProducts(): Response<ApiResponse<List<CatalogProduct>>>

    @GET("customer-portal/catalog/categories")
    suspend fun catalogCategories(): Response<ApiResponse<List<CatalogOption>>>

    @GET("customer-portal/catalog/brands")
    suspend fun catalogBrands(): Response<ApiResponse<List<CatalogOption>>>

    @GET("customer-portal/catalog/services")
    suspend fun catalogServices(): Response<ApiResponse<List<CatalogService>>>

    @POST("customer-portal/bookings")
    suspend fun requestService(
        @Header("Authorization") auth: String,
        @Body body: ServiceRequestBody
    ): Response<ApiResponse<BookingSummary>>

    @GET("customer-portal/bookings")
    suspend fun myBookings(@Header("Authorization") auth: String): Response<ApiResponse<List<BookingSummary>>>

    @GET("customer-portal/delivery-locations")
    suspend fun deliveryLocations(): Response<ApiResponse<List<DeliveryRegion>>>

    @GET("customer-portal/delivery-townships")
    suspend fun deliveryTownships(): Response<ApiResponse<List<DeliveryTownship>>>

    @POST("customer-portal/orders")
    suspend fun placeOrder(
        @Header("Authorization") auth: String,
        @Body body: PlaceOrderRequest
    ): Response<ApiResponse<CustomerOrder>>

    @GET("customer-portal/orders")
    suspend fun myOrders(@Header("Authorization") auth: String): Response<ApiResponse<List<CustomerOrder>>>

    @GET("customer-portal/orders/{id}")
    suspend fun myOrder(
        @Header("Authorization") auth: String,
        @retrofit2.http.Path("id") id: Int
    ): Response<ApiResponse<CustomerOrder>>

    @POST("customer-portal/orders/{id}/cancel")
    suspend fun cancelOrder(
        @Header("Authorization") auth: String,
        @retrofit2.http.Path("id") id: Int
    ): Response<ApiResponse<CustomerOrder>>

    @POST("customer-portal/orders/{id}/receipt")
    suspend fun confirmReceipt(
        @Header("Authorization") auth: String,
        @retrofit2.http.Path("id") id: Int,
        @Body body: ReceiptConfirmRequest
    ): Response<ApiResponse<CustomerOrder>>

    @POST("customer-portal/orders/{id}/returns")
    suspend fun requestReturn(
        @Header("Authorization") auth: String,
        @retrofit2.http.Path("id") id: Int,
        @Body body: ReturnRequest
    ): Response<ApiResponse<ProductReturn>>

    @GET("customer-portal/orders/{id}/returns")
    suspend fun orderReturns(
        @Header("Authorization") auth: String,
        @retrofit2.http.Path("id") id: Int
    ): Response<ApiResponse<List<ProductReturn>>>

    @POST("customer-portal/orders/{id}/rate")
    suspend fun rateOrder(
        @Header("Authorization") auth: String,
        @retrofit2.http.Path("id") id: Int,
        @Body body: OrderRatingRequest
    ): Response<ApiResponse<Unit>>

    /** Official POS sale voucher PDF (same template as shop). */
    @Streaming
    @GET("customer-portal/orders/{id}/invoice.pdf")
    suspend fun orderInvoicePdf(
        @Header("Authorization") auth: String,
        @retrofit2.http.Path("id") id: Int
    ): Response<okhttp3.ResponseBody>

    @Streaming
    @GET("customer-portal/orders/{id}/payment-receipt.pdf")
    suspend fun paymentReceiptPdf(
        @Header("Authorization") auth: String,
        @retrofit2.http.Path("id") id: Int
    ): Response<okhttp3.ResponseBody>

    @Streaming
    @GET("customer-portal/history/purchases/{saleId}/invoice.pdf")
    suspend fun purchaseInvoicePdf(
        @Header("Authorization") auth: String,
        @retrofit2.http.Path("saleId") saleId: Int
    ): Response<okhttp3.ResponseBody>

    @GET("customer-portal/history/purchases")
    suspend fun myPurchases(@Header("Authorization") auth: String): Response<ApiResponse<List<CustomerPurchase>>>

    @GET("customer-portal/history/jobs")
    suspend fun myJobs(@Header("Authorization") auth: String): Response<ApiResponse<List<CustomerJob>>>

    @GET("customer-portal/notifications")
    suspend fun myNotifications(@Header("Authorization") auth: String): Response<ApiResponse<List<CustomerNotification>>>

    @GET("customer-portal/chat/history")
    suspend fun chatHistory(
        @Header("Authorization") auth: String
    ): Response<ApiResponse<List<ChatMessage>>>

    @POST("customer-portal/chat/send")
    suspend fun sendChatMessage(
        @Header("Authorization") auth: String,
        @Body body: ChatRequest
    ): Response<ApiResponse<ChatMessage>>

    @GET("customer-portal/branding")

    suspend fun branding(): Response<ApiResponse<CustomerBranding>>

    @GET("customer-portal/branding/logo")
    suspend fun brandingLogo(): Response<okhttp3.ResponseBody>

    @POST("customer-portal/wishlist")
    suspend fun toggleWishlist(
        @Header("Authorization") auth: String,
        @Body body: WishlistRequest
    ): Response<ApiResponse<WishlistStatus>>

    @PUT("customer-portal/wishlist")
    suspend fun addWishlist(
        @Header("Authorization") auth: String,
        @Body body: WishlistRequest
    ): Response<ApiResponse<WishlistStatus>>

    @DELETE("customer-portal/wishlist/{productId}")
    suspend fun removeWishlist(
        @Header("Authorization") auth: String,
        @retrofit2.http.Path("productId") productId: Int
    ): Response<ApiResponse<WishlistStatus>>

    @GET("customer-portal/wishlist")
    suspend fun myWishlist(
        @Header("Authorization") auth: String
    ): Response<ApiResponse<List<CatalogProduct>>>

    @GET("customer-portal/wishlist/ids")
    suspend fun myWishlistIds(
        @Header("Authorization") auth: String
    ): Response<ApiResponse<List<Int>>>

    @POST("customer-portal/promo-code/validate")
    suspend fun validatePromoCode(
        @Header("Authorization") auth: String,
        @Body body: PromoCodeRequest
    ): Response<ApiResponse<PromoCodeResponse>>

    @GET("customer-portal/me/loyalty")
    suspend fun myLoyaltyPoints(
        @Header("Authorization") auth: String
    ): Response<ApiResponse<LoyaltyPoints>>
}
