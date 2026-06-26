package com.sspd.servicemgmt.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
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
import com.sspd.servicemgmt.R
import com.sspd.servicemgmt.ui.theme.*
import com.sspd.servicemgmt.ui.viewmodel.LoginViewModel
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

    var username  by remember { mutableStateOf("") }
    var password  by remember { mutableStateOf("") }
    var pwVisible by remember { mutableStateOf(false) }

    LaunchedEffect(state.loginSuccess) {
        if (state.loginSuccess) onSuccess()
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
    Text("ကုန်ပစ္စည်းနှင့် ရောင်းချမှုစနစ်", fontSize = 12.sp, color = Color.White.copy(0.7f))
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
