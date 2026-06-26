package com.sspd.servicemgmt.ui.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sspd.servicemgmt.api.ServiceItemDTO
import com.sspd.servicemgmt.api.ServiceTypeDTO
import com.sspd.servicemgmt.api.SubServiceTypeDTO
import com.sspd.servicemgmt.ui.theme.*
import com.sspd.servicemgmt.ui.components.AppLoading
import com.sspd.servicemgmt.ui.viewmodel.ServiceManagementViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceManagementScreen(onBack: () -> Unit) {
    val vm: ServiceManagementViewModel = viewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(state.actionSuccess) {
        state.actionSuccess?.let { snackbar.showSnackbar(it); vm.clearActionSuccess() }
    }
    LaunchedEffect(state.actionError) {
        state.actionError?.let { snackbar.showSnackbar(it); vm.clearActionError() }
    }

    // ── Service Type dialog ────────────────────────────────────────────────────
    state.typeDialog?.let { dialog ->
        ServiceTypeDialog(
            target   = dialog.target,
            saving   = state.saving,
            onDismiss = { vm.closeTypeDialog() },
            onSave   = { name, desc, active -> vm.saveType(name, desc, active) }
        )
    }

    // ── Service Item dialog ────────────────────────────────────────────────────
    state.itemDialog?.let { dialog ->
        ServiceItemDialog(
            target    = dialog.target,
            types     = state.types,
            subTypes  = state.subTypes,
            saving    = state.saving,
            onTypeSelected = { vm.loadSubTypes(it) },
            onDismiss = { vm.closeItemDialog() },
            onSave    = { item, price, typeId, subTypeId, active ->
                vm.saveItem(item, price, typeId, subTypeId, active)
            }
        )
    }

    // ── Delete type confirm ────────────────────────────────────────────────────
    state.deleteTypeTarget?.let { target ->
        DeleteConfirmDialog(
            title   = "အမျိုးအစား ဖျက်မည်",
            name    = target.name,
            saving  = state.saving,
            onConfirm = { vm.deleteType() },
            onDismiss = { vm.cancelDeleteType() }
        )
    }

    // ── Delete item confirm ────────────────────────────────────────────────────
    state.deleteItemTarget?.let { target ->
        DeleteConfirmDialog(
            title   = "ဝန်ဆောင်မှု ဖျက်မည်",
            name    = target.item,
            saving  = state.saving,
            onConfirm = { vm.deleteItem() },
            onDismiss = { vm.cancelDeleteItem() }
        )
    }

    // ── Sub-category sheet ────────────────────────────────────────────────────
    state.subTypeSheetParent?.let { parent ->
        SubTypesSheet(
            parent        = parent,
            subTypes      = state.subTypes,
            saving        = state.saving,
            subTypeDialog = state.subTypeDialog,
            deleteTarget  = state.deleteSubTarget,
            onDismiss     = { vm.closeSubTypeSheet() },
            onAddSub      = { vm.openAddSubDialog() },
            onEditSub     = { vm.openEditSubDialog(it) },
            onDeleteSub   = { vm.confirmDeleteSub(it) },
            onSaveSub     = { name, desc, active -> vm.saveSubType(name, desc, active) },
            onCloseSubDialog  = { vm.closeSubDialog() },
            onConfirmDelete   = { vm.deleteSub() },
            onCancelDelete    = { vm.cancelDeleteSub() }
        )
    }

    val filteredTypes = state.types.filter {
        state.search.isBlank() || it.name.contains(state.search, true)
    }
    val filteredItems = state.items.filter {
        state.search.isBlank() ||
        it.item.contains(state.search, true) ||
        it.serviceTypeName?.contains(state.search, true) == true ||
        it.code?.contains(state.search, true) == true
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("ဝန်ဆောင်မှုများ", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "နောက်ပြန်", tint = Color.White) }
                },
                actions = {
                    IconButton(onClick = { vm.load() }) { Icon(Icons.Outlined.Refresh, "ပြန်ဆောင်ရန်", tint = Color.White) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Primary, titleContentColor = Color.White)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick        = { if (selectedTab == 0) vm.openAddTypeDialog() else vm.openAddItemDialog() },
                containerColor = Primary,
                contentColor   = Color.White,
                icon           = { Icon(Icons.Outlined.Add, null) },
                text           = {
                    Text(
                        if (selectedTab == 0) "အမျိုးအစား ထည့်" else "ဝန်ဆောင်မှု ထည့်",
                        fontWeight = FontWeight.Bold
                    )
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).background(ScreenBg)) {

            TabRow(selectedTabIndex = selectedTab, containerColor = CardBg, contentColor = Primary) {
                Tab(
                    selected = selectedTab == 0, onClick = { selectedTab = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("ဝန်ဆောင်မှုအမျိုးအစား",
                                fontWeight = if (selectedTab == 0) FontWeight.ExtraBold else FontWeight.Normal,
                                fontSize = 12.sp)
                            if (state.types.isNotEmpty()) {
                                Surface(color = Primary, shape = RoundedCornerShape(10.dp)) {
                                    Text("${state.types.size}", modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                        fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                                }
                            }
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 1, onClick = { selectedTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("ဝန်ဆောင်မှုစာရင်း",
                                fontWeight = if (selectedTab == 1) FontWeight.ExtraBold else FontWeight.Normal,
                                fontSize = 12.sp)
                            if (state.items.isNotEmpty()) {
                                Surface(color = Primary, shape = RoundedCornerShape(10.dp)) {
                                    Text("${state.items.size}", modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                        fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                                }
                            }
                        }
                    }
                )
            }

            OutlinedTextField(
                value = state.search, onValueChange = { vm.setSearch(it) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                placeholder = { Text("ရှာဖွေရန်...") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = {
                    if (state.search.isNotBlank())
                        IconButton(onClick = { vm.setSearch("") }) { Icon(Icons.Outlined.Clear, "ရှင်းရန်", tint = TextMuted) }
                },
                singleLine = true, shape = RoundedCornerShape(12.dp)
            )

            if (state.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    AppLoading()
                }
            } else when (selectedTab) {
                0 -> TypesList(
                    types        = filteredTypes,
                    onEdit       = { vm.openEditTypeDialog(it) },
                    onDelete     = { vm.confirmDeleteType(it) },
                    onAdd        = { vm.openAddTypeDialog() },
                    onManageSubs = { vm.openSubTypeSheet(it) }
                )
                1 -> ItemsList(
                    items   = filteredItems,
                    onEdit  = { vm.openEditItemDialog(it) },
                    onDelete = { vm.confirmDeleteItem(it) },
                    onAdd   = { vm.openAddItemDialog() }
                )
            }
        }
    }
}

// ── Types List ────────────────────────────────────────────────────────────────

@Composable
private fun TypesList(
    types:         List<ServiceTypeDTO>,
    onEdit:        (ServiceTypeDTO) -> Unit,
    onDelete:      (ServiceTypeDTO) -> Unit,
    onAdd:         () -> Unit,
    onManageSubs:  (ServiceTypeDTO) -> Unit
) {
    if (types.isEmpty()) {
        EmptyState("ဝန်ဆောင်မှုအမျိုးအစား မရှိပါ", onAdd)
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(types, key = { it.id ?: it.name }) { type ->
            Card(
                shape  = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                border = BorderStroke(1.dp, BorderColor)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .padding(start = 14.dp, top = 10.dp, bottom = 6.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(40.dp).background(VioletBg, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Outlined.Category, null, tint = Violet, modifier = Modifier.size(20.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(type.name, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = TextMain)
                            if (!type.description.isNullOrBlank())
                                Text(type.description, fontSize = 11.sp, color = TextMuted, maxLines = 1)
                        }
                        ActiveBadge(type.isActive)
                        IconButton(onClick = { onEdit(type) }) {
                            Icon(Icons.Outlined.Edit, "ပြင်ရန်", tint = Primary, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { onDelete(type) }) {
                            Icon(Icons.Outlined.Delete, "ဖျက်ရန်", tint = Danger, modifier = Modifier.size(18.dp))
                        }
                    }
                    // Sub-categories chip
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 66.dp, bottom = 10.dp, end = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.clickable { onManageSubs(type) },
                            color    = VioletBg,
                            shape    = RoundedCornerShape(20.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment     = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Outlined.AccountTree, null, tint = Violet, modifier = Modifier.size(13.dp))
                                Text("ခွဲပိုင်းအမျိုးအစားများ", fontSize = 11.sp, color = Violet, fontWeight = FontWeight.SemiBold)
                                Icon(Icons.Outlined.ChevronRight, null, tint = Violet, modifier = Modifier.size(13.dp))
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(88.dp)) }
    }
}

// ── Items List ────────────────────────────────────────────────────────────────

@Composable
private fun ItemsList(
    items:    List<ServiceItemDTO>,
    onEdit:   (ServiceItemDTO) -> Unit,
    onDelete: (ServiceItemDTO) -> Unit,
    onAdd:    () -> Unit
) {
    if (items.isEmpty()) {
        EmptyState("ဝန်ဆောင်မှု မရှိပါ", onAdd)
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items, key = { it.id ?: it.item }) { item ->
            Card(
                shape  = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                border = BorderStroke(1.dp, BorderColor)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        if (!item.code.isNullOrBlank())
                            Text(item.code, fontSize = 10.sp, color = TextMuted)
                        Text(item.item, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = TextMain)
                        if (!item.serviceTypeName.isNullOrBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Outlined.Label, null, tint = TextMuted, modifier = Modifier.size(11.dp))
                                Text(
                                    buildString {
                                        append(item.serviceTypeName)
                                        if (!item.subServiceTypeName.isNullOrBlank())
                                            append(" › ${item.subServiceTypeName}")
                                    },
                                    fontSize = 11.sp, color = TextMuted
                                )
                            }
                        }
                    }
                    Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(end = 4.dp)) {
                        Text("${String.format("%,.0f", item.price)} Ks",
                            fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Primary)
                        Spacer(Modifier.height(4.dp))
                        ActiveBadge(item.isActive)
                    }
                    IconButton(onClick = { onEdit(item) }) {
                        Icon(Icons.Outlined.Edit, "ပြင်ရန်", tint = Primary, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { onDelete(item) }) {
                        Icon(Icons.Outlined.Delete, "ဖျက်ရန်", tint = Danger, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        item { Spacer(Modifier.height(88.dp)) }
    }
}

// ── Service Type Dialog ───────────────────────────────────────────────────────

@Composable
private fun ServiceTypeDialog(
    target:    ServiceTypeDTO?,
    saving:    Boolean,
    onDismiss: () -> Unit,
    onSave:    (name: String, description: String, active: Boolean) -> Unit
) {
    var name   by remember { mutableStateOf(target?.name ?: "") }
    var desc   by remember { mutableStateOf(target?.description ?: "") }
    var active by remember { mutableStateOf(target?.isActive ?: true) }
    var err    by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(if (target != null) Icons.Outlined.Edit else Icons.Outlined.Add, null, tint = Primary, modifier = Modifier.size(20.dp))
                Text(if (target != null) "အမျိုးအစား ပြင်ဆင်ရန်" else "အမျိုးအစား အသစ်", fontWeight = FontWeight.ExtraBold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it; err = "" },
                    label = { Text("အမည် *") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    shape = RoundedCornerShape(10.dp), isError = err.isNotBlank()
                )
                OutlinedTextField(
                    value = desc, onValueChange = { desc = it },
                    label = { Text("ဖော်ပြချက်") },
                    modifier = Modifier.fillMaxWidth(), maxLines = 2,
                    shape = RoundedCornerShape(10.dp)
                )
                ActiveToggle(active) { active = it }
                if (err.isNotBlank()) Text(err, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) { err = "အမည် ရိုက်ထည့်ပါ"; return@Button }
                    onSave(name, desc, active)
                },
                enabled = !saving,
                colors  = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                if (saving) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                else Text(if (target != null) "ပြင်ဆင်မည်" else "သိမ်းဆည်းမည်", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("မလုပ်တော့ပါ") } }
    )
}

// ── Service Item Dialog ───────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServiceItemDialog(
    target:         ServiceItemDTO?,
    types:          List<ServiceTypeDTO>,
    subTypes:       List<SubServiceTypeDTO>,
    saving:         Boolean,
    onTypeSelected: (Int) -> Unit,
    onDismiss:      () -> Unit,
    onSave:         (item: String, price: Double, typeId: Int, subTypeId: Int?, active: Boolean) -> Unit
) {
    var itemName      by remember { mutableStateOf(target?.item ?: "") }
    var priceStr      by remember { mutableStateOf(target?.price?.let { String.format("%.0f", it) } ?: "") }
    var selectedType  by remember { mutableStateOf(types.find { it.id == target?.serviceTypeId }) }
    var selectedSub   by remember { mutableStateOf<SubServiceTypeDTO?>(null) }
    var active        by remember { mutableStateOf(target?.isActive ?: true) }
    var err           by remember { mutableStateOf("") }
    var showTypeSheet by remember { mutableStateOf(false) }
    var showSubSheet  by remember { mutableStateOf(false) }

    // Pre-select sub-type for edit
    LaunchedEffect(subTypes) {
        if (target?.subServiceTypeId != null)
            selectedSub = subTypes.find { it.id == target.subServiceTypeId }
    }

    if (showTypeSheet) {
        ModalBottomSheet(onDismissRequest = { showTypeSheet = false }) {
            Column(Modifier.padding(16.dp)) {
                Text("ဝန်ဆောင်မှုအမျိုးအစား ရွေးပါ", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(8.dp))
                types.forEach { t ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            selectedType = t; selectedSub = null
                            showTypeSheet = false; onTypeSelected(t.id!!)
                        }.padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Text(t.name, fontSize = 14.sp, color = TextMain)
                        if (selectedType?.id == t.id) Icon(Icons.Outlined.Check, null, tint = Primary, modifier = Modifier.size(18.dp))
                    }
                    HorizontalDivider(color = BorderColor)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showSubSheet) {
        ModalBottomSheet(onDismissRequest = { showSubSheet = false }) {
            Column(Modifier.padding(16.dp)) {
                Text("ခွဲပိုင်းအမျိုးအစား ရွေးပါ", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { selectedSub = null; showSubSheet = false }.padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("— မသတ်မှတ်ပါ —", fontSize = 14.sp, color = TextMuted)
                    if (selectedSub == null) Icon(Icons.Outlined.Check, null, tint = Primary, modifier = Modifier.size(18.dp))
                }
                HorizontalDivider(color = BorderColor)
                subTypes.forEach { sub ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { selectedSub = sub; showSubSheet = false }.padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(sub.name, fontSize = 14.sp, color = TextMain)
                        if (selectedSub?.id == sub.id) Icon(Icons.Outlined.Check, null, tint = Primary, modifier = Modifier.size(18.dp))
                    }
                    HorizontalDivider(color = BorderColor)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(if (target != null) Icons.Outlined.Edit else Icons.Outlined.Add, null, tint = Primary, modifier = Modifier.size(20.dp))
                Text(if (target != null) "ဝန်ဆောင်မှု ပြင်ဆင်ရန်" else "ဝန်ဆောင်မှု အသစ်", fontWeight = FontWeight.ExtraBold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = itemName, onValueChange = { itemName = it; err = "" },
                    label = { Text("ဝန်ဆောင်မှုအမည် *") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    shape = RoundedCornerShape(10.dp), isError = err.isNotBlank()
                )
                OutlinedTextField(
                    value = priceStr, onValueChange = { priceStr = it; err = "" },
                    label = { Text("ဈေးနှုန်း (Ks) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )
                // Service Type picker
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth().clickable { showTypeSheet = true },
                    shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, BorderColor)
                ) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(selectedType?.name ?: "ဝန်ဆောင်မှုအမျိုးအစား ရွေးပါ *",
                            color = if (selectedType != null) TextMain else TextMuted, fontSize = 13.sp)
                        Icon(Icons.Outlined.ChevronRight, null, tint = TextMuted, modifier = Modifier.size(16.dp))
                    }
                }
                // Sub-type picker (only if type selected and subTypes available)
                if (selectedType != null) {
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth().clickable { showSubSheet = true },
                        shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, BorderColor)
                    ) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                selectedSub?.name ?: "ခွဲပိုင်းအမျိုးအစား (optional)",
                                color = if (selectedSub != null) TextMain else TextMuted, fontSize = 13.sp
                            )
                            Icon(Icons.Outlined.ChevronRight, null, tint = TextMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                ActiveToggle(active) { active = it }
                if (err.isNotBlank()) Text(err, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val price = priceStr.toDoubleOrNull()
                    when {
                        itemName.isBlank()    -> err = "ဝန်ဆောင်မှုအမည် ရိုက်ထည့်ပါ"
                        price == null         -> err = "ဈေးနှုန်း မှန်ကန်စွာ ရိုက်ပါ"
                        selectedType == null  -> err = "ဝန်ဆောင်မှုအမျိုးအစား ရွေးပါ"
                        else -> onSave(itemName, price, selectedType!!.id!!, selectedSub?.id, active)
                    }
                },
                enabled = !saving,
                colors  = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                if (saving) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                else Text(if (target != null) "ပြင်ဆင်မည်" else "သိမ်းဆည်းမည်", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("မလုပ်တော့ပါ") } }
    )
}

// ── Shared composables ────────────────────────────────────────────────────────

@Composable
private fun DeleteConfirmDialog(
    title:     String,
    name:      String,
    saving:    Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon  = { Icon(Icons.Outlined.Delete, "ဖျက်ရန်", tint = Danger) },
        title = { Text(title, fontWeight = FontWeight.ExtraBold) },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("အောက်ပါကို ဖျက်မည်မှာ သေချာပါသလား?")
                Surface(color = DangerBg, shape = RoundedCornerShape(8.dp)) {
                    Text(name, modifier = Modifier.fillMaxWidth().padding(12.dp),
                        fontWeight = FontWeight.ExtraBold, color = Danger)
                }
            }
        },
        confirmButton = {
            Button(
                onClick  = onConfirm, enabled = !saving,
                colors   = ButtonDefaults.buttonColors(containerColor = Danger)
            ) {
                if (saving) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                else Text("ဖျက်မည်", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("မဖျက်တော့ပါ") } }
    )
}

@Composable
private fun ActiveToggle(active: Boolean, onChange: (Boolean) -> Unit) {
    Card(
        shape  = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = if (active) SuccessBg else ScreenBg),
        border = BorderStroke(1.dp, if (active) Success.copy(0.4f) else BorderColor)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text(
                if (active) "ခွင့်ပြု (Active)" else "ရပ်နား (Inactive)",
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                color = if (active) Success else TextMuted
            )
            Switch(
                checked         = active,
                onCheckedChange = onChange,
                colors          = SwitchDefaults.colors(checkedThumbColor = Success, checkedTrackColor = SuccessBg)
            )
        }
    }
}

@Composable
private fun ActiveBadge(active: Boolean) {
    Surface(
        color = if (active) SuccessBg else BorderColor,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            if (active) "တက်ကြွ" else "ရပ်နား",
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            fontSize = 10.sp, fontWeight = FontWeight.Bold,
            color = if (active) Success else TextMuted
        )
    }
}

@Composable
private fun EmptyState(message: String, onAdd: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.Inbox, null, tint = TextMuted, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(8.dp))
            Text(message, color = TextMuted)
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onAdd) {
                Icon(Icons.Outlined.Add, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("ထည့်ရန်")
            }
        }
    }
}

// ── Sub-Types Bottom Sheet ────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubTypesSheet(
    parent:           ServiceTypeDTO,
    subTypes:         List<SubServiceTypeDTO>,
    saving:           Boolean,
    subTypeDialog:    ServiceManagementViewModel.SubTypeDialog?,
    deleteTarget:     SubServiceTypeDTO?,
    onDismiss:        () -> Unit,
    onAddSub:         () -> Unit,
    onEditSub:        (SubServiceTypeDTO) -> Unit,
    onDeleteSub:      (SubServiceTypeDTO) -> Unit,
    onSaveSub:        (name: String, desc: String, active: Boolean) -> Unit,
    onCloseSubDialog: () -> Unit,
    onConfirmDelete:  () -> Unit,
    onCancelDelete:   () -> Unit
) {
    // Sub-type add/edit dialog (shown on top of the sheet)
    subTypeDialog?.let { dialog ->
        SubTypeDialog(
            target    = dialog.target,
            saving    = saving,
            onDismiss = onCloseSubDialog,
            onSave    = onSaveSub
        )
    }

    // Delete confirm dialog
    deleteTarget?.let { sub ->
        DeleteConfirmDialog(
            title     = "ခွဲပိုင်းအမျိုးအစား ဖျက်မည်",
            name      = sub.name,
            saving    = saving,
            onConfirm = onConfirmDelete,
            onDismiss = onCancelDelete
        )
    }

    ModalBottomSheet(
        onDismissRequest  = onDismiss,
        sheetState        = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "ခွဲပိုင်းအမျိုးအစားများ",
                        fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = TextMain
                    )
                    Text(
                        parent.name,
                        fontSize = 12.sp, color = Violet, fontWeight = FontWeight.SemiBold
                    )
                }
                Button(
                    onClick        = onAddSub,
                    colors         = ButtonDefaults.buttonColors(containerColor = Primary),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    shape          = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Outlined.Add, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("ထည့်ရန်", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            HorizontalDivider(color = BorderColor)

            if (subTypes.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.AccountTree, null, tint = TextMuted, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("ခွဲပိုင်းအမျိုးအစား မရှိပါ", color = TextMuted, fontSize = 13.sp)
                    }
                }
            } else {
                Spacer(Modifier.height(8.dp))
                subTypes.forEach { sub ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(VioletBg, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Outlined.SubdirectoryArrowRight, null, tint = Violet, modifier = Modifier.size(16.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(sub.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextMain)
                            if (!sub.description.isNullOrBlank())
                                Text(sub.description, fontSize = 11.sp, color = TextMuted)
                        }
                        ActiveBadge(sub.isActive)
                        IconButton(onClick = { onEditSub(sub) }, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Outlined.Edit, "ပြင်ရန်", tint = Primary, modifier = Modifier.size(17.dp))
                        }
                        IconButton(onClick = { onDeleteSub(sub) }, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Outlined.Delete, "ဖျက်ရန်", tint = Danger, modifier = Modifier.size(17.dp))
                        }
                    }
                    HorizontalDivider(color = BorderColor.copy(0.5f))
                }
            }
        }
    }
}

// ── Sub-Type Add/Edit Dialog ──────────────────────────────────────────────────

@Composable
private fun SubTypeDialog(
    target:    SubServiceTypeDTO?,
    saving:    Boolean,
    onDismiss: () -> Unit,
    onSave:    (name: String, desc: String, active: Boolean) -> Unit
) {
    var name   by remember { mutableStateOf(target?.name ?: "") }
    var desc   by remember { mutableStateOf(target?.description ?: "") }
    var active by remember { mutableStateOf(target?.isActive ?: true) }
    var err    by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.AccountTree, null, tint = Violet, modifier = Modifier.size(20.dp))
                Text(
                    if (target != null) "ခွဲပိုင်းအမျိုးအစား ပြင်ဆင်" else "ခွဲပိုင်းအမျိုးအစား အသစ်",
                    fontWeight = FontWeight.ExtraBold
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it; err = "" },
                    label = { Text("အမည် *") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    shape = RoundedCornerShape(10.dp), isError = err.isNotBlank()
                )
                OutlinedTextField(
                    value = desc, onValueChange = { desc = it },
                    label = { Text("ဖော်ပြချက်") },
                    modifier = Modifier.fillMaxWidth(), maxLines = 2,
                    shape = RoundedCornerShape(10.dp)
                )
                ActiveToggle(active) { active = it }
                if (err.isNotBlank()) Text(err, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) { err = "အမည် ရိုက်ထည့်ပါ"; return@Button }
                    onSave(name, desc, active)
                },
                enabled = !saving,
                colors  = ButtonDefaults.buttonColors(containerColor = Violet)
            ) {
                if (saving) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                else Text(if (target != null) "ပြင်ဆင်မည်" else "သိမ်းဆည်းမည်", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("မလုပ်တော့ပါ") } }
    )
}

