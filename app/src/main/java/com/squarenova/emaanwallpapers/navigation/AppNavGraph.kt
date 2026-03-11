package com.squarenova.emaanwallpapers.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.squarenova.emaanwallpapers.ui.auth.LoginScreen
import com.squarenova.emaanwallpapers.ui.auth.OtpScreen
import com.squarenova.emaanwallpapers.ui.auth.ProfileSetupScreen
import com.squarenova.emaanwallpapers.ui.home.HomeScreen
import com.squarenova.emaanwallpapers.ui.profile.ProfileScreen
import com.squarenova.emaanwallpapers.ui.reels.ReelsScreen
import com.squarenova.emaanwallpapers.ui.splash.SplashScreen

import com.squarenova.emaanwallpapers.ui.subscription.SubscriptionScreen

private val bottomNavScreens = listOf("home", "reels")

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = currentRoute in bottomNavScreens

    Box(modifier = Modifier.fillMaxSize()) {

        NavHost(
            navController = navController,
            startDestination = "splash",
            // ✅ Add bottom padding so content isn't hidden behind nav bar
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = if (showBottomBar) 56.dp else 0.dp)
        ) {
            composable("splash") { SplashScreen(navController) }
            composable("login") { LoginScreen(navController) }
            composable(
                "otp/{otp}/{phone}",
                arguments = listOf(
                    navArgument("otp") { type = NavType.StringType },
                    navArgument("phone") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                OtpScreen(
                    navController = navController,
                    sentOtp = backStackEntry.arguments?.getString("otp") ?: "",
                    phone = backStackEntry.arguments?.getString("phone") ?: ""
                )
            }
            composable("profile_setup") { ProfileSetupScreen(navController) }
            composable("subscription") { SubscriptionScreen(navController) }
            composable("home") { HomeScreen(navController) }
            composable("reels") { ReelsScreen() }
            composable("profile") { ProfileScreen(navController) }
        }

        // ✅ Bottom nav bar pinned to bottom
        if (showBottomBar) {
            BottomNavBar(
                currentRoute = currentRoute,
                navController = navController,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

data class BottomNavItem(val route: String, val emoji: String, val label: String)

@Composable
fun BottomNavBar(
    currentRoute: String?,
    navController: NavController,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        BottomNavItem("home", "🕌", "Wallpapers"),
        BottomNavItem("reels", "🎬", "Reels")
    )
    val goldColor = Color(0xFFD4AF37)

    // ✅ Fixed compact height — 56dp only, no extra padding
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF0A3528))
            .navigationBarsPadding()
            .height(56.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { item ->
            val isSelected = currentRoute == item.route
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable {
                        if (currentRoute != item.route) {
                            navController.navigate(item.route) {
                                popUpTo("home") { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
            ) {
                Text(
                    text = item.emoji,
                    fontSize = if (isSelected) 22.sp else 20.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.label,
                    color = if (isSelected) goldColor else Color.White.copy(alpha = 0.5f),
                    fontSize = 10.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
                if (isSelected) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .width(16.dp)
                            .height(2.dp)
                            .clip(RoundedCornerShape(50.dp))
                            .background(goldColor)
                    )
                }
            }
        }
    }
}