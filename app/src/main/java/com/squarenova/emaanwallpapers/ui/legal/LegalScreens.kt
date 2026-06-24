package com.squarenova.emaanwallpapers.ui.legal

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.squarenova.emaanwallpapers.theme.AppTextPrimary
import com.squarenova.emaanwallpapers.theme.AppTextSecondary
import com.squarenova.emaanwallpapers.theme.BackgroundCream
import com.squarenova.emaanwallpapers.theme.ProfileCardSurface
import com.squarenova.emaanwallpapers.theme.ProfileEmerald

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalDocumentScreen(
    navController: NavController,
    title: String,
    sections: List<LegalSection>,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ProfileCardSurface,
                    titleContentColor = AppTextPrimary,
                ),
            )
        },
        containerColor = BackgroundCream,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            sections.forEach { section ->
                LegalSectionCard(section)
                Spacer(modifier = Modifier.height(12.dp))
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun LegalSectionCard(section: LegalSection) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = ProfileCardSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = section.heading,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = ProfileEmerald,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = section.body,
                style = MaterialTheme.typography.bodyMedium,
                color = AppTextSecondary,
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight,
            )
        }
    }
}

data class LegalSection(val heading: String, val body: String)

@Composable
fun PrivacyPolicyScreen(navController: NavController) {
    LegalDocumentScreen(
        navController = navController,
        title = "Privacy Policy",
        sections = LegalContent.privacyPolicy,
    )
}

@Composable
fun TermsAndConditionsScreen(navController: NavController) {
    LegalDocumentScreen(
        navController = navController,
        title = "Terms & Conditions",
        sections = LegalContent.termsAndConditions,
    )
}

@Composable
fun SubscriptionDisclosureScreen(navController: NavController) {
    LegalDocumentScreen(
        navController = navController,
        title = "Subscription Terms",
        sections = LegalContent.subscriptionDisclosure,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactUsScreen(navController: NavController) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Contact Us", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ProfileCardSurface),
            )
        },
        containerColor = BackgroundCream,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
        ) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = ProfileCardSurface),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        "We're here to help",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = AppTextPrimary,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        LegalContent.contactIntro,
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppTextSecondary,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Email", fontWeight = FontWeight.SemiBold, color = ProfileEmerald)
                    Text(LegalContent.supportEmail, color = AppTextPrimary)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("mailto:${LegalContent.supportEmail}")
                        putExtra(Intent.EXTRA_SUBJECT, "Emaan Wallpapers Support")
                    }
                    context.startActivity(Intent.createChooser(intent, "Contact support"))
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ProfileEmerald),
            ) {
                Text("Email Support", color = Color.White)
            }
        }
    }
}

@Composable
fun DeleteAccountScreen(navController: NavController) {
    LegalDocumentScreen(
        navController = navController,
        title = "Delete Account",
        sections = LegalContent.deleteAccount,
    )
}
