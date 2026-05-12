package com.example.lura.data.remote

import retrofit2.http.GET
import retrofit2.http.Path

interface LuraBackendApi {
    @GET("api/v1/categories")
    suspend fun getCategories(): List<CategoryResponseDto>

    @GET("api/v1/sounds")
    suspend fun getSounds(): List<SoundResponseDto>

    @GET("api/v1/sounds/category/{categoryId}")
    suspend fun getSoundsByCategory(
        @Path("categoryId") categoryId: String
    ): List<SoundResponseDto>

    @GET("api/v1/sounds/{soundId}/play")
    suspend fun getSoundPlayUrl(
        @Path("soundId") soundId: String
    ): SoundPlayResponseDto
}
