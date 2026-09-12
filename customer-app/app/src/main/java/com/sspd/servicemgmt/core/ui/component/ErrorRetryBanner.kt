package com.sspd.servicemgmt.core.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sspd.servicemgmt.core.ui.theme.Danger
import com.sspd.servicemgmt.core.ui.theme.DangerBg
import com.sspd.servicemgmt.core.ui.theme.Primary

@Composable
fun ErrorRetryBanner(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    retryLabel: String = "ပြန်ကြိုးစားမည်"
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = DangerBg,
        border = BorderStroke(1.dp, Danger.copy(alpha = 0.22f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(message, color = Danger, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onRetry) {
                Text(retryLabel, fontWeight = FontWeight.Bold, color = Primary)
            }
        }
    }
}
