package com.squarenova.emaanwallpapers.network

import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.Header
import retrofit2.http.POST

interface Fast2SmsApi {

    @FormUrlEncoded
    @POST("dev/bulkV2")
    suspend fun sendOtp(
        @Header("authorization") authorization: String,
        @Field("route") route: String = "q",
        @Field("message") message: String,
        @Field("language") language: String = "english",
        @Field("numbers") numbers: String
    ): Response<Any>
}