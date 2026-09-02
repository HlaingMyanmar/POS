package com.sspd.servicemgmt.feature.inventory

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sspd.servicemgmt.core.network.BrandDTO
import com.sspd.servicemgmt.core.network.CategoryDTO
import com.sspd.servicemgmt.core.network.UnitDTO
import com.sspd.servicemgmt.core.ui.component.AppLoading
import com.sspd.servicemgmt.core.ui.component.AppSearchField
import com.sspd.servicemgmt.core.ui.theme.*

private enum class InventorySetupTab(val title: String, val icon: ImageVector) {
    BRANDS("အမှတ်တံဆိပ်", Icons.Outlined.Bookmark),
    TAXONOMY("အမျိုးအစား", Icons.Outlined.AccountTree),
    UNITS("တိုင်းတာယူနစ်", Icons.Outlined.Straighten)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventorySetupScreen(onBack: () -> Unit) {
    val vm: InventorySetupViewModel = viewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var selectedTab by remember { mutableStateOf(InventorySetupTab.BRANDS) }
    var setupQuery by remember { mutableStateOf("") }
    var brandDialog by remember { mutableStateOf<BrandDTO?>(null) }
    var showNewBrand by remember { mutableStateOf(false) }
    var categoryDialog by remember { mutableStateOf<CategoryDTO?>(null) }
    var showNewCategory by remember { mutableStateOf(false) }
    var unitDialog by remember { mutableStateOf<UnitDTO?>(null) }
    var showNewUnit by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<DeleteTarget?>(null) }

    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it); vm.clearMessages() }
    }
    LaunchedEffect(state.success) {
        state.success?.let { snackbar.showSnackbar(it); vm.clearMessages() }
    }

    BrandDialog(
        visible = showNewBrand || brandDialog != null,
        item = brandDialog,
        saving = state.saving,
        onDismiss = { showNewBrand = false; brandDialog = null },
        onSave = { id, name, active ->
            vm.saveBrand(id, name, active) {
                showNewBrand = false
                brandDialog = null
            }
        }
    )

    CategoryDialog(
        visible = showNewCategory || categoryDialog != null,
        item = categoryDialog,
        parents = flattenCategories(state.categoryTree).filter { it.id != categoryDialog?.id },
        saving = state.saving,
        onDismiss = { showNewCategory = false; categoryDialog = null },
        onSave = { id, name, parentId, active ->
            vm.saveCategory(id, name, parentId, active) {
                showNewCategory = false
                categoryDialog = null
            }
        }
    )

    UnitDialog(
        visible = showNewUnit || unitDialog != null,
        item = unitDialog,
        saving = state.saving,
        onDismiss = { showNewUnit = false; unitDialog = null },
        onSave = { id, name, symbol, desc, active ->
            vm.saveUnit(id, name, symbol, desc, active) {
                showNewUnit = false
                unitDialog = null
            }
        }
    )

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            icon = { Icon(Icons.Outlined.Delete, null, tint = Danger) },
            title = { Text("ဖျက်မည်လား", fontWeight = FontWeight.ExtraBold) },
            text = { Text(target.label, fontSize = 14.sp) },
            confirmButton = {
                Button(
                    enabled = !state.deleting,
                    onClick = {
                        when (target) {
                            is DeleteTarget.Brand -> vm.deleteBrand(target.item)
                            is DeleteTarget.Category -> vm.deleteCategory(target.item)
                            is DeleteTarget.Unit -> vm.deleteUnit(target.item)
                        }
                        deleteTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Danger)
                ) { Text("ဖျက်မည်", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("မဖျက်ပါ") } }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("ပစ္စည်းအခြေခံစာရင်း", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, null, tint = OnPrimary) } },
                actions = { IconButton(onClick = { vm.load() }) { Icon(Icons.Outlined.Refresh, null, tint = Color.White) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Primary, titleContentColor = OnPrimary)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    when (selectedTab) {
                        InventorySetupTab.BRANDS -> showNewBrand = true
                        InventorySetupTab.TAXONOMY -> showNewCategory = true
                        InventorySetupTab.UNITS -> showNewUnit = true
                    }
                },
                containerColor = Primary,
                contentColor = Color.White
            ) { Icon(Icons.Outlined.Add, null) }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(ScreenBg)
        ) {
            ScrollableTabRow(
                selectedTabIndex = selectedTab.ordinal,
                containerColor = CardBg,
                edgePadding = 10.dp
            ) {
                InventorySetupTab.values().forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = { Text(tab.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        icon = { Icon(tab.icon, null, modifier = Modifier.size(18.dp)) }
                    )
                }
            }

            AppSearchField(
                value = setupQuery,
                onValueChange = { setupQuery = it },
                placeholder = "ရှာဖွေပါ",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )

            if (state.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { AppLoading() }
            } else {
                val q = setupQuery.trim()
                when (selectedTab) {
                    InventorySetupTab.BRANDS -> BrandList(
                        items = state.brands.filter { q.isBlank() || it.name.contains(q, true) },
                        onEdit = { brandDialog = it },
                        onDelete = { deleteTarget = DeleteTarget.Brand(it) }
                    )
                    InventorySetupTab.TAXONOMY -> CategoryTreeList(
                        items = state.categoryTree,
                        onEdit = { categoryDialog = it },
                        onDelete = { deleteTarget = DeleteTarget.Category(it) }
                    )
                    InventorySetupTab.UNITS -> UnitList(
                        items = state.units.filter { q.isBlank() || it.name.contains(q, true) || it.symbol.orEmpty().contains(q, true) },
                        onEdit = { unitDialog = it },
                        onDelete = { deleteTarget = DeleteTarget.Unit(it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun BrandList(items: List<BrandDTO>, onEdit: (BrandDTO) -> Unit, onDelete: (BrandDTO) -> Unit) {
    SetupList(
        emptyText = "အမှတ်တံဆိပ် မရှိသေးပါ",
        rows = items,
        key = { it.id ?: it.name.hashCode() },
        content = { item ->
            SetupRow(
                icon = Icons.Outlined.Bookmark,
                title = item.name,
                sub = if (item.isActive) "အသုံးပြုနေ" else "ပိတ်ထား",
                active = item.isActive,
                onEdit = { onEdit(item) },
                onDelete = { onDelete(item) }
            )
        }
    )
}

@Composable
private fun CategoryTreeList(items: List<CategoryDTO>, onEdit: (CategoryDTO) -> Unit, onDelete: (CategoryDTO) -> Unit) {
    val rows = remember(items) { flattenCategories(items) }
    SetupList(
        emptyText = "အမျိုးအစား မရှိသေးပါ",
        rows = rows,
        key = { it.id ?: it.name.hashCode() },
        content = { item ->
            SetupRow(
                icon = Icons.Outlined.AccountTree,
                title = item.name,
                sub = if (item.isActive) "အသုံးပြုနေ" else "ပိတ်ထား",
                active = item.isActive,
                indent = item.level * 18,
                onEdit = { onEdit(item.raw) },
                onDelete = { onDelete(item.raw) }
            )
        }
    )
}

@Composable
private fun UnitList(items: List<UnitDTO>, onEdit: (UnitDTO) -> Unit, onDelete: (UnitDTO) -> Unit) {
    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(color = PrimaryLight, shape = RoundedCornerShape(18.dp)) {
                    Icon(Icons.Outlined.Straighten, null, tint = Primary, modifier = Modifier.padding(18.dp).size(34.dp))
                }
                Text("တိုင်းတာယူနစ် မရှိသေးပါ", color = TextMain, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                Text("အခုန်၊ လုံး၊ ခု၊ ကီလို စတဲ့ ပစ္စည်းရေတွက်ယူနစ်တွေကို ထည့်ပါ။", color = TextMuted, fontSize = 13.sp)
            }
        }
        return
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp, 14.dp, 14.dp, 88.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Surface(
                color = PrimaryLight,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Primary.copy(0.16f))
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("တိုင်းတာယူနစ်များ", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = TextMain)
                        Text("ပစ္စည်းတစ်ခုချင်းရဲ့ ရေတွက်ပုံကို သတ်မှတ်ပါ", fontSize = 11.sp, color = TextMuted)
                    }
                    Surface(color = Color.White, shape = RoundedCornerShape(10.dp)) {
                        Text("${items.count { it.isActive }} / ${items.size}", modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Primary)
                    }
                }
            }
        }
        items(items, key = { it.id ?: (it.unitName ?: it.name).hashCode() }) { item ->
            UnitCard(
                item = item,
                onEdit = { onEdit(item) },
                onDelete = { onDelete(item) }
            )
        }
    }
}

@Composable
private fun UnitCard(item: UnitDTO, onEdit: () -> Unit, onDelete: () -> Unit) {
    val name = item.unitName?.takeIf { it.isNotBlank() } ?: item.name
    val symbol = item.symbol?.takeIf { it.isNotBlank() } ?: name.take(1)
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, if (item.isActive) Primary.copy(0.24f) else BorderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier.size(46.dp).background(if (item.isActive) PrimaryLight else Color(0xFFF1F5F9), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(symbol.uppercase(), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = if (item.isActive) Primary else TextMuted)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(name, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = TextMain, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.description?.takeIf { it.isNotBlank() } ?: "ဖော်ပြချက် မရှိသေးပါ", fontSize = 11.sp, color = TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Surface(color = if (item.isActive) PrimaryLight else Color(0xFFF1F5F9), shape = RoundedCornerShape(8.dp)) {
                    Text(
                        if (item.isActive) "အသုံးပြုနေ" else "ပိတ်ထား",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (item.isActive) Primary else TextMuted
                    )
                }
            }
            IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, "ပြင်ရန်", tint = Primary) }
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, "ဖျက်ရန်", tint = Danger) }
        }
    }
}

@Composable
private fun <T> SetupList(
    emptyText: String,
    rows: List<T>,
    key: (T) -> Any,
    content: @Composable (T) -> Unit
) {
    if (rows.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(emptyText, color = TextMuted, fontSize = 14.sp)
        }
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp, 14.dp, 14.dp, 88.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(rows, key = key) { content(it) }
    }
}

@Composable
private fun SetupRow(
    icon: ImageVector,
    title: String,
    sub: String,
    active: Boolean,
    indent: Int = 0,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(start = indent.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                Modifier.size(38.dp).background(if (active) PrimaryLight else Color(0xFFF1F5F9), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = if (active) Primary else TextMuted, modifier = Modifier.size(20.dp)) }
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = TextMain, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(sub, fontSize = 11.sp, color = TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, null, tint = Primary) }
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, null, tint = Danger) }
        }
    }
}

@Composable
private fun BrandDialog(
    visible: Boolean,
    item: BrandDTO?,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (Int?, String, Boolean) -> Unit
) {
    if (!visible) return
    var name by remember(item) { mutableStateOf(item?.name ?: "") }
    var active by remember(item) { mutableStateOf(item?.isActive ?: true) }
    BasicSetupDialog(
        title = if (item == null) "အမှတ်တံဆိပ်အသစ်" else "အမှတ်တံဆိပ် ပြင်မည်",
        saving = saving,
        onDismiss = onDismiss,
        onSave = { onSave(item?.id, name, active) }
    ) {
        DialogTextField("အမည်", name) { name = it }
        ActiveRow(active) { active = it }
    }
}

@Composable
private fun CategoryDialog(
    visible: Boolean,
    item: CategoryDTO?,
    parents: List<TreeCategory>,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (Int?, String, Int?, Boolean) -> Unit
) {
    if (!visible) return
    var name by remember(item) { mutableStateOf(item?.name ?: "") }
    var parentId by remember(item) { mutableStateOf(item?.parentId) }
    var active by remember(item) { mutableStateOf(item?.isActive ?: true) }
    BasicSetupDialog(
        title = if (item == null) "အမျိုးအစားအသစ်" else "အမျိုးအစား ပြင်မည်",
        saving = saving,
        onDismiss = onDismiss,
        onSave = { onSave(item?.id, name, parentId, active) }
    ) {
        DialogTextField("အမည်", name) { name = it }
        Text("မိခင်အမျိုးအစား", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextMuted)
        Surface(shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, BorderColor), color = Color.White) {
            Column(Modifier.fillMaxWidth().heightIn(max = 190.dp)) {
                ParentOption("အဓိကအမျိုးအစား", parentId == null) { parentId = null }
                parents.forEach { parent ->
                    ParentOption("${"  ".repeat(parent.level)}${parent.name}", parentId == parent.id) { parentId = parent.id }
                }
            }
        }
        ActiveRow(active) { active = it }
    }
}

@Composable
private fun UnitDialog(
    visible: Boolean,
    item: UnitDTO?,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (Int?, String, String, String, Boolean) -> Unit
) {
    if (!visible) return
    var name by remember(item) { mutableStateOf(item?.unitName?.takeIf { it.isNotBlank() } ?: item?.name ?: "") }
    var symbol by remember(item) { mutableStateOf(item?.symbol ?: "") }
    var desc by remember(item) { mutableStateOf(item?.description ?: "") }
    var active by remember(item) { mutableStateOf(item?.isActive ?: true) }
    BasicSetupDialog(
        title = if (item == null) "တိုင်းတာယူနစ်အသစ်" else "တိုင်းတာယူနစ် ပြင်မည်",
        saving = saving,
        onDismiss = onDismiss,
        onSave = { onSave(item?.id, name, symbol, desc, active) }
    ) {
        DialogTextField("ယူနစ်အမည်", name) { name = it }
        DialogTextField("အတိုကောက်", symbol) { symbol = it }
        DialogTextField("ဖော်ပြချက်", desc, keyboardType = KeyboardType.Text) { desc = it }
        ActiveRow(active) { active = it }
    }
}

@Composable
private fun BasicSetupDialog(
    title: String,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.ExtraBold) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp), content = content) },
        confirmButton = {
            Button(enabled = !saving, onClick = onSave, colors = ButtonDefaults.buttonColors(containerColor = Primary)) {
                if (saving) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                else Text("သိမ်းမည်", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("မလုပ်တော့ပါ") } }
    )
}

@Composable
private fun DialogTextField(label: String, value: String, keyboardType: KeyboardType = KeyboardType.Text, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = label != "ဖော်ပြချက်",
        minLines = if (label == "ဖော်ပြချက်") 2 else 1,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    )
}

@Composable
private fun ActiveRow(active: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("အသုံးပြုမည်", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextMain)
        Switch(checked = active, onCheckedChange = onChange)
    }
}

@Composable
private fun ParentOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, fontSize = 13.sp, color = TextMain, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private data class TreeCategory(
    val id: Int?,
    val name: String,
    val parentId: Int?,
    val isActive: Boolean,
    val level: Int,
    val raw: CategoryDTO
)

private fun flattenCategories(nodes: List<CategoryDTO>, level: Int = 0): List<TreeCategory> =
    nodes.flatMap { node ->
        val row = TreeCategory(
            id = node.id,
            name = node.name,
            parentId = node.parentId,
            isActive = node.isActive,
            level = level,
            raw = node
        )
        listOf(row) + flattenCategories(node.children ?: emptyList(), level + 1)
    }

private sealed class DeleteTarget(val label: String) {
    class Brand(val item: BrandDTO) : DeleteTarget(item.name)
    class Category(val item: CategoryDTO) : DeleteTarget(item.name)
    class Unit(val item: UnitDTO) : DeleteTarget(item.unitName?.takeIf { it.isNotBlank() } ?: item.name)
}
