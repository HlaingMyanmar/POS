package com.sspd.servicemgmt.feature.auth

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sspd.servicemgmt.BuildConfig
import com.sspd.servicemgmt.R
import com.sspd.servicemgmt.core.ui.theme.*

import kotlin.math.sqrt
import kotlin.random.Random

// ── Particle data classes ─────────────────────────────────────────────────────
private class NetParticle(var x: Float, var y: Float, var vx: Float, var vy: Float)
private class CodeSymbol(var x: Float, var y: Float, val label: String, val speed: Float, val sp: Float)
private class BinaryCol(val x: Float, var headY: Float, val speed: Float, val chars: String)

// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun LoginScreen(onSuccess: () -> Unit) {
    val vm: LoginViewModel = viewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current

    val remembered = remember { vm.rememberedUsername() }
    var username   by rememberSaveable { mutableStateOf(remembered) }
    var password   by remember { mutableStateOf("") }
    var pwVisible  by rememberSaveable { mutableStateOf(false) }
    var rememberMe by rememberSaveable { mutableStateOf(remembered.isNotEmpty()) }

    LaunchedEffect(state.loginSuccess) {
        if (state.loginSuccess) onSuccess()
    }

    fun submit() {
        keyboard?.hide()
        vm.login(username, password, rememberMe = rememberMe)
    }

    if (BuildConfig.TECHNICIAN_ONLY) {
        TechnicianLoginLayout(
            username           = username,
            password           = password,
            pwVisible          = pwVisible,
            loading            = state.loading,
            error              = state.error,
            onUsernameChange   = {
                username = it
                if (state.error.isNotEmpty()) vm.clearError()
            },
            onPasswordChange   = {
                password = it
                if (state.error.isNotEmpty()) vm.clearError()
            },
            onTogglePwVisible  = { pwVisible = !pwVisible },
            onLogin            = { submit() },
            onClearError       = vm::clearError
        )
        return
    }

    if (state.error.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { vm.clearError() },
            icon = { Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("ဝင်ရောက်မှု မအောင်မြင်ပါ", fontWeight = FontWeight.Bold) },
            text  = { Text(state.error) },
            confirmButton = {
                TextButton(onClick = { vm.clearError() }) {
                    Text("အိုကေ", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(Primary)) {

        TechBackground(modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .imePadding()
                .padding(horizontal = 24.dp)
                .padding(top = 56.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LogoSection()
            Spacer(Modifier.height(28.dp))
            LoginCard(
                username          = username,
                password          = password,
                pwVisible         = pwVisible,
                loading           = state.loading,
                onUsernameChange  = { username = it },
                onPasswordChange  = { password = it },
                onTogglePwVisible = { pwVisible = !pwVisible },
                onLogin           = { vm.login(username, password) }
            )
            Spacer(Modifier.height(24.dp))
            Text("© 2026 SSPD IT Solution  ·  v1.0.4-stable", fontSize = 11.sp, color = Color.White.copy(0.45f))
        }
    }
}

private val LoginAccent = Primary
private val LoginFieldBg = Color(0xFFF0F7F7)
private val LoginInk = TextMain

@Composable
private fun TechnicianLoginLayout(
    username: String,
    password: String,
    pwVisible: Boolean,
    loading: Boolean,
    error: String,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTogglePwVisible: () -> Unit,
    onLogin: () -> Unit,
    onClearError: () -> Unit,
) {
    var usernameError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    val usernameFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }

    fun submit() {
        val uBlank = username.isBlank()
        val pBlank = password.isBlank()
        usernameError = if (uBlank) "အသုံးပြုသူအမည် ထည့်ပါ" else null
        passwordError = if (pBlank) "စကားဝှက် ထည့်ပါ" else null
        when {
            uBlank -> usernameFocus.requestFocus()
            pBlank -> passwordFocus.requestFocus()
            else   -> onLogin()
        }
    }

    if (error.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = onClearError,
            icon = {
                Icon(
                    Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = Danger,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text("ဝင်ရောက်မှု မအောင်မြင်ပါ", fontWeight = FontWeight.Bold)
            },
            text = { Text(error) },
            confirmButton = {
                Button(
                    onClick = onClearError,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = LoginAccent)
                ) {
                    Text("အိုကေ", fontWeight = FontWeight.Bold)
                }
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = Color.White
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFE8F3F5), Color.White)
                )
            )
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                color = Primary.copy(alpha = 0.08f),
                radius = size.width * 0.55f,
                center = Offset(size.width * 0.92f, size.height * -0.02f)
            )
            drawCircle(
                color = Primary.copy(alpha = 0.05f),
                radius = size.width * 0.42f,
                center = Offset(size.width * -0.08f, size.height * 1.02f)
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(40.dp))
                Box(
                    modifier = Modifier
                        .size(92.dp)
                        .shadow(12.dp, CircleShape, ambientColor = Primary.copy(0.18f))
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.logo),
                        contentDescription = "SSPD လိုဂို",
                        modifier = Modifier.size(56.dp)
                    )
                }
                Spacer(Modifier.height(18.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(PrimaryLight)
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(
                        Icons.Outlined.Build,
                        contentDescription = null,
                        tint = LoginAccent,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "SSPD IT SERVICE",
                        color = LoginAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.8.sp
                    )
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    "မင်္ဂလာပါ",
                    color = LoginInk,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "အလုပ်စတင်ရန် Technician အကောင့်ဝင်ပါ",
                    color = TextMuted,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Computer • Network • Technical Support",
                    color = LoginAccent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                ServiceIdentityRow()
                Spacer(Modifier.height(22.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(10.dp, RoundedCornerShape(24.dp), ambientColor = Color(0x1A0F766E))
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.White)
                        .padding(horizontal = 20.dp, vertical = 22.dp)
                ) {
                    PrettyLoginField(
                        value = username,
                        onValueChange = { usernameError = null; onUsernameChange(it) },
                        label = "အသုံးပြုသူအမည်",
                        icon = Icons.Outlined.Person,
                        enabled = !loading,
                        isError = usernameError != null,
                        error = usernameError,
                        focusRequester = usernameFocus,
                        imeAction = ImeAction.Next,
                        onIme = { passwordFocus.requestFocus() }
                    )
                    Spacer(Modifier.height(14.dp))
                    PrettyLoginField(
                        value = password,
                        onValueChange = { passwordError = null; onPasswordChange(it) },
                        label = "စကားဝှက်",
                        icon = Icons.Outlined.Lock,
                        enabled = !loading,
                        isError = passwordError != null,
                        error = passwordError,
                        focusRequester = passwordFocus,
                        imeAction = ImeAction.Done,
                        onIme = { submit() },
                        isPassword = true,
                        pwVisible = pwVisible,
                        onTogglePwVisible = onTogglePwVisible
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = { submit() },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        enabled = !loading,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = LoginAccent,
                            disabledContainerColor = LoginAccent.copy(alpha = 0.55f),
                            disabledContentColor = Color.White
                        ),
                        elevation = ButtonDefaults.buttonElevation(0.dp)
                    ) {
                        if (loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("ဝင်ရောက်နေသည်...", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        } else {
                            Text("ဝင်ရောက်မည်", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
            Text(
                "SSPD IT Solution Center  ·  v${BuildConfig.VERSION_NAME}",
                color = TextMuted.copy(alpha = 0.8f),
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 20.dp)
            )
        }
    }
}

@Composable
private fun PrettyLoginField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    isError: Boolean,
    error: String?,
    focusRequester: FocusRequester,
    imeAction: ImeAction,
    onIme: () -> Unit,
    isPassword: Boolean = false,
    pwVisible: Boolean = false,
    onTogglePwVisible: () -> Unit = {}
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
        enabled = enabled,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        trailingIcon = if (isPassword) {
            {
                IconButton(onClick = onTogglePwVisible, enabled = enabled) {
                    Icon(
                        if (pwVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = if (pwVisible) "စကားဝှက် ဖျောက်ရန်" else "စကားဝှက် ပြရန်"
                    )
                }
            }
        } else null,
        isError = isError,
        supportingText = if (error != null) {
            { Text(error) }
        } else null,
        singleLine = true,
        visualTransformation = if (isPassword && !pwVisible)
            PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            keyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Text,
            imeAction = imeAction
        ),
        keyboardActions = KeyboardActions(
            onNext = { onIme() },
            onDone = { onIme() }
        ),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = LoginAccent,
            focusedLabelColor = LoginAccent,
            focusedLeadingIconColor = LoginAccent,
            cursorColor = LoginAccent,
            unfocusedBorderColor = Color.Transparent,
            unfocusedContainerColor = LoginFieldBg,
            focusedContainerColor = Color.White,
            errorBorderColor = Danger,
            errorLabelColor = Danger,
            errorSupportingTextColor = Danger
        )
    )
}

// ── Tech background: Network + Coding + Robot combined ───────────────────────
@Composable
private fun TechBackground(modifier: Modifier = Modifier) {

    val codeLabels = listOf(
        "</>", "{}", "[]", "=>", "if", "for", "val",
        "AI", "fn", "&&", "01", "!=", "//", "int", "::"
    )

    // Network particles
    val net = remember {
        List(20) {
            NetParticle(
                Random.nextFloat(), Random.nextFloat(),
                (Random.nextFloat() - 0.5f) * 0.00011f,
                (Random.nextFloat() - 0.5f) * 0.00011f
            )
        }
    }

    // Floating code symbols (coding layer)
    val code = remember {
        List(10) { i ->
            CodeSymbol(
                x     = Random.nextFloat(),
                y     = Random.nextFloat(),
                label = codeLabels[i % codeLabels.size],
                speed = 0.000028f + Random.nextFloat() * 0.000018f,
                sp    = 9f + Random.nextFloat() * 7f
            )
        }
    }

    // Binary rain columns (robot / AI layer)
    val binary = remember {
        List(5) {
            BinaryCol(
                x      = 0.08f + it * 0.21f,
                headY  = Random.nextFloat(),
                speed  = 0.00005f + Random.nextFloat() * 0.00004f,
                chars  = buildString { repeat(20) { append(if (Random.nextBoolean()) '1' else '0') } }
            )
        }
    }

    // Shared Paint for text (avoid allocation every frame)
    val paint = remember {
        android.graphics.Paint().apply {
            color     = android.graphics.Color.WHITE
            typeface  = android.graphics.Typeface.MONOSPACE
            textAlign = android.graphics.Paint.Align.CENTER
        }
    }

    var tick by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameMillis { ms ->
                val dt = if (last == 0L) 16f else (ms - last).toFloat()
                last = ms

                // Network: move + bounce
                net.forEach { p ->
                    p.x = (p.x + p.vx * dt).coerceIn(0f, 1f)
                    p.y = (p.y + p.vy * dt).coerceIn(0f, 1f)
                    if (p.x == 0f || p.x == 1f) p.vx = -p.vx
                    if (p.y == 0f || p.y == 1f) p.vy = -p.vy
                }

                // Code symbols: float upward, reset at bottom when reaching top
                code.forEach { s ->
                    s.y -= s.speed * dt
                    if (s.y < -0.06f) { s.y = 1.06f; s.x = Random.nextFloat() }
                }

                // Binary rain: fall downward, reset at top
                binary.forEach { b ->
                    b.headY += b.speed * dt
                    if (b.headY > 1.08f) b.headY = -0.08f
                }

                tick = ms
            }
        }
    }

    if (tick > Long.MIN_VALUE) Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val d = density

        // ── Layer 1 : Network (nodes + edges) ────────────────────────────
        val maxDist = w * 0.20f
        for (i in net.indices) {
            for (j in i + 1 until net.size) {
                val dx   = (net[i].x - net[j].x) * w
                val dy   = (net[i].y - net[j].y) * h
                val dist = sqrt(dx * dx + dy * dy)
                if (dist < maxDist) drawLine(
                    color       = Color.White.copy(alpha = (1f - dist / maxDist) * 0.28f),
                    start       = Offset(net[i].x * w, net[i].y * h),
                    end         = Offset(net[j].x * w, net[j].y * h),
                    strokeWidth = 1f
                )
            }
        }
        net.forEach { p ->
            drawCircle(Color.White.copy(0.48f), 2.8f, Offset(p.x * w, p.y * h))
        }

        // ── Layer 2 : Binary rain (robot / AI) ───────────────────────────
        binary.forEach { col ->
            val colX  = col.x * w
            val charH = 13f * d
            col.chars.forEachIndexed { idx, ch ->
                val charY = col.headY * h - idx * charH
                if (charY < -charH || charY > h + charH) return@forEachIndexed
                val t     = 1f - idx.toFloat() / col.chars.length
                val alpha = (t * t * 0.50f).coerceIn(0f, 1f)
                drawIntoCanvas { canvas ->
                    paint.textSize = 10f * d
                    paint.alpha    = (alpha * 255).toInt()
                    canvas.nativeCanvas.drawText(ch.toString(), colX, charY, paint)
                }
            }
        }

        // ── Layer 3 : Floating code symbols (coding) ─────────────────────
        code.forEach { s ->
            val alpha = when {
                s.y > 0.88f -> ((1f - s.y) / 0.12f) * 0.42f
                s.y < 0.12f -> (s.y / 0.12f) * 0.42f
                else        -> 0.42f
            }.coerceIn(0f, 1f)
            drawIntoCanvas { canvas ->
                paint.textSize = s.sp * d
                paint.alpha    = (alpha * 255).toInt()
                canvas.nativeCanvas.drawText(s.label, s.x * w, s.y * h, paint)
            }
        }

    }
}

// ── Logo section ──────────────────────────────────────────────────────────────
@Composable
private fun LogoSection() {
    AnimatedLogo(modifier = Modifier.size(130.dp))
    Spacer(Modifier.height(12.dp))
    Text("S.S.P.D IT Solution Center", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
    Text(
        if (BuildConfig.TECHNICIAN_ONLY) "Technician အတွက်သာ"
        else "ကုန်ပစ္စည်းနှင့် ရောင်းချမှုစနစ်",
        fontSize = 12.sp,
        color = Color.White.copy(0.7f)
    )
}

@Composable
private fun AnimatedLogo(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "logo")

    val breathe by transition.animateFloat(
        initialValue  = 0.96f,
        targetValue   = 1.04f,
        animationSpec = infiniteRepeatable(
            animation  = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe"
    )
    val aura by transition.animateFloat(
        initialValue  = 0.12f,
        targetValue   = 0.40f,
        animationSpec = infiniteRepeatable(
            animation  = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "aura"
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val r = size.minDimension * 0.52f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = aura),
                        Color.White.copy(alpha = aura * 0.25f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = r
                ),
                radius = r
            )
        }
        Image(
            painter            = painterResource(R.drawable.logo),
            contentDescription = "လိုဂို",
            modifier           = Modifier.fillMaxSize().scale(breathe)
        )
    }
}

// ── Login card ────────────────────────────────────────────────────────────────
@Composable
private fun LoginCard(
    username:          String,
    password:          String,
    pwVisible:         Boolean,
    loading:           Boolean,
    onUsernameChange:  (String) -> Unit,
    onPasswordChange:  (String) -> Unit,
    onTogglePwVisible: () -> Unit,
    onLogin:           () -> Unit,
) {
    val focusManager = LocalFocusManager.current
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
                enabled         = !loading,
                placeholder     = { Text("အသုံးပြုသူအမည်") },
                leadingIcon     = { Icon(Icons.Outlined.Person, contentDescription = null) },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                singleLine      = true,
                shape           = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.height(14.dp))

            FieldLabel("စကားဝှက်")
            OutlinedTextField(
                value                = password,
                onValueChange        = onPasswordChange,
                modifier             = Modifier.fillMaxWidth(),
                enabled              = !loading,
                placeholder          = { Text("စကားဝှက်") },
                leadingIcon          = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                trailingIcon         = {
                    IconButton(onClick = onTogglePwVisible, enabled = !loading) {
                        Icon(
                            if (pwVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = if (pwVisible) "စကားဝှက် ဖျောက်ရန်" else "စကားဝှက် ပြရန်"
                        )
                    }
                },
                keyboardOptions      = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Password,
                    imeAction    = ImeAction.Done
                ),
                keyboardActions      = KeyboardActions(onDone = { onLogin() }),
                visualTransformation = if (pwVisible) VisualTransformation.None
                                       else PasswordVisualTransformation(),
                singleLine           = true,
                shape                = RoundedCornerShape(12.dp)
            )

            Spacer(Modifier.height(20.dp))

            Button(
                onClick  = onLogin,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = Primary),
                enabled  = !loading
            ) {
                if (loading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("ဝင်ရောက်နေသည်...", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                } else {
                    Text("ဝင်ရောက်မည်", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted, letterSpacing = 0.5.sp)
    Spacer(Modifier.height(6.dp))
}

@Preview(
    name = "Technician Login",
    showBackground = true,
    showSystemUi = true,
    widthDp = 360,
    heightDp = 800
)
@Composable
private fun TechnicianLoginPreview() {
    AppTheme {
        TechnicianLoginLayout(
            username = "technician",
            password = "password",
            pwVisible = false,
            loading = false,
            error = "",
            onUsernameChange = {},
            onPasswordChange = {},
            onTogglePwVisible = {},
            onLogin = {},
            onClearError = {}
        )
    }
}

@Preview(
    name = "Technician Login Error",
    showBackground = true,
    showSystemUi = true,
    widthDp = 360,
    heightDp = 800
)
@Composable
private fun TechnicianLoginErrorPreview() {
    AppTheme {
        TechnicianLoginLayout(
            username = "technician",
            password = "wrong",
            pwVisible = false,
            loading = false,
            error = "Username သို့မဟုတ် Password မှားနေပါသည်",
            onUsernameChange = {},
            onPasswordChange = {},
            onTogglePwVisible = {},
            onLogin = {},
            onClearError = {}
        )
    }
}
@Composable
private fun ServiceIdentityRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ServiceIdentityChip("Computer", Icons.Outlined.Computer, Modifier.weight(1f))
        ServiceIdentityChip("Network", Icons.Outlined.Router, Modifier.weight(1f))
        ServiceIdentityChip("On-site", Icons.Outlined.HomeRepairService, Modifier.weight(1f))
    }
}

@Composable
private fun ServiceIdentityChip(label: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = Color.White.copy(alpha = 0.78f),
        border = androidx.compose.foundation.BorderStroke(1.dp, LoginAccent.copy(alpha = 0.14f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = null, tint = LoginAccent, modifier = Modifier.size(19.dp))
            Text(label, color = LoginInk, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}