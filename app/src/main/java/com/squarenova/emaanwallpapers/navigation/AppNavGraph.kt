package com.squarenova.emaanwallpapers.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.squarenova.emaanwallpapers.ui.auth.LoginScreen
import com.squarenova.emaanwallpapers.ui.auth.OtpScreen
import com.squarenova.emaanwallpapers.ui.auth.ProfileSetupScreen
import com.squarenova.emaanwallpapers.ui.components.FloatingBottomBar
import com.squarenova.emaanwallpapers.ui.home.HomeScreen
import com.squarenova.emaanwallpapers.ui.legal.ContactUsScreen
import com.squarenova.emaanwallpapers.ui.profile.ProfileScreen
import com.squarenova.emaanwallpapers.ui.reels.ReelsScreen
import com.squarenova.emaanwallpapers.ui.ringtone.RingtoneScreen
import com.squarenova.emaanwallpapers.ui.splash.SplashScreen
import com.squarenova.emaanwallpapers.ui.subscription.SubscriptionScreen

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = showsBottomBar(currentRoute)

    Box(modifier = Modifier.fillMaxSize()) {
        // No NavHost enter/exit transitions — keeps ActivityResultRegistry owners stable
        // for ProfileScreen's gallery launcher (stretch fix is via stable NavHost padding).
        NavHost(
            navController = navController,
            startDestination = "splash",
            modifier = Modifier.fillMaxSize(),
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
            composable("contact_us") { ContactUsScreen(navController) }
            composable("profile_setup") { ProfileSetupScreen(navController) }
            composable("subscription") { SubscriptionScreen(navController) }
            composable("home") { HomeScreen(navController) }
            composable("reels") { ReelsScreen(navController) }
            composable("ringtone") {
                RingtoneScreen()
            }
            composable("profile") { ProfileScreen(navController) }
        }

        AnimatedVisibility(
            visible = showBottomBar,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(animationSpec = tween(180)) +
                slideInVertically(animationSpec = tween(180)) { it / 2 },
            exit = fadeOut(animationSpec = tween(140)) +
                slideOutVertically(animationSpec = tween(140)) { it / 2 },
        ) {
            FloatingBottomBar(
                currentRoute = currentRoute,
                navController = navController,
            )
        }
    }
}
