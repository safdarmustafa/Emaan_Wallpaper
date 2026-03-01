package com.squarenova.emaanwallpapers.network

import com.google.firebase.firestore.FirebaseFirestore

object FirebaseClient {
    val db: FirebaseFirestore = FirebaseFirestore.getInstance()
}