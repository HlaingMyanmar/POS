package com.sspd.servicemgmt.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sspd.servicemgmt.ui.theme.*
import com.sspd.servicemgmt.ui.components.AppLoading
import com.sspd.servicemgmt.ui.viewmodel.SalesRankingViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesRankingScreen(onBack: () -> Unit) {
    val vm: SalesRankingViewModel = viewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        while (true) { vm.load(); delay(30_000) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ရောင်းအကောင်းဆုံး", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "နောက်ပြန်", tint = Color.White) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Primary, titleContentColor = Color.White)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).background(ScreenBg)) {
            if (state.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { AppLoading() }
            } else if (state.items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("ဒေတာမရှိပါ", color = TextMuted) }
            } else {
                LazyColumn(contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    itemsIndexed(state.items) { idx, item ->
                        val medal = when (idx) {
                            0 -> "🥇"
                            1 -> "🥈"
                            2 -> "🥉"
                            else -> null
                        }
                        val rankBg = when (idx) {
                            0 -> Color(0xFFFFFBEB)
                            1 -> Color(0xFFF8FAFC)
                            2 -> Color(0xFFFFF7ED)
                            else -> CardBg
                        }
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = rankBg),
                            border = BorderStroke(if (idx < 3) 1.5.dp else 1.dp, if (idx < 3) Color(0xFFD97706) else BorderColor),
                            elevation = CardDefaults.cardElevation(if (idx == 0) 4.dp else 1.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (medal != null) {
                                    Text(medal, fontSize = 28.sp)
                                } else {
                                    Box(
                                        modifier = Modifier.size(36.dp).clip(CircleShape).background(BorderColor),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("${idx + 1}", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = TextMuted)
                                    }
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(item.staffName ?: "-", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = TextMain)
                                    Text("${item.salesCount ?: 0} ကြိမ်", fontSize = 12.sp, color = TextMuted)
                                }
                                Text("${(item.totalAmount ?: 0).fmt()} Ks", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Primary)
                            }
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

private fun Long.fmt() = String.format("%,d", this)

