package com.sspd.servicemgmt.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sspd.servicemgmt.ui.theme.*
import com.sspd.servicemgmt.ui.components.AppLoading
import com.sspd.servicemgmt.ui.viewmodel.AuditLogViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditLogScreen(onBack: () -> Unit) {
    val vm: AuditLogViewModel = viewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        while (true) { vm.load(); delay(30_000) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Audit မှတ်တမ်းများ", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "နောက်ပြန်", tint = Color.White) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Primary, titleContentColor = Color.White)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).background(ScreenBg)) {
            if (state.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { AppLoading() }
            } else if (state.items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("မှတ်တမ်း မရှိပါ", color = TextMuted) }
            } else {
                LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.items) { log ->
                        Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = CardBg), border = BorderStroke(1.dp, BorderColor)) {
                            Column(Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Surface(
                                        color = when {
                                            log.action?.contains("DELETE", true) == true -> DangerBg
                                            log.action?.contains("CREATE", true) == true -> SuccessBg
                                            log.action?.contains("UPDATE", true) == true -> WarningBg
                                            else -> PrimaryLight
                                        },
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            log.action ?: "-",
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                            fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                            color = when {
                                                log.action?.contains("DELETE", true) == true -> Danger
                                                log.action?.contains("CREATE", true) == true -> Success
                                                log.action?.contains("UPDATE", true) == true -> Warning
                                                else -> Primary
                                            }
                                        )
                                    }
                                    Text(log.module ?: "-", fontSize = 11.sp, color = TextMuted)
                                }
                                Spacer(Modifier.height(6.dp))
                                if (!log.description.isNullOrBlank()) {
                                    Text(log.description, fontSize = 12.sp, color = TextMain, maxLines = 2)
                                    Spacer(Modifier.height(4.dp))
                                }
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Icon(Icons.Outlined.Person, null, tint = TextMuted, modifier = Modifier.size(11.dp))
                                        Text(log.actor ?: "-", fontSize = 10.sp, color = TextMuted)
                                    }
                                    Text(log.createdAt?.take(16)?.replace("T", " ") ?: "-", fontSize = 10.sp, color = TextMuted)
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

