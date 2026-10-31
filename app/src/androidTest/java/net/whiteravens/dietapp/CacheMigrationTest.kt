// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.whiteravens.dietapp.data.local.CacheDatabase
import net.whiteravens.dietapp.data.local.CachedDiaryDay
import net.whiteravens.dietapp.data.local.CachedProfile
import net.whiteravens.dietapp.data.local.PendingChangeRow
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** The cache as an update of the app finds it on the phone: a cache of the version before, migrated with what it holds. */
@RunWith(AndroidJUnit4::class)
class CacheMigrationTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "cache-migration-test.db"

    @After
    fun forget() {
        context.deleteDatabase(name)
    }

    /**
     * Makes the file a version of the app left: its tables and Room's own record of them, as the
     * schema Room wrote for [version] has them, holding what [fill] puts in.
     */
    private fun leftBy(version: Int, fill: (SupportSQLiteDatabase) -> Unit) {
        val schema = InstrumentationRegistry.getInstrumentation().context.assets
            .open("${CacheDatabase::class.java.name}/$version.json")
            .bufferedReader()
            .use { JSONObject(it.readText()) }
            .getJSONObject("database")
        val callback = object : SupportSQLiteOpenHelper.Callback(version) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                val entities = schema.getJSONArray("entities")
                for (i in 0 until entities.length()) {
                    val entity = entities.getJSONObject(i)
                    db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                }
                val setup = schema.getJSONArray("setupQueries")
                for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
                fill(db)
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(callback).build()
        FrameworkSQLiteOpenHelperFactory().create(configuration).use { it.writableDatabase }
    }

    @Test
    fun aCacheOfTheVersionBeforeKeepsItsCopiesAndGainsAnEmptyQueueOfChanges() {
        leftBy(2) { db ->
            db.execSQL("INSERT INTO cached_meal_plan (id, profileId, json, syncedAt) VALUES ('plan', 'profile', '{\"id\":\"plan\"}', 1)")
        }

        val cache = CacheDatabase.open(context, name)
        try {
            runBlocking {
                assertEquals("{\"id\":\"plan\"}", cache.cacheDao().mealPlan("plan")?.json)
                assertEquals(emptyList<PendingChangeRow>(), cache.pendingChanges().waiting().first())

                cache.pendingChanges().add(PendingChangeRow(kind = "eaten", change = "{}", madeAt = 2))

                assertEquals(listOf("eaten"), cache.pendingChanges().waiting().first().map { it.kind })
            }
        } finally {
            cache.close()
        }
    }

    @Test
    fun aCacheFromBeforeTheDiaryKeepsItsChangesWaitingAndGainsEmptyCopiesOfDaysAndProfiles() {
        leftBy(3) { db ->
            db.execSQL("INSERT INTO cached_meal_plan (id, profileId, json, syncedAt) VALUES ('plan', 'profile', '{\"id\":\"plan\"}', 1)")
            db.execSQL("INSERT INTO pending_change (kind, change, madeAt) VALUES ('eaten', '{}', 2)")
        }

        val cache = CacheDatabase.open(context, name)
        try {
            runBlocking {
                assertEquals("{\"id\":\"plan\"}", cache.cacheDao().mealPlan("plan")?.json)
                assertEquals(listOf("eaten"), cache.pendingChanges().waiting().first().map { it.kind })
                assertEquals(emptyList<CachedProfile>(), cache.keptReads().profiles())

                cache.keptReads().upsertDiaryDay(CachedDiaryDay("profile", "2026-10-31", "{}", 3))
                cache.keptReads().replaceProfiles(listOf(CachedProfile("a", 0, "Ala", "balanced", 4, 70.0, null, 3)))
                cache.keptReads().replaceProfiles(listOf(CachedProfile("b", 0, "Bo", "balanced", 4, 70.0, null, 4)))

                assertEquals("{}", cache.keptReads().diaryDay("profile", "2026-10-31")?.json)
                assertEquals(listOf("b"), cache.keptReads().profiles().map { it.id })
            }
        } finally {
            cache.close()
        }
    }
}
