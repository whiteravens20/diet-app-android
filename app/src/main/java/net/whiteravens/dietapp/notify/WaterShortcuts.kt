// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.notify

import android.content.Context
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.WaterShortcutActivity
import net.whiteravens.dietapp.domain.WATER_AMOUNTS_ML
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The shortcuts that holding the application's icon offers while an account is signed in: the
 * amounts of the water card, each adding water without opening the application
 * ([WaterShortcutActivity]). Made anew whenever the signed-in application starts, in the phone's
 * language of the moment, and taken away with the session.
 */
@Singleton
class WaterShortcuts @Inject constructor(@ApplicationContext private val context: Context) {
    fun publish() {
        val shortcuts = WATER_AMOUNTS_ML.mapIndexed { rank, ml ->
            ShortcutInfoCompat.Builder(context, idOf(ml))
                .setShortLabel(context.getString(R.string.water_add_ml, ml))
                .setRank(rank)
                .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_water))
                .setIntent(WaterShortcutActivity.adding(context, ml))
                .build()
        }
        ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
    }

    fun remove() = ShortcutManagerCompat.removeAllDynamicShortcuts(context)

    /** Tells the launcher the shortcut of [ml] was used, which it ranks its shortcuts by. */
    fun used(ml: Int) = ShortcutManagerCompat.reportShortcutUsed(context, idOf(ml))

    private fun idOf(ml: Int) = "water-$ml"
}
