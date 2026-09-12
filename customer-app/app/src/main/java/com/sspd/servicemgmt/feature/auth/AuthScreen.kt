package com.sspd.servicemgmt.feature.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sspd.servicemgmt.BuildConfig

@Composable
fun AuthScreen(onSuccess: () -> Unit) {
    val vm: AuthViewModel = viewModel()
    var mode by remember { mutableStateOf(AuthMode.LOGIN) }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    val server = BuildConfig.DEFAULT_BASE_URL
    var showPassword by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }

    // ViewModel survives logout; wipe previous success before listening again.
    LaunchedEffect(Unit) {
        vm.resetForLoginScreen()
        vm.loadBranding(server)
        ready = true
    }

    LaunchedEffect(vm.success, ready) {
        if (ready && vm.success) {
            vm.consumeSuccess()
            onSuccess()
        }
    }

    AuthFormUi(
        mode = mode,
        onModeChange = {
            mode = it
            vm.clearFeedback()
        },
        name = name,
        onNameChange = { name = it },
        phone = phone,
        onPhoneChange = { phone = it },
        email = email,
        onEmailChange = { email = it },
        password = password,
        onPasswordChange = { password = it },
        address = address,
        onAddressChange = { address = it },
        showPassword = showPassword,
        onTogglePassword = { showPassword = !showPassword },
        loading = vm.loading,
        error = vm.error,
        notice = vm.notice,
        companyLogo = vm.companyLogo,
        companyLogoBytes = vm.companyLogoBytes,
        companyName = vm.companyName,
        companyTagline = vm.companyTagline,
        onSubmit = {
            vm.submit(
                mode == AuthMode.REGISTER,
                name.trim(),
                phone.trim(),
                password,
                address.trim(),
                email.trim(),
                server
            )
        },
        onForgotSubmit = { vm.forgot(email.trim(), server) },
        onForgot = {
            mode = AuthMode.FORGOT
            vm.clearFeedback()
        },
        onBackFromForgot = {
            mode = AuthMode.LOGIN
            vm.clearFeedback()
        }
    )
}
