package com.sspd.servicemgmt.feature.product

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sspd.servicemgmt.core.ui.component.AppLoading
import com.sspd.servicemgmt.core.ui.theme.*
import com.sspd.servicemgmt.core.util.WarrantyUnit
import com.sspd.servicemgmt.core.util.toWarrantyMonths

import java.io.ByteArrayOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductFormScreen(
    onBack: () -> Unit,
    onSaved: (Int) -> Unit,
    onDeleted: () -> Unit
) {
    val vm: ProductFormViewModel = viewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var picker by remember { mutableStateOf<String?>(null) }
    var showDelete by remember { mutableStateOf(false) }

    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        val bmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
        else @Suppress("DEPRECATION") MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        vm.setPhotoBase64(bitmapToDataUri(bmp))
    }

    state.error?.let { err ->
        AlertDialog(
            onDismissRequest = { vm.clearError() },
            icon = { Icon(Icons.Outlined.ErrorOutline, null, tint = Danger) },
            title = { Text("သတိပေးချက်", fontWeight = FontWeight.ExtraBold) },
            text = { Text(err) },
            confirmButton = { TextButton(onClick = { vm.clearError() }) { Text("အိုကေ") } }
        )
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { if (!state.deleting) showDelete = false },
            icon = { Icon(Icons.Outlined.DeleteForever, null, tint = Danger) },
            title = { Text("ကုန်ပစ္စည်း ဖျက်မည်", fontWeight = FontWeight.ExtraBold) },
            text = { Text("ဒီကုန်ပစ္စည်းကို ဖျက်မည်။ ဆက်လုပ်မလား?") },
            confirmButton = {
                Button(
                    onClick = { vm.delete { showDelete = false; onDeleted() } },
                    enabled = !state.deleting,
                    colors = ButtonDefaults.buttonColors(containerColor = Danger)
                ) {
                    if (state.deleting) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Text("ဖျက်မည်", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("မဖျက်ပါ") } }
        )
    }

    when (picker) {
        "brand" -> PickerSheet(
            title = "Brand ရွေးပါ",
            items = state.brands,
            label = { it.name },
            onSelect = { vm.selectBrand(it); picker = null },
            allowCreate = true,
            createLabel = "Brand အသစ်ထည့်မည်",
            creating = state.creatingBrand,
            onCreate = { name -> vm.createBrand(name) { picker = null } },
            onDismiss = { picker = null }
        )
        "category" -> PickerSheet(
            title = "အမျိုးအစား ရွေးပါ",
            items = state.categories,
            label = { it.name },
            onSelect = { vm.selectCategory(it); picker = null },
            allowCreate = true,
            createLabel = "အမျိုးအစား အသစ်ထည့်မည်",
            creating = state.creatingCategory,
            onCreate = { name -> vm.createCategory(name) { picker = null } },
            onDismiss = { picker = null }
        )
        "unit" -> PickerSheet(
            title = "Unit ရွေးပါ",
            items = state.units,
            label = { unit ->
                val name = unit.unitName?.takeIf { it.isNotBlank() } ?: unit.name
                unit.symbol?.let { "$name ($it)" } ?: name
            },
            onSelect = { vm.selectUnit(it); picker = null },
            onDismiss = { picker = null }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (vm.isEdit) "ကုန်ပစ္စည်း ပြင်ဆင်" else "ကုန်ပစ္စည်း အသစ်", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "နောက်ပြန်", tint = Primary) } },
                actions = {
                    if (vm.isEdit) {
                        IconButton(onClick = { showDelete = true }) {
                            Icon(Icons.Outlined.Delete, "ဖျက်ရန်", tint = Danger)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White, titleContentColor = Primary)
            )
        },
        bottomBar = {
            if (!state.loading) {
                Surface(color = CardBg, shadowElevation = 8.dp, border = BorderStroke(1.dp, BorderColor)) {
                    Button(
                        onClick = { vm.save(onSaved) },
                        enabled = !state.saving,
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary)
                    ) {
                        if (state.saving) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        else {
                            Icon(Icons.Outlined.Save, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("သိမ်းမည်", fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { AppLoading() }
            return@Scaffold
        }

        Column(
            Modifier.fillMaxSize().padding(padding).background(ScreenBg)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ProductPhotoBox(state.photoBase64, onClick = { imageLauncher.launch("image/*") })
            ProductTextField(state.name, vm::setName, "ကုန်ပစ္စည်းအမည် *")

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProductPickCard("Brand *", state.selectedBrand?.name, Modifier.weight(1f)) { picker = "brand" }
                ProductPickCard(
                    "Unit *",
                    state.selectedUnit?.let { it.unitName?.takeIf { n -> n.isNotBlank() } ?: it.name },
                    Modifier.weight(1f)
                ) { picker = "unit" }
            }
            ProductPickCard("အမျိုးအစား *", state.selectedCategory?.name) { picker = "category" }

            Text("ကုန်ပစ္စည်းအခြေအနေ", fontSize = 12.sp, color = TextMuted, fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ProductTypeButton("New", "အသစ်", state.productType, Modifier.widthIn(min = 104.dp)) { vm.setProductType("New") }
                ProductTypeButton("Second_New", "အသစ်နီးပါး", state.productType, Modifier.widthIn(min = 120.dp)) { vm.setProductType("Second_New") }
                ProductTypeButton("Second", "အသုံးပြုပြီး", state.productType, Modifier.widthIn(min = 120.dp)) { vm.setProductType("Second") }
            }

            Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = CardBg), border = BorderStroke(1.dp, BorderColor)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Serial ဖြင့်ထိန်းမည်", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextMain)
                        Text("IMEI/Serial ပါသောပစ္စည်းများအတွက်", fontSize = 11.sp, color = TextMuted)
                    }
                    Switch(checked = state.hasSerial, onCheckedChange = vm::setHasSerial)
                }
            }

            if (!state.hasSerial) {
                ProductTextField(state.stockQty, vm::setStockQty, "လက်ကျန်အရေအတွက်", keyboardType = KeyboardType.Number)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProductTextField(state.sellingPrice, vm::setSellingPrice, "ရောင်းဈေး *", Modifier.weight(1f), KeyboardType.Decimal)
                ProductTextField(state.costPrice, vm::setCostPrice, "ဝယ်ဈေး", Modifier.weight(1f), KeyboardType.Decimal)
            }
            ProductTextField(state.reorderLevel, vm::setReorderLevel, "Reorder", keyboardType = KeyboardType.Number)
            Text("အခြေခံ အာမခံ", fontSize = 12.sp, color = TextMuted, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                ProductTextField(state.warrantyValue, vm::setWarrantyValue, "ကာလ", Modifier.weight(1f), KeyboardType.Number)
                WarrantyUnit.entries.forEach { unit ->
                    FilterChip(
                        selected = state.warrantyUnit == unit,
                        onClick = { vm.setWarrantyUnit(unit) },
                        label = { Text(unit.label, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    )
                }
            }
            val warrantyValue = state.warrantyValue.toIntOrNull() ?: 0
            if (warrantyValue > 0) {
                val months = toWarrantyMonths(warrantyValue, state.warrantyUnit)
                Text(
                    if (state.warrantyUnit == WarrantyUnit.DAY) "= $warrantyValue ရက်"
                    else "= $months လ",
                    fontSize = 11.sp,
                    color = Primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            ProductTextField(state.warrantyTerms, vm::setWarrantyTerms, "အာမခံမှတ်ချက်")
            ProductTextField(state.remark, vm::setRemark, "မှတ်ချက်", minLines = 3)
            Spacer(Modifier.height(86.dp))
        }
    }
}

@Composable
private fun ProductPhotoBox(photoBase64: String?, onClick: () -> Unit) {
    val bmp = remember(photoBase64) { photoBase64?.let { decodeDataUri(it) } }
    Box(
        Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(14.dp))
            .background(PrimaryLight).clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (bmp != null) {
            Image(bitmap = bmp.asImageBitmap(), contentDescription = "Product photo", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Surface(color = Color.Black.copy(0.55f), shape = RoundedCornerShape(10.dp), modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp)) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.PhotoCamera, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("ပုံပြောင်း", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.AddAPhoto, null, tint = Primary, modifier = Modifier.size(34.dp))
                Spacer(Modifier.height(6.dp))
                Text("ကုန်ပစ္စည်းပုံ ထည့်ရန်", color = Primary, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ProductTextField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    keyboardType: KeyboardType = KeyboardType.Text,
    minLines: Int = 1
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = modifier,
        minLines = minLines,
        maxLines = if (minLines > 1) 4 else 1,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
private fun ProductPickCard(label: String, value: String?, modifier: Modifier = Modifier.fillMaxWidth(), onClick: () -> Unit) {
    OutlinedCard(modifier = modifier.clickable { onClick() }, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, BorderColor)) {
        Row(Modifier.fillMaxWidth().padding(13.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.SemiBold)
                Text(value ?: "ရွေးပါ", fontSize = 14.sp, color = if (value == null) TextMuted else TextMain, fontWeight = FontWeight.Bold)
            }
            Icon(Icons.Outlined.ChevronRight, null, tint = TextMuted, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun ProductTypeButton(value: String, label: String, selected: String, modifier: Modifier, onClick: () -> Unit) {
    FilterChip(
        selected = selected == value,
        onClick = onClick,
        modifier = modifier,
        label = { Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> PickerSheet(
    title: String,
    items: List<T>,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    allowCreate: Boolean = false,
    createLabel: String = "အသစ်ထည့်မည်",
    creating: Boolean = false,
    onCreate: ((String) -> Unit)? = null
) {
    var q by remember { mutableStateOf("") }
    val query = q.trim()
    val filtered = if (query.isBlank()) items else items.filter { label(it).contains(query, ignoreCase = true) }
    val exactMatch = filtered.any { label(it).trim().equals(query, ignoreCase = true) }
    val canCreate = allowCreate && query.isNotBlank() && !exactMatch
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            OutlinedTextField(
                value = q,
                onValueChange = { q = it },
                placeholder = { Text("ရှာဖွေရန်") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                singleLine = true,
                shape = RoundedCornerShape(10.dp)
            )
            if (canCreate) {
                OutlinedButton(
                    onClick = { onCreate?.invoke(query) },
                    enabled = !creating && onCreate != null,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (creating) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Outlined.Add, null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("$createLabel: $query", fontWeight = FontWeight.Bold)
                }
            }
            if (filtered.isEmpty()) {
                Text(
                    "ရှာဖွေမှု မတွေ့ပါ",
                    modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                    color = TextMuted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            filtered.forEach { item ->
                ListItem(
                    headlineContent = { Text(label(item), fontWeight = FontWeight.SemiBold) },
                    modifier = Modifier.clickable { onSelect(item) }
                )
                HorizontalDivider(color = BorderColor)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun bitmapToDataUri(bitmap: Bitmap): String {
    val maxDim = 500
    val scaled = if (bitmap.width > maxDim || bitmap.height > maxDim) {
        val ratio = maxDim.toFloat() / maxOf(bitmap.width, bitmap.height)
        Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true)
    } else bitmap
    val out = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, 76, out)
    return "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
}

private fun decodeDataUri(uri: String): Bitmap? = runCatching {
    val raw = uri.substringAfter("base64,", uri)
    val bytes = Base64.decode(raw, Base64.DEFAULT)
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}.getOrNull()
