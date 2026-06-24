package com.squarenova.emaanwallpapers.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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

import com.squarenova.emaanwallpapers.ui.components.FloatingBottomBar
import com.squarenova.emaanwallpapers.ui.subscription.SubscriptionScreen
import com.squarenova.emaanwallpapers.ui.legal.ContactUsScreen
import com.squarenova.emaanwallpapers.ui.legal.DeleteAccountScreen
import com.squarenova.emaanwallpapers.ui.legal.PrivacyPolicyScreen
import com.squarenova.emaanwallpapers.ui.legal.SubscriptionDisclosureScreen
import com.squarenova.emaanwallpapers.ui.legal.TermsAndConditionsScreen

private val bottomNavScreens = listOf("home", "reels")

// Matches floating bar: nav inset + bottom padding + bar row + gap above bar
private val bottomBarInsetDp = 90.dp

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
                .padding(bottom = if (showBottomBar) bottomBarInsetDp else 0.dp)
        ) {
            composable("splash") { SplashScreen(navController) }
            composable("login") { LoginScreen(navController) }
            composable(
                "otp/{phone}",
                arguments = listOf(
                    navArgument("phone") { type = NavType.StringType },
                ),
            ) { backStackEntry ->
                OtpScreen(
                    navController = navController,
                    phone = backStackEntry.arguments?.getString("phone") ?: "",
                )
            }
            composable("privacy_policy") { PrivacyPolicyScreen(navController) }
            composable("terms") { TermsAndConditionsScreen(navController) }
            composable("contact_us") { ContactUsScreen(navController) }
            composable("subscription_disclosure") { SubscriptionDisclosureScreen(navController) }
            composable("delete_account_info") { DeleteAccountScreen(navController) }
            composable("profile_setup") { ProfileSetupScreen(navController) }
            composable("subscription") { SubscriptionScreen(navController) }
            composable("home") { HomeScreen(navController) }
            composable("reels") { ReelsScreen(navController) }
            composable("profile") { ProfileScreen(navController) }
        }

        // ✅ Bottom nav bar pinned to bottom
        if (showBottomBar) {
            FloatingBottomBar(
                currentRoute = currentRoute,
                navController = navController,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}
