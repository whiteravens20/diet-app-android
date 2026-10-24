// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.domain

/** kcal in 1 g of protein, fat and carbohydrate (Atwater factors), as the API splits a target into grams. */
private val KCAL_PER_GRAM = listOf(4.0, 9.0, 4.0)

/** The energy that grams of protein, fat and carbohydrate give, in that order. */
fun energyOf(grams: List<Double>): List<Double> = grams.zip(KCAL_PER_GRAM) { g, kcal -> g * kcal }
