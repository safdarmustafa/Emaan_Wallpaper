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
        @Field("message") message: String,
        @Field("numbers") numbers: String,
        @Field("sender_id") senderId: String,
        @Field("pe_id") peId: String,
        @Field("template_id") templateId: String,
        @Field("route") route: String = "q",
        @Field("language") language: String = "english"
    ): Response<Any>
}