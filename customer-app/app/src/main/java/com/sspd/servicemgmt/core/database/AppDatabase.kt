package com.sspd.servicemgmt.core.database

import androidx.room.*
import com.sspd.servicemgmt.core.network.CatalogProduct
import com.sspd.servicemgmt.core.network.CustomerOrder

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey val id: Int,
    val name: String?,
    val productCode: String?,
    val sellingPrice: Double?,
    val thumbnailUrl: String?,
    val categoryName: String?
)

@Entity(tableName = "orders")
data class OrderEntity(
    @PrimaryKey val id: Int,
    val orderNo: String?,
    val status: String?,
    val total: Double?,
    val createdAt: String?
)

@Dao
interface AppDao {
    @Query("SELECT * FROM products")
    suspend fun getAllProducts(): List<ProductEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProducts(products: List<ProductEntity>)

    @Query("DELETE FROM products")
    suspend fun clearProducts()

    @Query("SELECT * FROM orders")
    suspend fun getAllOrders(): List<OrderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrders(orders: List<OrderEntity>)

    @Query("DELETE FROM orders")
    suspend fun clearOrders()
}

@Database(entities = [ProductEntity::class, OrderEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao

    companion object {
        @Volatile private var instance: AppDatabase? = null
        fun getDatabase(context: android.content.Context): AppDatabase {
            return instance ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java, "pos_customer_cache.db"
                ).build().also { instance = it }
            }
        }
    }
}
