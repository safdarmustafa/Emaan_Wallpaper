package com.squarenova.emaanwallpapers.data.model

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val phone_number: String,
    val first_name: String,
    val last_name: String
)