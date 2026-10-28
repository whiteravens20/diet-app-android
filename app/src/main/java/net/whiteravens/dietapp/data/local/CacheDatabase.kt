// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.local

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Offline cache, and the changes made on the phone that wait to be sent. The first page of a
 * profile's plans, the plans opened, and shopping lists are stored as the raw JSON the API
 * returned, so the UI can render them without a network connection. `syncedAt` says how old a copy
 * is. A change waiting is the one thing here the server does not have yet, so a new version of the
 * tables migrates them from the one before (see [CacheDatabase.open]); all of it goes when the
 * session ends.
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

/**
 * A change made on the phone that the server does not have yet, in the order it was made ([id]):
 * what kind it is, and the change itself as JSON, which holds the key an addition is sent with
 * every time. A change the server refused is not sent again: it stays, with the code it was
 * refused with and the server's own words, until the user has seen why.
 */
@Entity(tableName = "pending_change")
data class PendingChangeRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val change: String,
    val madeAt: Long,
    val refusedCode: String? = null,
    val refusedMessage: String? = null,
)

@Dao
interface PendingChangeDao {
    @Insert
    suspend fun add(row: PendingChangeRow): Long

    /** The changes waiting to be sent, the oldest first, as they come and go. */
    @Query("SELECT * FROM pending_change WHERE refusedCode IS NULL ORDER BY id")
    fun waiting(): Flow<List<PendingChangeRow>>

    /** The changes the server refused, the oldest first, until each is seen. */
    @Query("SELECT * FROM pending_change WHERE refusedCode IS NOT NULL ORDER BY id")
    fun refused(): Flow<List<PendingChangeRow>>

    /** The oldest change waiting: the one to send next. */
    @Query("SELECT * FROM pending_change WHERE refusedCode IS NULL ORDER BY id LIMIT 1")
    suspend fun next(): PendingChangeRow?

    /** Whether a change waits to be sent. */
    @Query("SELECT EXISTS(SELECT 1 FROM pending_change WHERE refusedCode IS NULL)")
    suspend fun anyWaiting(): Boolean

    @Query("UPDATE pending_change SET refusedCode = :code, refusedMessage = :message WHERE id = :id")
    suspend fun refuse(id: Long, code: String, message: String?)

    @Query("DELETE FROM pending_change WHERE id = :id")
    suspend fun remove(id: Long)

    /** Takes away a change that still waits: how many it took away, none where it was sent or refused meanwhile. */
    @Query("DELETE FROM pending_change WHERE id = :id AND refusedCode IS NULL")
    suspend fun removeWaiting(id: Long): Int

    @Query("DELETE FROM pending_change")
    suspend fun removeAll()
}

@Database(
    entities = [CachedPlanList::class, CachedMealPlan::class, CachedShoppingList::class, PendingChangeRow::class],
    version = 3,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 2, to = 3)],
)
abstract class CacheDatabase : RoomDatabase() {
    abstract fun cacheDao(): CacheDao

    abstract fun pendingChanges(): PendingChangeDao

    companion object {
        /** The file the app keeps it in. */
        const val NAME = "diet-app-cache.db"

        /**
         * The database in the file [name], its tables migrated from the version before. A version
         * from before the changes waiting were kept, and a newer one an older app finds, held copies
         * alone: those start empty.
         */
        fun open(context: Context, name: String = NAME): CacheDatabase =
            Room.databaseBuilder(context, CacheDatabase::class.java, name)
                .fallbackToDestructiveMigrationFrom(dropAllTables = true, 1)
                .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                .build()
    }
}
