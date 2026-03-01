package com.squarenova.emaanwallpapers.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.squarenova.emaanwallpapers.data.DataStoreManager
import com.squarenova.emaanwallpapers.data.model.User
import com.squarenova.emaanwallpapers.network.FirebaseClient
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(navController: NavController) {

    val context = LocalContext.current
    val dataStoreManager = DataStoreManager(context)
    val scope = rememberCoroutineScope()

    var user by remember { mutableStateOf<User?>(null) }

    // 🔥 FETCH FROM FIREBASE
    LaunchedEffect(Unit) {

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
                }
        }
    }

    val headerGradient = Brush.verticalGradient(
        listOf(
            Color(0xFF1B5E20),
            Color(0xFF0D3B2E)
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D3B2E))
    ) {

        // 🔝 HEADER
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .background(headerGradient),
            contentAlignment = Alignment.TopCenter
        ) {

            IconButton(
                onClick = { navController.popBackStack() },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
            ) {
                Text("<", color = Color.White, fontSize = 22.sp)
            }

            IconButton(
                onClick = { },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                Text("⚙", fontSize = 20.sp)
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = 60.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Surface(
                    shape = CircleShape,
                    color = Color(0xFFD4AF37),
                    modifier = Modifier.size(120.dp),
                    tonalElevation = 8.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = user?.first_name?.firstOrNull()?.toString() ?: "",
                            fontSize = 36.sp,
                            color = Color.Black
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "${user?.first_name ?: ""} ${user?.last_name ?: ""}",
                    color = Color.White,
                    fontSize = 22.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Member of Emaan Wallpapers",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(80.dp))

        // 📊 Stats Card
        Card(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1F4F3D)
            )
        ) {
            Row(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Country", color = Color.White.copy(alpha = 0.7f))
                    Text(user?.country ?: "", color = Color.White)
                }

                Column {
                    Text("City", color = Color.White.copy(alpha = 0.7f))
                    Text(user?.city ?: "", color = Color.White)
                }

                Column {
                    Text("Age", color = Color.White.copy(alpha = 0.7f))
                    Text(user?.age?.toString() ?: "", color = Color.White)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Column(modifier = Modifier.padding(horizontal = 20.dp)) {

            ProfileItem("Edit Profile")
            ProfileItem("Upgrade to Premium")
            ProfileItem("Share App")

            // ✅ FIXED LOGOUT
            ProfileItem("Logout") {
                scope.launch {
                    dataStoreManager.logout()   // 🔥 CLEAR LOGIN DATA
                    navController.navigate("login") {
                        popUpTo("home") { inclusive = true }
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileItem(title: String, onClick: (() -> Unit)? = null) {

    Card(
        onClick = { onClick?.invoke() },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF154734)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(18.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title, color = Color.White)
            Text(">", color = Color.White.copy(alpha = 0.6f))
        }
    }
}