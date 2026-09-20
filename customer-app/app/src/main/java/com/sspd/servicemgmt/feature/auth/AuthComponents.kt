package com.sspd.servicemgmt.feature.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.sspd.servicemgmt.R
import com.sspd.servicemgmt.core.ui.theme.BorderColor
import com.sspd.servicemgmt.core.ui.theme.CardBg
import com.sspd.servicemgmt.core.ui.theme.Danger
import com.sspd.servicemgmt.core.ui.theme.DangerBg
import com.sspd.servicemgmt.core.ui.theme.OnPrimary
import com.sspd.servicemgmt.core.ui.theme.Primary
import com.sspd.servicemgmt.core.ui.theme.Success
import com.sspd.servicemgmt.core.ui.theme.SuccessBg
import com.sspd.servicemgmt.core.ui.theme.SurfaceSoft
import com.sspd.servicemgmt.core.ui.theme.TextMain
import com.sspd.servicemgmt.core.ui.theme.TextMuted
import com.sspd.servicemgmt.core.util.PreferenceManager

private val FieldShape = RoundedCornerShape(12.dp)
private val PanelShape = RoundedCornerShape(20.dp)

@Composable
fun CompanyLogoImage(
    logoUrl: String = "",
    logoBytes: ByteArray? = null,
    logoBase64: String = "",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // Don't use ByteArray itself as remember key (content equals is unreliable for keys).
    val logoBytesKey = logoBytes?.size ?: -1
    val model: Any? = remember(logoUrl, logoBytesKey, logoBase64) {
        when {
            logoBytes != null && logoBytes.isNotEmpty() -> logoBytes as Any
            logoUrl.isNotBlank() -> logoUrl
            else -> PreferenceManager.decodeLogoBase64(logoBase64)
        }
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(CardBg)
            .border(1.dp, BorderColor.copy(alpha = 0.65f), RoundedCornerShape(16.dp))
            .padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        if (model != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(model)
                    .crossfade(true)
                    .build(),
                contentDescription = "Company logo",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
                error = painterResource(R.drawable.ic_logo),
                placeholder = painterResource(R.drawable.ic_logo),
                fallback = painterResource(R.drawable.ic_logo)
            )
        } else {
            Image(
                painter = painterResource(R.drawable.ic_logo),
                contentDescription = "SSPD logo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
    }
}

@Composable
fun AuthModeSelector(mode: AuthMode, onModeChange: (AuthMode) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceSoft)
            .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
            .padding(3.dp)
    ) {
        listOf(AuthMode.LOGIN to "ဝင်ရန်", AuthMode.REGISTER to "ဖွင့်ရန်")
            .forEach { (item, label) ->
                val selected = mode == item
                Surface(
                    onClick = { onModeChange(item) },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = if (selected) Primary else Color.Transparent,
                    shadowElevation = 0.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = label,
                            color = if (selected) OnPrimary else TextMuted,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            style = MaterialTheme.typography.labelLarge,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
    }
}

@Composable
fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    keyboardType: KeyboardType,
    password: Boolean = false,
    passwordVisible: Boolean = false,
    onPasswordVisibilityChange: (() -> Unit)? = null,
    imeAction: ImeAction = ImeAction.Next,
    onDone: (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = {
            Icon(icon, contentDescription = null, tint = if (value.isNotBlank()) Primary else TextMuted)
        },
        trailingIcon = if (password) {
            {
                IconButton(onClick = { onPasswordVisibilityChange?.invoke() }) {
                    Icon(
                        if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = if (passwordVisible) "စကားဝှက် ဖျောက်ရန်" else "စကားဝှက် ပြရန်",
                        tint = TextMuted
                    )
                }
            }
        } else null,
        visualTransformation = if (password && !passwordVisible) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = FieldShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Primary,
            unfocusedBorderColor = BorderColor,
            focusedLabelColor = Primary,
            unfocusedLabelColor = TextMuted,
            cursorColor = Primary,
            focusedContainerColor = CardBg,
            unfocusedContainerColor = SurfaceSoft
        )
    )
}

@Composable
fun AuthSectionHeader(title: String, subtitle: String) {
    androidx.compose.foundation.layout.Column(
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = TextMain,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted
        )
    }
}

@Composable
fun AuthFeedbackMessage(error: String?, notice: String?) {
    when {
        error != null -> Surface(
            color = DangerBg,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Danger.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(Icons.Outlined.ErrorOutline, null, tint = Danger, modifier = Modifier.size(18.dp))
                Text(error, color = Danger, style = MaterialTheme.typography.bodySmall)
            }
        }
        notice != null -> Surface(
            color = SuccessBg,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Success.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(Icons.Outlined.CheckCircle, null, tint = Success, modifier = Modifier.size(18.dp))
                Text(notice, color = Success, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun AuthPrimaryButton(
    text: String,
    loadingText: String,
    loading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    leadingIcon: ImageVector? = null
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Primary,
            contentColor = OnPrimary,
            disabledContainerColor = Primary.copy(alpha = 0.35f),
            disabledContentColor = OnPrimary.copy(alpha = 0.85f)
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            disabledElevation = 0.dp
        )
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = OnPrimary,
                strokeWidth = 2.dp
            )
            Spacer(Modifier.width(10.dp))
            Text(loadingText, fontWeight = FontWeight.SemiBold)
        } else {
            if (leadingIcon != null) {
                Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun AuthFormPanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = PanelShape,
        color = CardBg,
        tonalElevation = 0.dp,
        shadowElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor)
    ) {
        Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp)) {
            content()
        }
    }
}
