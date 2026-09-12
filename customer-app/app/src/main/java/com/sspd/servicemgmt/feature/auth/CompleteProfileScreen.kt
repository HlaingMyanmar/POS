package com.sspd.servicemgmt.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sspd.servicemgmt.core.ui.theme.OnPrimary
import com.sspd.servicemgmt.core.ui.theme.Primary
import com.sspd.servicemgmt.core.ui.theme.PrimaryDark
import com.sspd.servicemgmt.core.ui.theme.ScreenBg
import com.sspd.servicemgmt.core.util.PreferenceManager

@Composable
fun CompleteProfileScreen(onDone: () -> Unit) {
    val vm: AuthViewModel = viewModel()
    val context = LocalContext.current
    val prefs = remember { PreferenceManager(context) }
    var name by remember { mutableStateOf(prefs.displayName) }
    var phone by remember { mutableStateOf(prefs.phone) }
    var address by remember { mutableStateOf(prefs.address) }

    LaunchedEffect(vm.profileSaved) { if (vm.profileSaved) onDone() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to PrimaryDark,
                        0.22f to Primary,
                        0.45f to ScreenBg,
                        1.0f to ScreenBg
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(56.dp))
            Text(
                "ပရိုဖိုင် ဖြည့်စွက်ရန်",
                style = MaterialTheme.typography.headlineSmall,
                color = OnPrimary,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Service ခေါ်ရန် / ပစ္စည်းမှာရန် ဖုန်းလိုအပ်ပါတယ်",
                style = MaterialTheme.typography.bodyMedium,
                color = OnPrimary.copy(alpha = 0.86f)
            )
            Spacer(Modifier.height(28.dp))

            AuthFormPanel {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    AuthSectionHeader(
                        title = "ဆက်သွယ်ရန် အချက်အလက်",
                        subtitle = if (prefs.email.isNotBlank()) prefs.email else "ဖုန်းနှင့် လိပ်စာ ထည့်ပါ"
                    )
                    AuthTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = "အမည်",
                        icon = Icons.Rounded.Person,
                        keyboardType = KeyboardType.Text
                    )
                    AuthTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = "ဖုန်း",
                        icon = Icons.Rounded.Phone,
                        keyboardType = KeyboardType.Phone
                    )
                    AuthTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = "လိပ်စာ",
                        icon = Icons.Rounded.Home,
                        keyboardType = KeyboardType.Text
                    )
                    AuthFeedbackMessage(error = vm.error, notice = null)
                    AuthPrimaryButton(
                        text = "သိမ်းမည်",
                        loadingText = "သိမ်းနေသည်...",
                        loading = vm.loading,
                        enabled = !vm.loading && phone.trim().length >= 6,
                        onClick = { vm.completeProfile(name, phone, address) }
                    )
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}
