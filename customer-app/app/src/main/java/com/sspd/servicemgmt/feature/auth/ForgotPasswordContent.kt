package com.sspd.servicemgmt.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sspd.servicemgmt.core.ui.theme.TextMain
import com.sspd.servicemgmt.core.ui.theme.TextMuted

@Composable
fun ForgotPasswordContent(
    email: String,
    onEmailChange: (String) -> Unit,
    loading: Boolean,
    error: String?,
    notice: String?,
    onSubmit: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.offset(x = (-8).dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "ပြန်သွားရန်",
                    tint = TextMain
                )
            }
            Text(
                "အကောင့်ဝင်ရန် ပြန်သွားမည်",
                color = TextMuted,
                fontWeight = FontWeight.Medium
            )
        }
        AuthSectionHeader(
            title = "စကားဝှက် ပြန်သတ်မှတ်ရန်",
            subtitle = "Customer App အကောင့်နှင့် ချိတ်ထားသော Email သာ ထည့်ပါ။ အခြား Email သို့ ပို့မရပါ။"
        )
        AuthTextField(
            value = email,
            onValueChange = onEmailChange,
            label = "Email",
            icon = Icons.Rounded.Email,
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Done,
            onDone = { if (email.contains("@") && !loading) onSubmit() }
        )
        AuthFeedbackMessage(error = error, notice = notice)
        AuthPrimaryButton(
            text = "Reset link ပို့မည်",
            loadingText = "ပို့နေပါတယ်...",
            loading = loading,
            enabled = !loading && email.contains("@"),
            onClick = onSubmit,
            leadingIcon = Icons.Rounded.Email
        )
    }
}
