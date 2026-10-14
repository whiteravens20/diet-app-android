// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

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
 * Offline cache. The first page of a profile's plans, the plans opened, and
 * shopping lists are stored as the raw JSON the API returned, so the UI can
 * render them without a network connection. `syncedAt` says how old a copy is.
 * The cache holds nothing the server does not, so a version of the app that
 * changes its tables starts it empty (see `AppModule.database`).
 */

/** The first page of a profile's plans, as `GET /meal-plans` answered it. */
@Entity(tableName = "cached_plan_list")
data class CachedPlanList(
    @PrimaryKey val profileId: String,
    val json: String,
    val syncedAt: Long,
)

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
    suspend fun upsertPlanList(list: CachedPlanList)

    @Query("SELECT * FROM cached_plan_list WHERE profileId = :profileId")
    suspend fun planList(profileId: String): CachedPlanList?

    @Query("DELETE FROM cached_plan_list WHERE profileId = :profileId")
    suspend fun deletePlanList(profileId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMealPlan(plan: CachedMealPlan)

    @Query("SELECT * FROM cached_meal_plan WHERE id = :id")
    suspend fun mealPlan(id: String): CachedMealPlan?

    @Query("DELETE FROM cached_meal_plan WHERE id = :id")
    suspend fun deleteMealPlan(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertShoppingList(list: CachedShoppingList)

    @Query("SELECT * FROM cached_shopping_list WHERE id = :id")
    suspend fun shoppingList(id: String): CachedShoppingList?

    @Query("SELECT * FROM cached_shopping_list WHERE planId = :planId ORDER BY syncedAt DESC")
    suspend fun shoppingListsForPlan(planId: String): List<CachedShoppingList>

    @Query("DELETE FROM cached_shopping_list WHERE id = :id")
    suspend fun deleteShoppingList(id: String)
}

@Database(
    entities = [CachedPlanList::class, CachedMealPlan::class, CachedShoppingList::class],
    version = 2,
    exportSchema = false,
)
abstract class CacheDatabase : RoomDatabase() {
    abstract fun cacheDao(): CacheDao
}
