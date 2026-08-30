package com.sspd.servicemgmt.feature.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sspd.servicemgmt.BuildConfig
import com.sspd.servicemgmt.R
import com.sspd.servicemgmt.core.ui.theme.*

@Composable
fun LoginScreen(onSuccess: () -> Unit) {
    val vm: LoginViewModel = viewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()

    var username  by remember { mutableStateOf("") }
    var password  by remember { mutableStateOf("") }
    var pwVisible by remember { mutableStateOf(false) }

    LaunchedEffect(state.loginSuccess) {
        if (state.loginSuccess) onSuccess()
    }

    if (BuildConfig.TECHNICIAN_ONLY) {
        TechnicianLoginLayout(
            username = username,
            password = password,
            pwVisible = pwVisible,
            loading = state.loading,
            onUsernameChange = { username = it },
            onPasswordChange = { password = it },
            onTogglePwVisible = { pwVisible = !pwVisible },
            onLogin = { vm.login(username, password) }
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize().background(ScreenBg)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .imePadding()
                .padding(horizontal = 24.dp)
                .padding(top = 48.dp, bottom = 32.dp)
                .widthIn(max = 480.dp)
                .align(Alignment.TopCenter),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            RetailLogo()
            Spacer(Modifier.height(24.dp))
            LoginCard(
                username          = username,
                password          = password,
                pwVisible         = pwVisible,
                loading           = state.loading,
                error             = state.error,
                onUsernameChange  = { username = it; vm.clearError() },
                onPasswordChange  = { password = it; vm.clearError() },
                onTogglePwVisible = { pwVisible = !pwVisible },
                onLogin           = { vm.login(username, password) },
                onClearError      = { vm.clearError() }
            )
            Spacer(Modifier.height(24.dp))
            Text(
                "© 2026 SSPD IT Solution  ·  v${BuildConfig.VERSION_NAME}",
                fontSize = 11.sp,
                color = TextMuted
            )
        }
    }
}

// ── Tech background: Network + Coding + Robot combined ───────────────────────
@Composable
private fun TechnicianLoginLayout(
    username: String, password: String, pwVisible: Boolean, loading: Boolean,
    onUsernameChange: (String) -> Unit, onPasswordChange: (String) -> Unit,
    onTogglePwVisible: () -> Unit, onLogin: () -> Unit,
) {
    val navy = Color(0xFF0B1830)
    val teal = Color(0xFF14B8A6)
    Box(Modifier.fillMaxSize().background(navy)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding()
            .imePadding().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            TechnicianLoginHeader(teal)
            Spacer(Modifier.height(24.dp))
            TechnicianLoginCard(username, password, pwVisible, loading, navy, teal,
                onUsernameChange, onPasswordChange, onTogglePwVisible, onLogin)
        }
    }
}

@Composable
private fun TechnicianLoginHeader(teal: Color) {
    Image(painterResource(R.drawable.logo), "SSPD logo", Modifier.size(52.dp))
    Spacer(Modifier.height(12.dp))
    Text("SSPD FIELD SERVICE", color = Color.White, fontWeight = FontWeight.ExtraBold)
    Text("Technician workspace", color = teal, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(36.dp))
    Text("Welcome, Technician", Modifier.fillMaxWidth(), Color.White, 23.sp, fontWeight = FontWeight.ExtraBold)
    Text("Sign in to start your assigned work", Modifier.fillMaxWidth(), Color.White.copy(0.68f), 12.sp)
}

@Composable
private fun TechnicianLoginCard(
    username: String, password: String, pwVisible: Boolean, loading: Boolean,
    navy: Color, teal: Color, onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit, onTogglePwVisible: () -> Unit, onLogin: () -> Unit,
) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(Color.White), elevation = CardDefaults.cardElevation(12.dp)) {
        Column(Modifier.padding(22.dp)) {
            Text("Technician Sign In", color = navy, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            Text("Use your technician account", color = TextMuted, fontSize = 11.sp)
            Spacer(Modifier.height(22.dp))
            OutlinedTextField(username, onUsernameChange, Modifier.fillMaxWidth(),
                label = { Text("Username") }, leadingIcon = { Icon(Icons.Outlined.Badge, null, tint = teal) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next), singleLine = true,
                shape = RoundedCornerShape(14.dp))
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(password, onPasswordChange, Modifier.fillMaxWidth(),
                label = { Text("Password") }, leadingIcon = { Icon(Icons.Outlined.Lock, null, tint = teal) },
                trailingIcon = { IconButton(onTogglePwVisible) {
                    Icon(if (pwVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, null)
                } }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                visualTransformation = if (pwVisible) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true, shape = RoundedCornerShape(14.dp))
            Spacer(Modifier.height(22.dp))
            Button(onLogin, Modifier.fillMaxWidth().height(54.dp),
                enabled = !loading && username.isNotBlank() && password.isNotBlank(),
                shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(teal)) {
                if (loading) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                else { Icon(Icons.Outlined.Login, null); Spacer(Modifier.width(8.dp)); Text("SIGN IN", fontWeight = FontWeight.ExtraBold) }
            }
        }
    }
}

@Composable
private fun RetailLogo() {
    Image(
        painter = painterResource(R.drawable.logo),
        contentDescription = "လိုဂို",
        modifier = Modifier.size(88.dp)
    )
    Spacer(Modifier.height(12.dp))
    Text("S.S.P.D IT Solution Center", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = TextMain)
    Text("ကုန်ပစ္စည်းနှင့် ရောင်းချမှုစနစ်", fontSize = 13.sp, color = TextMuted)
}

// ── Login card ────────────────────────────────────────────────────────────────
@Composable
private fun LoginCard(
    username:          String,
    password:          String,
    pwVisible:         Boolean,
    loading:           Boolean,
    error:             String = "",
    onUsernameChange:  (String) -> Unit,
    onPasswordChange:  (String) -> Unit,
    onTogglePwVisible: () -> Unit,
    onLogin:           () -> Unit,
    onClearError:      () -> Unit = {},
) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(22.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Column(Modifier.padding(24.dp)) {
            Text(
                "အကောင့်ဝင်ရောက်မည်",
                fontSize = 18.sp, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(18.dp))

            FieldLabel("အသုံးပြုသူနာမည်")
            OutlinedTextField(
                value           = username,
                onValueChange   = onUsernameChange,
                modifier        = Modifier.fillMaxWidth(),
                placeholder     = { Text("အသုံးပြုသူအမည်") },
                leadingIcon     = { Icon(Icons.Outlined.Person, null) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                singleLine      = true,
                shape           = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.height(14.dp))

            FieldLabel("စကားဝှက်")
            OutlinedTextField(
                value                = password,
                onValueChange        = onPasswordChange,
                modifier             = Modifier.fillMaxWidth(),
                placeholder          = { Text("စကားဝှက်") },
                leadingIcon          = { Icon(Icons.Outlined.Lock, null) },
                trailingIcon         = {
                    IconButton(onClick = onTogglePwVisible) {
                        Icon(
                            if (pwVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            null
                        )
                    }
                },
                keyboardOptions      = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction    = ImeAction.Done
                ),
                visualTransformation = if (pwVisible) VisualTransformation.None
                                       else PasswordVisualTransformation(),
                singleLine           = true,
                shape                = RoundedCornerShape(12.dp)
            )

            if (error.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(error, color = Danger, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick  = onLogin,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = Primary),
                enabled  = !loading
            ) {
                if (loading)
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                else
                    Text("ဝင်ရောက်မည်", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted, letterSpacing = 0.5.sp)
    Spacer(Modifier.height(6.dp))
}

@androidx.compose.ui.tooling.preview.Preview(name = "Login", showBackground = true, widthDp = 390, heightDp = 800)
@Composable
private fun LoginRetailPreview() {
    AppTheme {
        Column(
            Modifier.fillMaxSize().background(ScreenBg).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            RetailLogo()
            Spacer(Modifier.height(24.dp))
            LoginCard(
                username = "",
                password = "",
                pwVisible = false,
                loading = false,
                error = "",
                onUsernameChange = {},
                onPasswordChange = {},
                onTogglePwVisible = {},
                onLogin = {}
            )
        }
    }
}
