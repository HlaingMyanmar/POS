package com.sspd.servicemgmt.core.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sspd.servicemgmt.core.ui.theme.BorderColor
import com.sspd.servicemgmt.core.ui.theme.TextMain
import com.sspd.servicemgmt.core.ui.theme.TextMuted

@Composable
fun AppSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search"
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
                    Icon(Icons.Outlined.Clear, "Clear", modifier = Modifier.size(18.dp))
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp)
    )
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
        val normalized = query.trim()
        if (normalized.isBlank()) items else items.filter {
            label(it).contains(normalized, true) ||
                    subtitle?.invoke(it).orEmpty().contains(normalized, true)
        }
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
            AppSearchField(value = query, onValueChange = { query = it })
            Spacer(Modifier.height(8.dp))
            if (filtered.isEmpty()) {
                Text("ရှာမတွေ့ပါ", fontSize = 12.sp, color = TextMuted, modifier = Modifier.padding(vertical = 16.dp))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = listMaxHeight),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    itemsIndexed(
                        filtered,
                        key = { index, item -> key?.invoke(index, item) ?: "${label(item)}-$index" }
                    ) { _, item ->
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
