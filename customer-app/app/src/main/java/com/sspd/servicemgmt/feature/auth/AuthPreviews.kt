package com.sspd.servicemgmt.feature.auth

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.sspd.servicemgmt.core.ui.theme.AppTheme

@Composable
private fun AuthFormPreviewScaffold(
    mode: AuthMode,
    name: String = "",
    phone: String = "",
    email: String = "",
    password: String = "",
    address: String = "",
    showPassword: Boolean = false,
    loading: Boolean = false,
    error: String? = null,
    notice: String? = null
) {
    AppTheme {
        AuthFormUi(
            mode = mode,
            onModeChange = {},
            name = name,
            onNameChange = {},
            phone = phone,
            onPhoneChange = {},
            email = email,
            onEmailChange = {},
            password = password,
            onPasswordChange = {},
            address = address,
            onAddressChange = {},
            showPassword = showPassword,
            onTogglePassword = {},
            loading = loading,
            error = error,
            notice = notice,
            companyLogo = "",
            companyLogoBytes = null,
            companyName = "SSPD Customer",
            companyTagline = "Service နှင့် Shopping ကို တစ်နေရာတည်းမှာ",
            onSubmit = {},
            onForgotSubmit = {},
            onForgot = {},
            onBackFromForgot = {}
        )
    }
}

@Preview(name = "Login", showBackground = true, backgroundColor = 0xFFF4F7FA, widthDp = 390, heightDp = 844)
@Composable
private fun AuthLoginPreview() {
    AuthFormPreviewScaffold(
        mode = AuthMode.LOGIN,
        phone = "09xxxxxxxx",
        password = "••••••••"
    )
}

@Preview(name = "Register", showBackground = true, backgroundColor = 0xFFF4F7FA, widthDp = 390, heightDp = 844)
@Composable
private fun AuthRegisterPreview() {
    AuthFormPreviewScaffold(
        mode = AuthMode.REGISTER,
        name = "Aung Kyaw",
        email = "aung@example.com",
        phone = "09123456789",
        password = "secret1",
        address = "Yangon"
    )
}

@Preview(name = "Forgot", showBackground = true, backgroundColor = 0xFFF4F7FA, widthDp = 390, heightDp = 844)
@Composable
private fun AuthForgotPreview() {
    AuthFormPreviewScaffold(
        mode = AuthMode.FORGOT,
        email = "aung@example.com",
        notice = "Reset link ကို email သို့ ပို့ပြီးပါပြီ (၅ မိနစ် သက်တမ်း)"
    )
}

@Preview(name = "Login error", showBackground = true, backgroundColor = 0xFFF4F7FA, widthDp = 390, heightDp = 844)
@Composable
private fun AuthLoginErrorPreview() {
    AuthFormPreviewScaffold(
        mode = AuthMode.LOGIN,
        phone = "09xxxxxxxx",
        password = "123456",
        error = "ဖုန်း / Email / အမည် သို့မဟုတ် စကားဝှက် မှားနေပါတယ်"
    )
}
