package com.sspd.servicemgmt.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sspd.servicemgmt.core.navigation.ComposePages
import com.sspd.servicemgmt.core.navigation.Screen
import com.sspd.servicemgmt.core.ui.theme.*
import com.sspd.servicemgmt.core.util.PreferenceManager

private data class SettingsEntry(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val route: String,
    val permission: String? = null,
    val adminOnly: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSecurityScreen(onBack: () -> Unit, onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    val prefs = remember(context) { PreferenceManager(context) }
    val isAdmin = prefs.hasRole("ADMINISTRATOR") || prefs.hasRole("ADMIN")
    val security = listOf(
        SettingsEntry("Account", "Profile and account information", Icons.Outlined.ManageAccounts, Screen.Account.route),
        SettingsEntry("Users", "Accounts, status and staff links", Icons.Outlined.Group, ComposePages.users, "CAN_ACCESS_USERS_READ"),
        SettingsEntry("Roles & Permissions", "Access control and permissions", Icons.Outlined.AdminPanelSettings, ComposePages.roles, "CAN_ACCESS_ROLES_READ"),
        SettingsEntry("Audit Logs", "Login and system activity history", Icons.Outlined.Policy, Screen.AuditLog.route, "CAN_ACCESS_AUDIT_LOG_READ"),
    )
    val system = listOf(
        SettingsEntry("Company Settings", "Business identity and contact details", Icons.Outlined.Business, ComposePages.company, adminOnly = true),
        SettingsEntry("Voucher & Print", "Receipt and document design", Icons.Outlined.ReceiptLong, ComposePages.voucher, adminOnly = true),
        SettingsEntry("Backup & Restore", "Database backup controls", Icons.Outlined.Backup, ComposePages.backup, "CAN_ACCESS_BACKUP_SETTINGS_READ"),
        SettingsEntry("App Version Management", "Manager and Technician APK releases", Icons.Outlined.AppSettingsAlt, Screen.SoftwareUpdate.route, "CAN_ACCESS_USERS_READ"),
        SettingsEntry("Software Update", "Check and install Manager updates", Icons.Outlined.SystemUpdate, Screen.SoftwareUpdate.route),
        SettingsEntry("About", "Application and build information", Icons.Outlined.Info, Screen.About.route),
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ဆက်တင်နှင့် လုံခြုံရေး", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Primary, titleContentColor = OnPrimary, navigationIconContentColor = OnPrimary),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SecuritySummary(prefs.displayName.ifBlank { prefs.username }, prefs.rolesStr)
            SettingsSection("လုံခြုံရေး", security.filter { allowed(it, prefs, isAdmin) }, onNavigate)
            SettingsSection("စနစ်ဆက်တင်", system.filter { allowed(it, prefs, isAdmin) }, onNavigate)
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun SecuritySummary(name: String, roles: String) {
    Card(colors = CardDefaults.cardColors(containerColor = PrimaryLight), border = BorderStroke(1.dp, Primary.copy(alpha = .25f)), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.VerifiedUser, null, tint = Primary, modifier = Modifier.size(34.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(name, fontWeight = FontWeight.ExtraBold, color = TextMain)
                Text(roles.ifBlank { "Authenticated user" }.replace("ROLE_", ""), fontSize = 12.sp, color = TextMuted)
                Text("Permission-based access is active", fontSize = 11.sp, color = Primary)
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, entries: List<SettingsEntry>, onNavigate: (String) -> Unit) {
    if (entries.isEmpty()) return
    Text(title, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = TextMuted, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
    Card(colors = CardDefaults.cardColors(containerColor = CardBg), border = BorderStroke(1.dp, BorderColor), shape = RoundedCornerShape(16.dp)) {
        entries.forEachIndexed { index, entry ->
            Row(modifier = Modifier.fillMaxWidth().clickable { onNavigate(entry.route) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(entry.icon, null, tint = Primary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(entry.title, fontWeight = FontWeight.Bold, color = TextMain)
                    Text(entry.subtitle, fontSize = 11.sp, color = TextMuted)
                }
                Icon(Icons.Outlined.ChevronRight, null, tint = TextMuted)
            }
            if (index < entries.lastIndex) HorizontalDivider(color = BorderColor, modifier = Modifier.padding(start = 54.dp))
        }
    }
}

private fun allowed(entry: SettingsEntry, prefs: PreferenceManager, isAdmin: Boolean): Boolean =
    (!entry.adminOnly || isAdmin) && (entry.permission == null || isAdmin || prefs.hasPermission(entry.permission))
