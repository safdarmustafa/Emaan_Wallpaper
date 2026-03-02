package com.squarenova.emaanwallpapers.ui.home

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.squarenova.emaanwallpapers.data.DataStoreManager
import com.squarenova.emaanwallpapers.data.model.User
import com.squarenova.emaanwallpapers.network.FirebaseClient
import kotlinx.coroutines.flow.firstOrNull

@Composable
fun HomeScreen(navController: NavController) {

    val context = LocalContext.current
    val dataStoreManager = DataStoreManager(context)
    val configuration = LocalConfiguration.current

    // 📐 Perfect 9:16 ratio
    val screenWidth = configuration.screenWidthDp.dp
    val cardWidth = screenWidth - 24.dp
    val cardHeight = cardWidth * (16f / 9f)

    var user by remember { mutableStateOf<User?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var wallpaperList by remember { mutableStateOf<List<String>>(emptyList()) }
    var isWallpaperLoading by remember { mutableStateOf(true) }

    // 🔥 USER FETCH
    LaunchedEffect(Unit) {
        try {
            val phone = dataStoreManager.phoneNumber.firstOrNull()
            if (!phone.isNullOrEmpty()) {
                FirebaseClient.db
                    .collection("users")
                    .document(phone)
                    .get()
                    .addOnSuccessListener { document ->
                        if (document.exists()) {
                            user = User(
                                phone_number = document.getString("phone_number") ?: "",
                                first_name = document.getString("first_name") ?: "",
                                last_name = document.getString("last_name") ?: "",
                                age = document.getLong("age")?.toInt() ?: 0,
                                country = document.getString("country") ?: "",
                                city = document.getString("city") ?: "",
                                gender = document.getString("gender") ?: ""
                            )
                        }
                        isLoading = false
                    }
                    .addOnFailureListener {
                        Log.e("HOME_ERROR", it.message ?: "Unknown error")
                        isLoading = false
                    }
            } else {
                isLoading = false
            }
        } catch (e: Exception) {
            Log.e("HOME_ERROR", e.message ?: "Unknown error")
            isLoading = false
        }
    }

    val categories = listOf(
        "kaaba", "Madinah", "Quran", "Mosque",
        "Islamic Quotes", "Ramadan", "Allah"
    )
    var selectedCategory by remember { mutableStateOf("kaaba") }

    // 🔥 FETCH WALLPAPERS
    LaunchedEffect(selectedCategory) {
        isWallpaperLoading = true
        FirebaseClient.db
            .collection("Wallpapers")
            .document(selectedCategory)
            .get()
            .addOnSuccessListener { document ->
                val images = document.get("images") as? List<String>
                wallpaperList = images ?: emptyList()
                isWallpaperLoading = false
            }
            .addOnFailureListener {
                wallpaperList = emptyList()
                isWallpaperLoading = false
            }
    }

    val gradient = Brush.verticalGradient(
        colors = listOf(Color(0xFF064E3B), Color(0xFF0F766E))
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
    ) {

        // ✅ HEADER — statusBarsPadding() fixes overlap with battery/wifi icons
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .background(brush = gradient)
                .statusBarsPadding()  // ✅ KEY FIX — respects status bar height
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            // Left: Greeting + Name
            Column(
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Text(
                    text = "Assalamu Alaikum 🌙",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (!isLoading) user?.first_name ?: "Guest" else "",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Right: Avatar circle → profile
            IconButton(
                onClick = { navController.navigate("profile") },
                modifier = Modifier
                    .size(46.dp)
                    .align(Alignment.CenterEnd)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFD4AF37),
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = user?.first_name?.firstOrNull()
                                ?.uppercaseChar()?.toString() ?: "?",
                            color = Color.Black,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // ✅ CATEGORY ROW
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(vertical = 8.dp),
            contentPadding = PaddingValues(horizontal = 10.dp)
        ) {
            items(categories) { category ->
                FilterChip(
                    selected = selectedCategory == category,
                    onClick = { selectedCategory = category },
                    label = {
                        Text(
                            text = category.replaceFirstChar { it.uppercase() },
                            fontSize = 12.sp
                        )
                    },
                    modifier = Modifier.padding(horizontal = 4.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF064E3B),
                        selectedLabelColor = Color.White,
                        containerColor = Color(0xFFF0F0F0),
                        labelColor = Color.Black
                    )
                )
            }
        }

        // ✅ 9:16 WALLPAPER FEED
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 20.dp)
        ) {
            if (isWallpaperLoading) {
                items(3) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(cardHeight),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color(0xFF064E3B))
                    }
                }
            } else {
                items(wallpaperList) { url ->
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                    ) {
                        AsyncImage(
                            model = url,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(cardHeight) // ✅ Perfect 9:16
                        )
                    }
                }
            }
        }
    }
}