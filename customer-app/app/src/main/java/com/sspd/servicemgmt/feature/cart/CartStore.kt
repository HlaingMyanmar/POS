package com.sspd.servicemgmt.feature.cart

import android.content.Context
import com.sspd.servicemgmt.core.network.CatalogProduct
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs

data class CartItem(
    val product: CatalogProduct,
    val qty: Int,
    val savedPrice: Double = product.sellingPrice ?: 0.0
)

object CartStore {
    private val _items = MutableStateFlow<List<CartItem>>(emptyList())
    val items = _items.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var persistence: CartPersistence? = null
    private var customerId: Int = 0

    fun init(context: Context) {
        if (persistence == null) {
            persistence = CartPersistence(context.applicationContext)
        }
    }

    suspend fun bindAccount(accountId: Int) {
        mutex.withLock {
            if (accountId <= 0) {
                customerId = 0
                _items.value = emptyList()
                return
            }
            if (customerId == accountId) return
            if (customerId > 0) {
                runCatching { persistence?.save(customerId, _items.value) }
            } else if (_items.value.isNotEmpty()) {
                customerId = accountId
                runCatching { persistence?.save(accountId, _items.value) }
                return
            }
            customerId = accountId
            _items.value = persistence?.load(accountId).orEmpty()
        }
    }

    /** Drop in-memory cart only. Other accounts' saved carts stay on disk. */
    fun detach() {
        customerId = 0
        _items.value = emptyList()
    }

    fun add(product: CatalogProduct) {
        val max = (product.stockQty ?: 0).coerceAtLeast(0)
        if (max <= 0) return
        _items.update { current ->
            val existing = current.find { it.product.id == product.id }
            if (existing == null) {
                current + CartItem(product, 1.coerceAtMost(max), product.sellingPrice ?: 0.0)
            } else {
                current.map {
                    if (it.product.id == product.id) {
                        it.copy(product = product, qty = (it.qty + 1).coerceAtMost(max))
                    } else it
                }
            }
        }
        persist()
    }

    fun setQty(productId: Int, qty: Int) {
        _items.update { current ->
            if (qty <= 0) current.filter { it.product.id != productId }
            else current.map { if (it.product.id == productId) it.copy(qty = qty) else it }
        }
        persist()
    }

    /** Add or update quantity; qty <= 0 removes the line. Never exceeds product stockQty. */
    fun setQty(product: CatalogProduct, qty: Int) {
        val max = (product.stockQty ?: 0).coerceAtLeast(0)
        val capped = qty.coerceAtMost(max)
        _items.update { current ->
            if (capped <= 0) current.filter { it.product.id != product.id }
            else {
                val exists = current.any { it.product.id == product.id }
                if (exists) current.map {
                    if (it.product.id == product.id) {
                        it.copy(product = product, qty = capped)
                    } else it
                }
                else current + CartItem(product, capped, product.sellingPrice ?: 0.0)
            }
        }
        persist()
    }

    /**
     * Match saved product IDs to live catalog. Caps qty to stock, drops unavailable lines,
     * and reports price / stock differences against the last acknowledged price.
     */
    fun reconcile(liveById: Map<Int, CatalogProduct>): List<CartIssue> {
        val issues = mutableListOf<CartIssue>()
        val next = ArrayList<CartItem>(_items.value.size)
        for (item in _items.value) {
            val live = liveById[item.product.id]
            val name = live?.name ?: item.product.name ?: "ပစ္စည်း #${item.product.id}"
            val stock = (live?.stockQty ?: 0).coerceAtLeast(0)
            val sellable = live != null && live.inStock != false && stock > 0
            if (!sellable) {
                issues += CartIssue(
                    kind = if (live == null) CartIssue.Kind.UNAVAILABLE else CartIssue.Kind.OUT_OF_STOCK,
                    productId = item.product.id,
                    name = name,
                    detail = if (live == null) {
                        "$name ကို ယခု မှာယူ၍ မရတော့ပါ — ခြင်းတောင်းမှ ဖယ်လိုက်ပါသည်"
                    } else {
                        "$name စတော့ကုန်ပါပြီ — ခြင်းတောင်းမှ ဖယ်လိုက်ပါသည်"
                    }
                )
                continue
            }
            val available = live ?: continue
            val qty = item.qty.coerceAtMost(stock)
            if (qty < item.qty) {
                issues += CartIssue(
                    kind = CartIssue.Kind.STOCK_CAPPED,
                    productId = item.product.id,
                    name = name,
                    detail = "$name ${item.qty} ခု တောင်းထားပြီး ${stock} ခုသာ ကျန်သည် — အရေအတွက် လျှော့လိုက်ပါသည်"
                )
            }
            val newPrice = available.sellingPrice ?: 0.0
            if (abs(item.savedPrice - newPrice) > 0.009) {
                issues += CartIssue(
                    kind = CartIssue.Kind.PRICE_CHANGED,
                    productId = item.product.id,
                    name = name,
                    detail = "$name ဈေး ${money(item.savedPrice)} မှ ${money(newPrice)} သို့ ပြောင်းသွားပါသည်"
                )
            }
            next += CartItem(product = available, qty = qty, savedPrice = item.savedPrice)
        }
        _items.value = next
        persist()
        return issues
    }

    fun acknowledgePrices() {
        _items.update { current ->
            current.map { it.copy(savedPrice = it.product.sellingPrice ?: it.savedPrice) }
        }
        persist()
    }

    /** After a successful order only. */
    fun clearAfterOrder() {
        _items.value = emptyList()
        val id = customerId
        if (id > 0) {
            scope.launch { runCatching { persistence?.clear(id) } }
        }
    }

    @Deprecated("Use detach() on logout or clearAfterOrder() after a successful order")
    fun clear() = detach()

    fun total(): Double = _items.value.sumOf { (it.product.sellingPrice ?: 0.0) * it.qty }

    private fun persist() {
        scope.launch {
            mutex.withLock {
                val id = customerId
                if (id > 0) {
                    runCatching { persistence?.save(id, _items.value) }
                }
            }
        }
    }

    private fun money(amount: Double): String {
        val safe = if (amount.isFinite()) amount else 0.0
        val absValue = abs(safe).toLong()
        val formatted = absValue.toString().reversed().chunked(3).joinToString(",").reversed()
        return if (safe < 0) "-$formatted Ks" else "$formatted Ks"
    }
}
