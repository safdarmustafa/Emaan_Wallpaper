package com.squarenova.emaanwallpapers.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.squarenova.emaanwallpapers.ui.splash.SplashScreen
import com.squarenova.emaanwallpapers.ui.auth.LoginScreen
import com.squarenova.emaanwallpapers.ui.auth.OtpScreen
import com.squarenova.emaanwallpapers.ui.auth.ProfileSetupScreen
import com.squarenova.emaanwallpapers.ui.home.HomeScreen
import com.squarenova.emaanwallpapers.ui.profile.ProfileScreen

@Composable
fun AppNavGraph(navController: NavHostController) {

    NavHost(
        navController = navController,
        startDestination = "splash"
    ) {

        composable("splash") { SplashScreen(navController) }
        composable("login") { LoginScreen(navController) }

        composable("otp/{otp}/{phone}") { backStackEntry ->
            val otp = backStackEntry.arguments?.getString("otp") ?: ""
            val phone = backStackEntry.arguments?.getString("phone") ?: ""
            OtpScreen(navController, otp, phone)
        }

        composable("profile_setup") { ProfileSetupScreen(navController) }
        composable("home") { HomeScreen(navController) }
        composable("profile") { ProfileScreen(navController) }
    }
}