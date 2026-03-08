package com.squarenova.emaanwallpapers.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.squarenova.emaanwallpapers.analytics.AnalyticsManager
import com.squarenova.emaanwallpapers.ui.components.smoothClickable
import com.squarenova.emaanwallpapers.ui.splash.SplashScreen

private val bottomNavScreens = listOf("home", "reels")

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = currentRoute in bottomNavScreens

    // Screen view tracking — fires whenever route changes
    LaunchedEffect(currentRoute) {
        currentRoute?.let { route ->
            val screenName = when {
                route.startsWith("otp/") -> "OTP"
                else -> route.replaceFirstChar { it.uppercaseChar() }
            }
            AnalyticsManager.trackScreen(screenName)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        NavHost(
            navController = navController,
            startDestination = "splash",
            // ✅ Add bottom padding so content isn't hidden behind nav bar
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = if (showBottomBar) 52.dp else 0.dp)
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

data class BottomNavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

@Composable
fun BottomNavBar(
    currentRoute: String?,
    navController: NavController,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        BottomNavItem(
            route = "home",
            label = "Wallpapers",
            selectedIcon = Icons.Filled.Home,
            unselectedIcon = Icons.Filled.Home
        ),
        BottomNavItem(
            route = "reels",
            label = "Reels",
            selectedIcon = Icons.Filled.PlayArrow,
            unselectedIcon = Icons.Filled.PlayArrow
        )
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        color = Color(0xFF0A3528),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
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
                        .smoothClickable {
                            if (currentRoute != item.route) {
                                AnalyticsManager.trackEvent("Bottom Nav - ${item.label} Tapped")
                                navController.navigate(item.route) {
                                    popUpTo("home") { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                ) {
                    Icon(
                        imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = item.label,
                        tint = if (isSelected) Color(0xFFD4AF37) else Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.label,
                        color = if (isSelected) Color(0xFFD4AF37) else Color.White.copy(alpha = 0.5f),
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
    }
}