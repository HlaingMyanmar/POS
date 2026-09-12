package com.sspd.servicemgmt.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sspd.servicemgmt.BuildConfig
import com.sspd.servicemgmt.core.ui.theme.OnPrimary
import com.sspd.servicemgmt.core.ui.theme.Primary
import com.sspd.servicemgmt.core.ui.theme.PrimaryDark
import com.sspd.servicemgmt.core.ui.theme.ScreenBg
import com.sspd.servicemgmt.core.ui.theme.TextMuted

@Composable
fun AuthFormUi(
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
    companyLogo: String = "",
    companyLogoBytes: ByteArray? = null,
    companyName: String = BuildConfig.APP_DISPLAY_NAME,
    companyTagline: String = "Service နှင့် Shopping ကို တစ်နေရာတည်းမှာ",
    onSubmit: () -> Unit,
    onForgotSubmit: () -> Unit,
    onForgot: () -> Unit,
    onBackFromForgot: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to PrimaryDark,
                        0.28f to Primary,
                        0.52f to ScreenBg,
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
            Spacer(Modifier.height(48.dp))
            AuthBrandHeader(
                logoUrl = companyLogo,
                logoBytes = companyLogoBytes,
                companyName = companyName,
                companyTagline = companyTagline
            )
            Spacer(Modifier.height(28.dp))

            AuthFormPanel {
                when (mode) {
                    AuthMode.FORGOT -> ForgotPasswordContent(
                        email = email,
                        onEmailChange = onEmailChange,
                        loading = loading,
                        error = error,
                        notice = notice,
                        onSubmit = onForgotSubmit,
                        onBack = onBackFromForgot
                    )
                    AuthMode.LOGIN, AuthMode.REGISTER -> LoginRegisterContent(
                        mode = mode,
                        onModeChange = onModeChange,
                        name = name,
                        onNameChange = onNameChange,
                        phone = phone,
                        onPhoneChange = onPhoneChange,
                        email = email,
                        onEmailChange = onEmailChange,
                        password = password,
                        onPasswordChange = onPasswordChange,
                        address = address,
                        onAddressChange = onAddressChange,
                        showPassword = showPassword,
                        onTogglePassword = onTogglePassword,
                        loading = loading,
                        error = error,
                        notice = notice,
                        onSubmit = onSubmit,
                        onForgot = onForgot
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(
                text = "Secure customer portal",
                style = MaterialTheme.typography.labelMedium,
                color = TextMuted.copy(alpha = 0.85f),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun AuthBrandHeader(
    logoUrl: String,
    logoBytes: ByteArray?,
    companyName: String,
    companyTagline: String
) {
    CompanyLogoImage(
        logoUrl = logoUrl,
        logoBytes = logoBytes,
        modifier = Modifier.size(72.dp)
    )
    Spacer(Modifier.height(14.dp))
    Text(
        text = companyName.ifBlank { BuildConfig.APP_DISPLAY_NAME },
        style = MaterialTheme.typography.headlineSmall,
        color = OnPrimary,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(6.dp))
    Text(
        text = companyTagline.ifBlank { "Service နှင့် Shopping ကို တစ်နေရာတည်းမှာ" },
        style = MaterialTheme.typography.bodyMedium,
        color = OnPrimary.copy(alpha = 0.86f),
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 12.dp)
    )
}
