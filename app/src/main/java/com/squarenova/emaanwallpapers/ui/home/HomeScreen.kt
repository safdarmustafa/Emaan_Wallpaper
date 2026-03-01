package com.squarenova.emaanwallpapers.ui.home
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.squarenova.emaanwallpapers.data.DataStoreManager
import com.squarenova.emaanwallpapers.data.model.User
import com.squarenova.emaanwallpapers.network.FirebaseClient
import kotlinx.coroutines.flow.firstOrNull

data class Wallpaper(
    val url: String,
    val category: String
)

@Composable
fun HomeScreen(navController: NavController) {

    val context = LocalContext.current
    val dataStoreManager = DataStoreManager(context)

    var user by remember { mutableStateOf<User?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    // 🔥 FIREBASE FETCH (UNCHANGED)
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
        "Kaaba", "Madinah", "Quran", "Mosque",
        "Islamic Quotes", "Ramadan", "Allah"
    )

    var selectedCategory by remember { mutableStateOf("Kaaba") }

    val wallpapers = listOf(
        Wallpaper("https://picsum.photos/600/900", "Kaaba"),
        Wallpaper("https://picsum.photos/500/800", "Kaaba"),
        Wallpaper("https://picsum.photos/600/1000", "Madinah"),
        Wallpaper("https://picsum.photos/400/700", "Quran"),
        Wallpaper("https://picsum.photos/700/900", "Allah"),
        Wallpaper("https://picsum.photos/500/750", "Ramadan")
    )

    val gradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF064E3B),
            Color(0xFF0F766E)
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF3F6F5))
    ) {

        // 🌿 PREMIUM HEADER
        Surface(
            shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .shadow(8.dp),
            color = Color.Transparent
        ) {
            Box(
                modifier = Modifier
                    .background(gradient)
                    .padding(horizontal = 20.dp, vertical = 28.dp)
            ) {

                if (!isLoading && user != null) {
                    Column(
                        modifier = Modifier.align(Alignment.CenterStart)
                    ) {
                        Text(
                            text = "Assalamu Alaikum",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 13.sp
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = user!!.first_name,
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = Color(0xFFD4AF37),
                    modifier = Modifier
                        .size(52.dp)
                        .align(Alignment.CenterEnd),
                    tonalElevation = 6.dp,
                    onClick = { navController.navigate("profile") }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = user?.first_name?.firstOrNull()?.toString() ?: "",
                            color = Color.Black,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 🔥 CATEGORY ROW (ELEGANT STYLE)
        LazyRow(
            modifier = Modifier.padding(start = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(categories.size) { index ->
                val category = categories[index]

                val selected = selectedCategory == category

                FilterChip(
                    selected = selected,
                    onClick = { selectedCategory = category },
                    label = {
                        Text(
                            category,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF064E3B),
                        selectedLabelColor = Color.White,
                        containerColor = Color.White
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 🔥 WALLPAPER GRID (Pinterest Feel)
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.fillMaxSize()
        ) {

            items(
                wallpapers.filter { it.category == selectedCategory }
            ) { wallpaper: Wallpaper ->

                Card(
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {

                    Column {

                        AsyncImage(
                            model = wallpaper.url,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(420.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {

                            Text(
                                text = selectedCategory,
                                fontSize = 14.sp,
                                color = Color(0xFF064E3B)
                            )

                            Text(
                                text = "♡ Save",
                                fontSize = 14.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        }
    }
}