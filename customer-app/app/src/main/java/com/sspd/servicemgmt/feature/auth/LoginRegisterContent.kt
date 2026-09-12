package com.sspd.servicemgmt.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sspd.servicemgmt.core.ui.theme.Primary
import com.sspd.servicemgmt.core.util.PasswordRules

@Composable
fun LoginRegisterContent(
    mode: AuthMode,
    onModeChange: (AuthMode) -> Unit,
    name: String,
    onNameChange: (String) -> Unit,
    phone: String,
    onPhoneChange: (String) -> Unit,
    email: String,
    onEmailChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    address: String,
    onAddressChange: (String) -> Unit,
    showPassword: Boolean,
    onTogglePassword: () -> Unit,
    loading: Boolean,
    error: String?,
    notice: String?,
    onSubmit: () -> Unit,
    onForgot: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        AuthModeSelector(mode = mode, onModeChange = onModeChange)

        AuthSectionHeader(
            title = if (mode == AuthMode.LOGIN) "အကောင့်ဝင်ရန်" else "အကောင့်အသစ်",
            subtitle = if (mode == AuthMode.LOGIN) {
                "ဖုန်း၊ Email သို့မဟုတ် အမည် နှင့် စကားဝှက် ထည့်ပါ"
            } else {
                "လိုအပ်သော အချက်အလက်များ ဖြည့်ပြီး စတင်အသုံးပြုပါ"
            }
        )

        if (mode == AuthMode.REGISTER) {
            AuthTextField(
                value = name,
                onValueChange = onNameChange,
                label = "အမည်",
                icon = Icons.Rounded.Person,
                keyboardType = KeyboardType.Text
            )
            AuthTextField(
                value = email,
                onValueChange = onEmailChange,
                label = "Email",
                icon = Icons.Rounded.Email,
                keyboardType = KeyboardType.Email
            )
        }

        AuthTextField(
            value = phone,
            onValueChange = onPhoneChange,
            label = if (mode == AuthMode.LOGIN) "ဖုန်း / Email / အမည်" else "ဖုန်းနံပါတ်",
            icon = if (mode == AuthMode.LOGIN) Icons.Rounded.Person else Icons.Rounded.Phone,
            keyboardType = if (mode == AuthMode.LOGIN) KeyboardType.Text else KeyboardType.Phone
        )
        AuthTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = if (mode == AuthMode.REGISTER) PasswordRules.HINT else "စကားဝှက်",
            icon = Icons.Rounded.Lock,
            keyboardType = KeyboardType.Password,
            password = true,
            passwordVisible = showPassword,
            onPasswordVisibilityChange = onTogglePassword,
            imeAction = if (mode == AuthMode.LOGIN) ImeAction.Done else ImeAction.Next,
            onDone = {
                if (!loading && phone.trim().length >= 2 && password.length >= 6) {
                    onSubmit()
                }
            }
        )

        if (mode == AuthMode.REGISTER) {
            AuthTextField(
                value = address,
                onValueChange = onAddressChange,
                label = "လိပ်စာ (မထည့်လည်းရ)",
                icon = Icons.Rounded.Home,
                keyboardType = KeyboardType.Text
            )
        }

        if (mode == AuthMode.LOGIN) {
            TextButton(
                onClick = onForgot,
                modifier = Modifier.align(Alignment.End),
                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp)
            ) {
                Text(
                    "စကားဝှက် မေ့နေပါသလား?",
                    color = Primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        AuthFeedbackMessage(error = error, notice = notice)

        val formReady = if (mode == AuthMode.REGISTER) {
            phone.trim().length >= 6 &&
                PasswordRules.isStrong(password) &&
                email.contains("@")
        } else {
            phone.trim().length >= 2 && password.length >= 6
        }
        AuthPrimaryButton(
            text = if (mode == AuthMode.LOGIN) "အကောင့်ဝင်မည်" else "အကောင့်ဖွင့်မည်",
            loadingText = "ခဏစောင့်ပါ...",
            loading = loading,
            enabled = !loading && formReady,
            onClick = onSubmit
        )
    }
}
