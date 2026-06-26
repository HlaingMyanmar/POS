package com.sspd.servicemgmt.ui.screens

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
import com.sspd.servicemgmt.ui.theme.*
import com.sspd.servicemgmt.ui.components.AppLoading
import com.sspd.servicemgmt.ui.viewmodel.SaleReturnListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaleReturnListScreen(
    onBack:          () -> Unit,
    onReturnClick:   (Int) -> Unit = {},
    onNewReturn:     () -> Unit    = {}
) {
    val vm: SaleReturnListViewModel = viewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    var searchText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { vm.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ရောင်းချမှု ပြန်လည်ခံယူခြင်း", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "နောက်ပြန်", tint = Color.White) }
                },
                actions = {
                    IconButton(onClick = { vm.load() }) { Icon(Icons.Outlined.Refresh, "ပြန်ဆောင်ရန်", tint = Color.White) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Danger, titleContentColor = Color.White)
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNewReturn, containerColor = Danger) {
                Icon(Icons.Outlined.Add, null, tint = Color.White)
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).background(ScreenBg)) {

            // Search
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                placeholder = { Text("Sale Code / ဖောက်သည် ရှာပါ...") },
                leadingIcon = { Icon(Icons.Outlined.Search, null, tint = TextMuted) },
                trailingIcon = {
                    if (searchText.isNotBlank())
                        IconButton(onClick = { searchText = ""; vm.setSearch("") }) {
                            Icon(Icons.Outlined.Close, null, tint = TextMuted)
                        }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.setSearch(searchText) })
            )

            if (state.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    AppLoading()
                }
            } else if (state.items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.AssignmentReturn, null, tint = TextMuted, modifier = Modifier.size(52.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Return မရှိသေးပါ", color = TextMuted)
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.items) { ret ->
                        Card(
                            shape    = RoundedCornerShape(12.dp),
                            colors   = CardDefaults.cardColors(containerColor = CardBg),
                            border   = BorderStroke(1.dp, BorderColor),
                            modifier = Modifier.clickable { ret.id?.let { onReturnClick(it) } }
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column {
                                        Text(ret.returnCode ?: "#${ret.id}", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Danger)
                                        Text(ret.customerName ?: "—", fontSize = 13.sp, color = TextMain)
                                    }
                                    Surface(color = DangerBg, shape = RoundedCornerShape(8.dp)) {
                                        Text(
                                            "${String.format("%,.0f", ret.totalReturnAmount ?: 0.0)} Ks",
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Danger
                                        )
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Outlined.Receipt, null, tint = TextMuted, modifier = Modifier.size(12.dp))
                                        Text(ret.saleCode ?: "—", fontSize = 11.sp, color = TextMuted)
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Outlined.CalendarMonth, null, tint = TextMuted, modifier = Modifier.size(12.dp))
                                        Text(ret.returnDate?.take(10) ?: "—", fontSize = 11.sp, color = TextMuted)
                                    }
                                }
                                if (!ret.reason.isNullOrBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(ret.reason, fontSize = 11.sp, color = TextMuted, maxLines = 1)
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

