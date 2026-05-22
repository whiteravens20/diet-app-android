package net.whiteravens.dietapp.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit binding for the Diet App API. Endpoints and payload shapes mirror the
 * web platform's contract — see docs/contracts/api-contract.md. The DTOs below
 * are the Kotlin equivalents of `packages/shared` in the web repo.
 */
interface DietApiService {

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): AuthResponse

    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequest): AuthResponse

    @GET("profiles")
    suspend fun profiles(): List<ProfileDto>

    @GET("profiles/{id}/calories")
    suspend fun calories(@Path("id") profileId: String): CalorieCalculationDto

    @GET("meal-plans")
    suspend fun mealPlans(@Query("profileId") profileId: String): List<MealPlanDto>

    @GET("meal-plans/{id}")
    suspend fun mealPlan(@Path("id") id: String): MealPlanDto

    @GET("shopping-lists/{id}")
    suspend fun shoppingList(@Path("id") id: String): ShoppingListDto
}
