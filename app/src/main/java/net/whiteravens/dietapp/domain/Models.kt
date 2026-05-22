package net.whiteravens.dietapp.domain

/**
 * Domain models — the app's own representation, decoupled from wire DTOs.
 * Repositories map `data.remote.*Dto` into these. Kept minimal in the Phase-3
 * scaffold; expand alongside the screens that need them.
 */

data class Profile(val id: String, val name: String, val dietType: String, val mealCount: Int)

data class CalorieTarget(val maintenance: Int, val dailyTarget: Int, val source: String)
