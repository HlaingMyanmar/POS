package com.sspd.servicemgmt.feature.service.catalog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sspd.servicemgmt.core.network.ServiceItemDTO
import com.sspd.servicemgmt.core.ui.theme.AppTheme
import com.sspd.servicemgmt.core.ui.theme.BorderColor
import com.sspd.servicemgmt.core.ui.theme.Primary
import com.sspd.servicemgmt.core.ui.theme.TextMain
import com.sspd.servicemgmt.core.ui.theme.TextMuted

fun filterServiceItems(items: List<ServiceItemDTO>, query: String): List<ServiceItemDTO> {
    val q = query.trim()
    if (q.isBlank()) return items
    return items.filter { si ->
        si.item.contains(q, true) ||
            si.code.orEmpty().contains(q, true) ||
            si.serviceTypeName.orEmpty().contains(q, true) ||
            si.subServiceTypeName.orEmpty().contains(q, true)
    }
}

@Composable
fun ServiceItemPickerContent(
    items: List<ServiceItemDTO>,
    search: String,
    onSearch: (String) -> Unit,
    onSelect: (ServiceItemDTO) -> Unit,
    modifier: Modifier = Modifier
) {
    val listMaxHeight = (LocalConfiguration.current.screenHeightDp * 0.52f).dp
    Column(modifier.fillMaxWidth()) {
        Text("ဝန်ဆောင်မှု ရွေးပါ", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = search,
            onValueChange = onSearch,
            placeholder = { Text("ရှာဖွေပါ (အမည် / အမျိုးအစား)", fontSize = 12.sp) },
            leadingIcon = { Icon(Icons.Outlined.Search, null, modifier = Modifier.size(18.dp)) },
            trailingIcon = {
                if (search.isNotBlank()) {
                    IconButton(onClick = { onSearch("") }) {
                        Icon(Icons.Outlined.Clear, "ရှင်းရန်", modifier = Modifier.size(18.dp))
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(10.dp)
        )
        Spacer(Modifier.height(8.dp))
        if (items.isEmpty()) {
            Text(
                "ဝန်ဆောင်မှု မတွေ့ပါ",
                fontSize = 12.sp,
                color = TextMuted,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = listMaxHeight),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                itemsIndexed(items, key = { index, si -> si.id ?: "si-$index-${si.item}" }) { _, si ->
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onSelect(si) }.padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(si.item, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextMain)
                                if (!si.serviceTypeName.isNullOrBlank())
                                    Text(si.serviceTypeName, fontSize = 11.sp, color = TextMuted)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    "ပုံမှန် ${String.format("%,.0f", si.price)} Ks",
                                    fontSize = 12.sp,
                                    color = Primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "အနည်းဆုံး ${si.minPrice?.let { String.format("%,.0f", it) } ?: "—"} · အများဆုံး ${si.maxPrice?.let { String.format("%,.0f", it) } ?: "—"}",
                                    fontSize = 10.sp,
                                    color = TextMuted
                                )
                            }
                        }
                        HorizontalDivider(color = BorderColor)
                    }
                }
            }
        }
    }
}

private val previewServiceItems = listOf(
    ServiceItemDTO(id = 1, item = "Laptop Cleaning", price = 15000.0, minPrice = 10000.0, maxPrice = 25000.0, serviceTypeName = "Maintenance"),
    ServiceItemDTO(id = 2, item = "CCTV Installation", price = 45000.0, minPrice = 35000.0, maxPrice = 80000.0, serviceTypeName = "Installation"),
    ServiceItemDTO(id = 3, item = "Network Troubleshooting", price = 20000.0, minPrice = 15000.0, maxPrice = 40000.0, serviceTypeName = "Network"),
    ServiceItemDTO(id = 4, item = "Printer Repair", price = 18000.0, minPrice = 12000.0, maxPrice = 30000.0, serviceTypeName = "Repair")
)

@Preview(name = "ဝန်ဆောင်မှု ရွေးပါ", showBackground = true, widthDp = 390, heightDp = 640)
@Composable
private fun ServiceItemPickerPreview() {
    AppTheme {
        Surface(color = Color.White) {
            ServiceItemPickerContent(
                items = previewServiceItems,
                search = "",
                onSearch = {},
                onSelect = {},
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            )
        }
    }
}

@Preview(name = "ရှာဖွေမှု ရလဒ်မရှိ", showBackground = true, widthDp = 390, heightDp = 320)
@Composable
private fun ServiceItemPickerEmptyPreview() {
    AppTheme {
        Surface(color = Color.White) {
            ServiceItemPickerContent(
                items = emptyList(),
                search = "xyz",
                onSearch = {},
                onSelect = {},
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            )
        }
    }
}

@Composable
fun rememberFilteredServiceItems(
    items: List<ServiceItemDTO>,
    query: String
): List<ServiceItemDTO> = remember(items, query) { filterServiceItems(items, query) }
