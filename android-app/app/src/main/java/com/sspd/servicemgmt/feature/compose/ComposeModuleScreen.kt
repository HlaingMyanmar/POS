package com.sspd.servicemgmt.feature.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sspd.servicemgmt.core.ui.component.AppEmptyState
import com.sspd.servicemgmt.core.ui.component.AppLoading
import com.sspd.servicemgmt.core.ui.component.AppScaffold
import com.sspd.servicemgmt.core.ui.component.AppSearchField
import com.sspd.servicemgmt.core.ui.theme.CardBg
import com.sspd.servicemgmt.core.ui.theme.OnPrimary
import com.sspd.servicemgmt.core.ui.theme.Primary
import com.sspd.servicemgmt.core.ui.theme.ScreenBg
import com.sspd.servicemgmt.core.ui.theme.TextMain
import com.sspd.servicemgmt.core.ui.theme.TextMuted

@Composable
fun ComposeModuleScreen(moduleId: String, onBack: () -> Unit) {
    val vm: ComposeModuleViewModel = viewModel(key = moduleId)
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(moduleId) { vm.bind(moduleId) }

    state.error?.let { msg ->
        AlertDialog(
            onDismissRequest = vm::clearError,
            title = { Text("သတိပေးချက်", fontWeight = FontWeight.ExtraBold) },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = vm::clearError) { Text("အိုကေ") } }
        )
    }

    if (state.editorTitle != null) {
        AlertDialog(
            onDismissRequest = vm::closeEditor,
            title = { Text(state.editorTitle ?: "", fontWeight = FontWeight.ExtraBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(state.fieldA, vm::setFieldA, label = { Text("အမည်") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(state.fieldB, vm::setFieldB, label = { Text("ဖုန်း / မှတ်ချက်") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    if (moduleId == "staff" || moduleId == "payment-methods") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("ဖွင့်ထားသည်", modifier = Modifier.weight(1f))
                            Switch(checked = state.toggle, onCheckedChange = vm::setToggle)
                        }
                    }
                }
            },
            confirmButton = { Button(onClick = vm::save) { Text("သိမ်းမည်") } },
            dismissButton = { TextButton(onClick = vm::closeEditor) { Text("ပယ်ဖျက်") } }
        )
    }

    val filtered = state.rows.filter {
        val q = state.query.trim()
        q.isBlank() || (it.title + it.subtitle + it.trailing).contains(q, true)
    }

    AppScaffold(
        title = state.title.ifBlank { "Module" },
        onBack = onBack,
        actions = {
            IconButton(onClick = vm::load) { Icon(Icons.Outlined.Refresh, "ပြန်ဖတ်ရန်", tint = OnPrimary) }
        },
        floatingActionButton = {
            if (state.canCreate) {
                FloatingActionButton(onClick = vm::openCreate, containerColor = Primary) {
                    Icon(Icons.Outlined.Add, "အသစ်", tint = Color.White)
                }
            }
        }
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { AppLoading() }
            return@AppScaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).background(ScreenBg),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (state.infoBody != null) {
                item {
                    Text(state.infoBody ?: "", color = TextMain, fontSize = 15.sp, lineHeight = 22.sp)
                }
            }
            if (state.metrics.isNotEmpty()) {
                itemsIndexed(state.metrics) { _, (label, value) ->
                    Card(colors = CardDefaults.cardColors(containerColor = CardBg), shape = RoundedCornerShape(12.dp)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp)) {
                            Text(label, fontSize = 12.sp, color = TextMuted)
                            Text(value, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = TextMain)
                        }
                    }
                }
            }
            if (state.rows.isNotEmpty()) {
                item { AppSearchField(state.query, vm::setQuery, placeholder = "ရှာရန်") }
            }
            if (filtered.isEmpty() && state.infoBody == null && state.metrics.isEmpty()) {
                item { AppEmptyState("စာရင်း မရှိပါ") }
            }
            items(filtered, key = { it.id + it.title }) { row ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().then(
                        if (state.canCreate) Modifier.clickable { vm.openEdit(row) } else Modifier
                    )
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(row.title, fontWeight = FontWeight.Bold, color = TextMain)
                            if (row.subtitle.isNotBlank()) {
                                Spacer(Modifier.height(2.dp))
                                Text(row.subtitle, fontSize = 12.sp, color = TextMuted)
                            }
                        }
                        if (row.trailing.isNotBlank()) {
                            Text(row.trailing, fontWeight = FontWeight.SemiBold, color = Primary, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}
