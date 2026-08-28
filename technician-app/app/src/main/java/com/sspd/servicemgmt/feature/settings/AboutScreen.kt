package com.sspd.servicemgmt.feature.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sspd.servicemgmt.BuildConfig
import com.sspd.servicemgmt.R
import com.sspd.servicemgmt.core.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("အကြောင်းအရာ", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "နောက်ပြန်", tint = Color.White) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Primary, titleContentColor = Color.White)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).background(ScreenBg),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(40.dp))

            // Logo
            Surface(
                modifier = Modifier.size(92.dp),
                shape = RoundedCornerShape(24.dp),
                color = Color.White,
                border = BorderStroke(1.dp, BorderColor),
                shadowElevation = 8.dp
            ) {
                Image(
                    painter = painterResource(R.drawable.logo),
                    contentDescription = "SSPD logo",
                    modifier = Modifier.padding(12.dp)
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(BuildConfig.APP_DISPLAY_NAME, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = TextMain)
            Text("Version ${BuildConfig.VERSION_NAME}", fontSize = 13.sp, color = TextMuted)
            Spacer(Modifier.height(8.dp))
            Text("Computer • Network • Technical Support", fontSize = 14.sp, color = Primary, fontWeight = FontWeight.SemiBold)

            Spacer(Modifier.height(40.dp))

            // Info items
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                border = BorderStroke(1.dp, BorderColor)
            ) {
                Column {
                    AboutRow(Icons.Outlined.Business, "ကုမ္ပဏီ", "SSPD IT Solution Center")
                    HorizontalDivider(color = BorderColor)
                    AboutRow(Icons.Outlined.Code, "Technology", "Kotlin + Jetpack Compose")
                    HorizontalDivider(color = BorderColor)
                    AboutRow(Icons.Outlined.Api, "Backend", "Spring Boot REST API")
                    HorizontalDivider(color = BorderColor)
                    AboutRow(Icons.Outlined.DataObject, "Database", "MySQL")
                }
            }

            Spacer(Modifier.weight(1f))
            Text(
                "© 2026 SSPD IT Solution Center\nAll rights reserved.",
                fontSize = 11.sp, color = TextMuted, textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun AboutRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, null, tint = Primary, modifier = Modifier.size(20.dp))
        Text(label, fontSize = 13.sp, color = TextMuted, modifier = Modifier.weight(1f))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextMain)
    }
}
