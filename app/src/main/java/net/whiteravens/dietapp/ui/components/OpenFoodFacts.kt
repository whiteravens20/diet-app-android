// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.components

import android.net.Uri

/** Open Food Facts, where anybody can add or correct a product for everybody. */
const val OPEN_FOOD_FACTS = "https://world.openfoodfacts.org"

/** The page of a product at Open Food Facts, where it is corrected. */
fun offProductPage(code: String): String = "$OPEN_FOOD_FACTS/product/${Uri.encode(code)}"
