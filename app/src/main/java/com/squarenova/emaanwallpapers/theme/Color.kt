package com.squarenova.emaanwallpapers.theme

import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

/** Brand */
val BrandGreen = Color(0xFF41AB5D)
val BrandGreenDark = Color(0xFF2D7A42)
val BrandGreenMuted = Color(0xFF5A9D6E)

/**
 * App-wide neutrals (Material / Play-style: light surfaces, green as accent only).
 */
val AppBackground = Color(0xFFF5F8F7)
val AppSurface = Color(0xFFFFFFFF)
val AppTextPrimary = Color(0xFF1A1C1E)
val AppTextSecondary = Color(0xFF5F6368)
val AppTextTertiary = Color(0xFF80868B)
val AppDivider = Color(0xFFE8EBE9)
val AppScrim = BrandGreen.copy(alpha = 0.08f)

/** Home — aliases (minimal green: mostly white + gray, green for selection/accents) */
val HomeBackground = AppBackground
val HomeSurfaceStrip = AppSurface
val HomeTopBarSurface = AppSurface
val HomeTextBlack = AppTextPrimary
val HomeChipIdle = Color(0xFFF0F4F2)
val HomeChipSelected = BrandGreen
val HomeFilterIconIdle = Color(0xFFE8ECEA)
val HomeFloatingBarSurface = AppSurface
