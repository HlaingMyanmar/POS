package com.sspd.servicemgmt.feature.inventory

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sspd.servicemgmt.core.ui.component.AppLoading
import com.sspd.servicemgmt.core.ui.component.AppSearchField
import com.sspd.servicemgmt.core.ui.theme.*

private val AdjColor = Color(0xFF0891B2)
private val AdjBg    = Color(0xFFECFEFF)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockAdjListScreen(
    onBack:     () -> Unit,
    onAdjClick: (Int) -> Unit = {},
    onNewAdj:   () -> Unit    = {}
) {
    val vm: StockAdjListViewModel = viewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    var searchText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { vm.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ကုန်ပစ္စည်း ပမာဏ ပြင်ဆင်မှု", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, "နောက်ပြန်", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { vm.load() }) {
                        Icon(Icons.Outlined.Refresh, "ပြန်ဆောင်ရန်", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor     = AdjColor,
                    titleContentColor  = Color.White
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNewAdj, containerColor = AdjColor) {
                Icon(Icons.Outlined.Add, null, tint = Color.White)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(ScreenBg)
        ) {
            AppSearchField(
                value = searchText,
                onValueChange = { searchText = it; vm.setSearch(it) },
                placeholder = "ကုဒ် / အကြောင်းအရင်း ရှာပါ...",
                modifier = Modifier.padding(12.dp)
            )

            if (state.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { AppLoading() }
            } else if (state.items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.Inventory, null, tint = TextMuted, modifier = Modifier.size(52.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("ပြင်ဆင်မှု မရှိသေးပါ", color = TextMuted)
                    }
                }
            } else {
                LazyColumn(
                    contentPadding      = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.items) { adj ->
                        Card(
                            shape    = RoundedCornerShape(12.dp),
                            colors   = CardDefaults.cardColors(containerColor = CardBg),
                            border   = BorderStroke(1.dp, BorderColor),
                            modifier = Modifier.clickable { adj.id?.let { onAdjClick(it) } }
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment     = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            adj.adjCode ?: "#${adj.id}",
                                            fontSize   = 13.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color      = AdjColor
                                        )
                                        if (!adj.staffName.isNullOrBlank()) {
                                            Text(adj.staffName, fontSize = 12.sp, color = TextMain)
                                        }
                                    }
                                    Surface(color = AdjBg, shape = RoundedCornerShape(8.dp)) {
                                        Text(
                                            "${adj.items?.size ?: 0} မျိုး",
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = AdjColor
                                        )
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment     = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Outlined.CalendarMonth, null, tint = TextMuted, modifier = Modifier.size(12.dp))
                                        Text(adj.adjDate?.take(10) ?: "—", fontSize = 11.sp, color = TextMuted)
                                    }
                                }
                                if (!adj.reason.isNullOrBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(adj.reason, fontSize = 11.sp, color = TextMuted, maxLines = 1)
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }
}
