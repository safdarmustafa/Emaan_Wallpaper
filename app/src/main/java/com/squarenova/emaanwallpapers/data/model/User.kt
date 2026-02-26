package com.squarenova.emaanwallpapers.data.model

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val phone_number: String,
    val first_name: String,
    val last_name: String,
    val age: Int,
    val country: String,
    val city: String,
    val gender: String
)