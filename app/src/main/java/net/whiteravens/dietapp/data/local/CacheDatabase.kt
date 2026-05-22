package net.whiteravens.dietapp.data.local

import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.RoomDatabase
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Offline cache. Meal plans and shopping lists are stored as the raw JSON the
 * API returned, so the UI can render them without a network connection. The
 * `syncedAt` timestamp drives staleness and the Phase-3 sync layer.
 */

@Entity(tableName = "cached_meal_plan")
data class CachedMealPlan(
    @PrimaryKey val id: String,
    val profileId: String,
    val json: String,
    val syncedAt: Long,
)

@Entity(tableName = "cached_shopping_list")
data class CachedShoppingList(
    @PrimaryKey val id: String,
    val planId: String,
    val json: String,
    val syncedAt: Long,
)

@Dao
interface CacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMealPlan(plan: CachedMealPlan)

    @Query("SELECT * FROM cached_meal_plan WHERE profileId = :profileId ORDER BY syncedAt DESC")
    suspend fun mealPlans(profileId: String): List<CachedMealPlan>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertShoppingList(list: CachedShoppingList)

    @Query("SELECT * FROM cached_shopping_list WHERE id = :id")
    suspend fun shoppingList(id: String): CachedShoppingList?
}

@Database(
    entities = [CachedMealPlan::class, CachedShoppingList::class],
    version = 1,
    exportSchema = true,
)
abstract class CacheDatabase : RoomDatabase() {
    abstract fun cacheDao(): CacheDao
}
