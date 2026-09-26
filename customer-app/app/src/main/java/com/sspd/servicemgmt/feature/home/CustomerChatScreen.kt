package com.sspd.servicemgmt.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.HeadsetMic
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sspd.servicemgmt.core.network.ChatMessage
import com.sspd.servicemgmt.core.ui.theme.AppTheme
import com.sspd.servicemgmt.core.ui.theme.BorderColor
import com.sspd.servicemgmt.core.ui.theme.CardBg
import com.sspd.servicemgmt.core.ui.theme.OnPrimary
import com.sspd.servicemgmt.core.ui.theme.Primary
import com.sspd.servicemgmt.core.ui.theme.PrimaryDark
import com.sspd.servicemgmt.core.ui.theme.PrimaryLight
import com.sspd.servicemgmt.core.ui.theme.ScreenBg
import com.sspd.servicemgmt.core.ui.theme.SurfaceSoft
import com.sspd.servicemgmt.core.ui.theme.TextMain
import com.sspd.servicemgmt.core.ui.theme.TextMuted

@Composable
fun CustomerChatScreen(
    messages: List<ChatMessage>,
    onSendMessage: (String) -> Unit,
    onBack: () -> Unit,
    showBottomNav: Boolean = true
) {
    var text by remember { mutableStateOf("") }
    val inspectionMode = LocalInspectionMode.current
    val listState = rememberLazyListState()
    val lastMessageId = messages.lastOrNull()?.id
    val messageCount = messages.size

    if (!inspectionMode) {
        BackHandler(onBack = onBack)
    }

    LaunchedEffect(messageCount, lastMessageId) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBg)
            .imePadding()
    ) {
        // Chat Support Header
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = CardBg,
            shadowElevation = 1.dp,
            border = BorderStroke(1.dp, BorderColor.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = TextMain
                    )
                }
                Spacer(Modifier.width(6.dp))

                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(PrimaryLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.SupportAgent,
                        contentDescription = null,
                        tint = Primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "SSPD Customer Support",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextMain
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF22C55E))
                        )
                        Text(
                            text = "Online · ဆိုင်နှင့် တိုက်ရိုက် ပြောဆိုရန်",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }
                }
            }
        }

        // Chat Messages List
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    SupportWelcomeCard()
                }
            } else {
                items(messages, key = { msg -> msg.id }) { msg ->
                    ChatBubble(msg)
                }
            }
        }

        // Chat Input Bar (Raised cleanly above Bottom Navigation)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = CardBg,
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, BorderColor)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (!showBottomNav) Modifier.navigationBarsPadding() else Modifier)
                    .padding(
                        start = 12.dp,
                        end = 12.dp,
                        top = 10.dp,
                        bottom = if (showBottomNav) 10.dp + 72.dp else 10.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(2000) },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("ဆိုင်သို့ စာရေးပါ…", style = MaterialTheme.typography.bodyMedium, color = TextMuted) },
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Primary,
                        unfocusedBorderColor = BorderColor,
                        focusedContainerColor = SurfaceSoft,
                        unfocusedContainerColor = SurfaceSoft,
                        cursorColor = Primary
                    ),
                    maxLines = 4
                )
                Spacer(Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        if (text.isNotBlank()) {
                            onSendMessage(text)
                            text = ""
                        }
                    },
                    enabled = text.isNotBlank(),
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (text.isNotBlank()) Primary else PrimaryLight)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Send,
                        contentDescription = "Send",
                        tint = if (text.isNotBlank()) OnPrimary else Primary.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SupportWelcomeCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, BorderColor.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(PrimaryLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.HeadsetMic,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(28.dp)
                )
            }

            Text(
                text = "SSPD Customer Support မှ ကြိုဆိုပါတယ်",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = PrimaryDark,
                textAlign = TextAlign.Center
            )

            Text(
                text = "ပစ္စည်းများ၊ Service Booking နှင့် အော်ဒါများအကြောင်း မေးခွန်းများ ရှိပါက စာရေးသား မေးမြန်းနိုင်ပါသည်။ ဆိုင်ဘက်မှ အမြန်ဆုံး ပြန်လည်ဖြေကြားပေးပါမည်။",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
fun ChatBubble(msg: ChatMessage) {
    val isAdmin = msg.isFromAdmin
    val alignment = if (isAdmin) Alignment.CenterStart else Alignment.CenterEnd
    val bubbleColor = if (isAdmin) SurfaceSoft else Primary
    val textColor = if (isAdmin) TextMain else OnPrimary
    val senderLabel = if (isAdmin) "SSPD" else null
    val timeLabel = formatChatTime(msg.createdAt)

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(0.85f),
            horizontalArrangement = if (isAdmin) Arrangement.Start else Arrangement.End,
            verticalAlignment = Alignment.Bottom
        ) {
            if (isAdmin) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(PrimaryLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.SupportAgent,
                        contentDescription = null,
                        tint = Primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(Modifier.width(6.dp))
            }

            Surface(
                color = bubbleColor,
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isAdmin) 4.dp else 16.dp,
                    bottomEnd = if (isAdmin) 16.dp else 4.dp
                ),
                border = if (isAdmin) BorderStroke(1.dp, BorderColor) else null
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = msg.text,
                        color = textColor,
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 20.sp
                    )
                    Row(
                        modifier = Modifier.align(Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val meta = listOfNotNull(senderLabel, timeLabel).joinToString(" · ")
                        if (meta.isNotBlank()) {
                            Text(
                                text = meta,
                                color = if (isAdmin) TextMuted else OnPrimary.copy(alpha = 0.75f),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp
                            )
                        }
                        if (!isAdmin) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = null,
                                tint = OnPrimary.copy(alpha = 0.85f),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatChatTime(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val timePart = when {
        'T' in raw -> raw.substringAfter('T')
        ' ' in raw -> raw.substringAfterLast(' ')
        else -> raw
    }.substringBefore('.')
    val match = Regex("""(\d{1,2}):(\d{2})""").find(timePart) ?: return null
    val hour = match.groupValues[1].padStart(2, '0')
    val minute = match.groupValues[2]
    return "$hour:$minute"
}

@Preview(showBackground = true, backgroundColor = 0xFFF8F9FA, widthDp = 390, heightDp = 844)
@Composable
private fun CustomerChatScreenPreview() {
    AppTheme {
        CustomerChatScreen(
            messages = listOf(
                ChatMessage(
                    id = 1,
                    text = "မင်္ဂလာပါ ဆိုင်က ပစ္စည်းများအကြောင်း မေးမြန်းလိုပါသလားခင်ဗျာ။",
                    isFromAdmin = true,
                    createdAt = "2026-09-22T10:30:00"
                ),
                ChatMessage(
                    id = 2,
                    text = "ဟုတ်ကဲ့၊ Laptop Battery အပိုပစ္စည်း ရနိုင်ပါသလား။",
                    isFromAdmin = false,
                    createdAt = "2026-09-22T10:31:00"
                )
            ),
            onSendMessage = {},
            onBack = {}
        )
    }
}
