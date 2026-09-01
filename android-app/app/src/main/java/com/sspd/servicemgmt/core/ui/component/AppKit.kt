package com.sspd.servicemgmt.core.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sspd.servicemgmt.core.ui.theme.AppTheme
import com.sspd.servicemgmt.core.ui.theme.BorderColor
import com.sspd.servicemgmt.core.ui.theme.CardBg
import com.sspd.servicemgmt.core.ui.theme.OnPrimary
import com.sspd.servicemgmt.core.ui.theme.Primary
import com.sspd.servicemgmt.core.ui.theme.PrimaryLight
import com.sspd.servicemgmt.core.ui.theme.ScreenBg
import com.sspd.servicemgmt.core.ui.theme.TextMain
import com.sspd.servicemgmt.core.ui.theme.TextMuted

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = { Text(title, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, "ပြန်ရန်", tint = OnPrimary)
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Primary,
            titleContentColor = OnPrimary,
            actionIconContentColor = OnPrimary,
            navigationIconContentColor = OnPrimary
        )
    )
}

@Composable
fun AppScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    snackbar: SnackbarHostState? = null,
    floatingActionButton: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        containerColor = ScreenBg,
        topBar = { AppTopBar(title = title, onBack = onBack, actions = actions) },
        snackbarHost = { snackbar?.let { SnackbarHost(it) } },
        floatingActionButton = floatingActionButton,
        content = content
    )
}

@Composable
fun AppSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "ရှာဖွေပါ"
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder, fontSize = 13.sp) },
        leadingIcon = { Icon(Icons.Outlined.Search, null, modifier = Modifier.size(18.dp), tint = TextMuted) },
        trailingIcon = {
            if (value.isNotBlank()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Outlined.Clear, "ရှင်းရန်", modifier = Modifier.size(18.dp))
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, BorderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(14.dp), content = content)
    }
}

@Composable
fun AppListRow(
    title: String,
    subtitle: String? = null,
    trailing: String? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    AppCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextMain)
                if (!subtitle.isNullOrBlank()) {
                    Text(subtitle, fontSize = 12.sp, color = TextMuted)
                }
            }
            if (!trailing.isNullOrBlank()) {
                Text(trailing, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Primary)
            }
        }
    }
}

@Composable
fun AppEmptyState(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector = Icons.Outlined.Inbox,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, null, tint = TextMuted, modifier = Modifier.size(40.dp))
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextMain)
        if (!subtitle.isNullOrBlank()) Text(subtitle, fontSize = 12.sp, color = TextMuted)
        if (actionLabel != null && onAction != null) {
            AppPrimaryButton(actionLabel, onClick = onAction)
        }
    }
}

@Composable
fun AppSectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        modifier = modifier.padding(vertical = 8.dp),
        fontSize = 13.sp,
        fontWeight = FontWeight.ExtraBold,
        color = TextMain
    )
}

@Composable
fun AppStatusChip(label: String, color: Color, bg: Color = color.copy(0.12f)) {
    Surface(color = bg, shape = RoundedCornerShape(8.dp)) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
fun AppPrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(46.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = OnPrimary)
    ) {
        Text(label, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> AppPickerSheet(
    title: String,
    items: List<T>,
    label: (T) -> String,
    subtitle: ((T) -> String?)? = null,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    key: ((Int, T) -> Any)? = null
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(items, query) {
        val q = query.trim()
        if (q.isBlank()) items else items.filter { label(it).contains(q, true) || subtitle?.invoke(it).orEmpty().contains(q, true) }
    }
    val listMaxHeight = (LocalConfiguration.current.screenHeightDp * 0.52f).dp
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
        ) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(8.dp))
            AppSearchField(value = query, onValueChange = { query = it }, placeholder = "ရှာဖွေပါ")
            Spacer(Modifier.height(8.dp))
            if (filtered.isEmpty()) {
                Text("မတွေ့ပါ", fontSize = 12.sp, color = TextMuted, modifier = Modifier.padding(vertical = 16.dp))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = listMaxHeight),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    itemsIndexed(filtered, key = { index, item -> key?.invoke(index, item) ?: "${label(item)}-$index" }) { _, item ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(item) }
                                .padding(vertical = 10.dp)
                        ) {
                            Text(label(item), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextMain)
                            subtitle?.invoke(item)?.takeIf { it.isNotBlank() }?.let {
                                Text(it, fontSize = 12.sp, color = TextMuted)
                            }
                        }
                        HorizontalDivider(color = BorderColor)
                    }
                }
            }
        }
    }
}

@Preview(name = "Kit", showBackground = true, widthDp = 390, heightDp = 720)
@Composable
private fun AppKitPreview() {
    AppTheme {
        Surface(color = ScreenBg) {
            Column(
                Modifier
                    .fillMaxSize()
                    .widthIn(max = 560.dp)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AppSearchField(value = "", onValueChange = {}, placeholder = "ရှာဖွေပါ (အမည် / ကုဒ်)")
                AppSectionHeader("ယနေ့ ခြုံငုံ")
                AppListRow(title = "Sale #1001", subtitle = "မောင်မောင်", trailing = "15,000 Ks", onClick = {})
                AppStatusChip("ကျော်လွန်", Color(0xFFDC2626), PrimaryLight)
                AppEmptyState(title = "စာရင်း မရှိသေးပါ", subtitle = "အသစ်ထည့်ရန် အောက်က ခလုတ်နှိပ်ပါ")
                AppPrimaryButton("သိမ်းဆည်းမည်", onClick = {})
            }
        }
    }
}
