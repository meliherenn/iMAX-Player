package com.imax.player.ui.settings

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.imax.player.core.designsystem.theme.ImaxColors
import com.imax.player.core.security.ParentalControlManager

// ─────────────────────────────────────────────────────────────────────────────
// Parental Control Settings Section
// Plug into SettingsContent as a SettingsSection block.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ParentalControlSection(
    parentalControlManager: ParentalControlManager,
    isTv: Boolean
) {
    // Observed state — re-read on each recomposition triggered by refresh key
    var refreshKey by remember { mutableIntStateOf(0) }
    val isPinSet by remember(refreshKey) { mutableStateOf(parentalControlManager.isPinSet) }
    val isChildLock by remember(refreshKey) { mutableStateOf(parentalControlManager.isChildLockEnabled) }
    val whitelistCategories by remember(refreshKey) { mutableStateOf(parentalControlManager.getWhitelistedCategories()) }

    // Dialog states
    var showSetPinDialog by remember { mutableStateOf(false) }
    var showVerifyPinDialog by remember { mutableStateOf(false) }
    var verifyPinPurpose by remember { mutableStateOf(VerifyPurpose.TOGGLE_LOCK) }
    var showCategorySheet by remember { mutableStateOf(false) }
    var showClearPinDialog by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {

        // ── Child lock toggle ────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Transparent)
                .clickable {
                    if (!isPinSet) {
                        // Must set PIN first
                        showSetPinDialog = true
                    } else {
                        verifyPinPurpose = VerifyPurpose.TOGGLE_LOCK
                        showVerifyPinDialog = true
                    }
                }
                .padding(horizontal = 4.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isChildLock) Icons.Filled.Lock else Icons.Filled.LockOpen,
                contentDescription = null,
                tint = if (isChildLock) ImaxColors.Primary else ImaxColors.TextSecondary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Çocuk Kilidi",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ImaxColors.TextPrimary
                )
                Text(
                    if (!isPinSet) "PIN ayarlanmamış — önce PIN belirleyin"
                    else if (isChildLock) "Aktif — sadece izin verilen kategoriler görünür"
                    else "Pasif",
                    style = MaterialTheme.typography.bodySmall,
                    color = ImaxColors.TextTertiary
                )
            }
            Switch(
                checked = isChildLock,
                onCheckedChange = null, // handled via click
                colors = SwitchDefaults.colors(
                    checkedThumbColor = ImaxColors.Primary,
                    checkedTrackColor = ImaxColors.Primary.copy(alpha = 0.3f)
                )
            )
        }

        HorizontalDivider(color = ImaxColors.DividerColor, modifier = Modifier.padding(vertical = 2.dp))

        // ── PIN controls ─────────────────────────────────────────────────────
        if (isPinSet) {
            ParentalActionRow(
                icon = Icons.Filled.Pin,
                label = "PIN Değiştir",
                onClick = { showSetPinDialog = true }
            )
            ParentalActionRow(
                icon = Icons.Filled.DeleteForever,
                label = "PIN'i Sil",
                isDanger = true,
                onClick = { showClearPinDialog = true }
            )
        } else {
            ParentalActionRow(
                icon = Icons.Filled.Pin,
                label = "PIN Belirle",
                onClick = { showSetPinDialog = true }
            )
        }

        // ── Whitelist categories ──────────────────────────────────────────────
        ParentalActionRow(
            icon = Icons.Filled.Category,
            label = "İzin Verilen Kategoriler",
            subtitle = if (whitelistCategories.isEmpty()) "Hiçbiri seçilmedi"
            else whitelistCategories.joinToString(", "),
            onClick = { showCategorySheet = true }
        )
    }

    // ── Dialogs / Sheets ─────────────────────────────────────────────────────

    if (showSetPinDialog) {
        SetPinDialog(
            onConfirm = { newPin ->
                parentalControlManager.setPin(newPin)
                refreshKey++
                showSetPinDialog = false
            },
            onDismiss = { showSetPinDialog = false }
        )
    }

    if (showVerifyPinDialog) {
        VerifyPinDialog(
            title = when (verifyPinPurpose) {
                VerifyPurpose.TOGGLE_LOCK -> if (isChildLock) "Kilidi Kapatmak İçin PIN" else "Kilidi Açmak İçin PIN"
                VerifyPurpose.CLEAR_PIN -> "PIN'i Silmek İçin Mevcut PIN"
            },
            onVerify = { pin ->
                val ok = parentalControlManager.verifyPin(pin)
                if (ok) {
                    when (verifyPinPurpose) {
                        VerifyPurpose.TOGGLE_LOCK -> {
                            parentalControlManager.setChildLock(!isChildLock)
                            refreshKey++
                        }
                        VerifyPurpose.CLEAR_PIN -> {
                            parentalControlManager.clearPin()
                            refreshKey++
                        }
                    }
                }
                ok
            },
            onDismiss = { showVerifyPinDialog = false }
        )
    }

    if (showClearPinDialog) {
        AlertDialog(
            onDismissRequest = { showClearPinDialog = false },
            icon = { Icon(Icons.Filled.Warning, contentDescription = null, tint = ImaxColors.Error) },
            title = { Text("PIN'i Sil") },
            text = { Text("PIN silinince çocuk kilidi de devre dışı kalacak. Emin misiniz?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearPinDialog = false
                        verifyPinPurpose = VerifyPurpose.CLEAR_PIN
                        showVerifyPinDialog = true
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = ImaxColors.Error)
                ) { Text("Evet, Sil") }
            },
            dismissButton = {
                TextButton(onClick = { showClearPinDialog = false }) { Text("İptal") }
            },
            containerColor = ImaxColors.Surface,
            titleContentColor = ImaxColors.TextPrimary,
            textContentColor = ImaxColors.TextSecondary
        )
    }

    if (showCategorySheet) {
        CategoryWhitelistSheet(
            whitelisted = whitelistCategories,
            onToggle = { cat, allowed ->
                if (allowed) parentalControlManager.addToWhitelist(cat)
                else parentalControlManager.removeFromWhitelist(cat)
                refreshKey++
            },
            onDismiss = { showCategorySheet = false }
        )
    }
}

// ─── PIN purpose enum ────────────────────────────────────────────────────────
private enum class VerifyPurpose { TOGGLE_LOCK, CLEAR_PIN }

// ─── Set PIN Dialog ──────────────────────────────────────────────────────────
@Composable
private fun SetPinDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = ImaxColors.Surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = ImaxColors.Primary, modifier = Modifier.size(36.dp))
                Text("PIN Belirle", style = MaterialTheme.typography.titleMedium, color = ImaxColors.TextPrimary)
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) pin = it },
                    label = { Text("4 haneli PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ImaxColors.Primary,
                        focusedLabelColor = ImaxColors.Primary
                    )
                )
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) confirmPin = it },
                    label = { Text("PIN Tekrar") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ImaxColors.Primary,
                        focusedLabelColor = ImaxColors.Primary
                    )
                )
                error?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = ImaxColors.Error)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("İptal") }
                    Button(
                        onClick = {
                            when {
                                pin.length != 4 -> error = "PIN 4 haneli olmalı"
                                pin != confirmPin -> error = "PIN'ler eşleşmiyor"
                                else -> onConfirm(pin)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = ImaxColors.Primary)
                    ) { Text("Kaydet") }
                }
            }
        }
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

// ─── Verify PIN Dialog ───────────────────────────────────────────────────────
@Composable
private fun VerifyPinDialog(
    title: String,
    onVerify: (String) -> Boolean,
    onDismiss: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = ImaxColors.Surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = ImaxColors.Primary, modifier = Modifier.size(36.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, color = ImaxColors.TextPrimary, textAlign = TextAlign.Center)
                OutlinedTextField(
                    value = pin,
                    onValueChange = {
                        if (it.length <= 4 && it.all { c -> c.isDigit() }) {
                            pin = it
                            error = false
                        }
                    },
                    label = { Text("PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    isError = error,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ImaxColors.Primary,
                        errorBorderColor = ImaxColors.Error
                    )
                )
                if (error) {
                    Text("Hatalı PIN", style = MaterialTheme.typography.labelSmall, color = ImaxColors.Error)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("İptal") }
                    Button(
                        onClick = {
                            val ok = onVerify(pin)
                            if (ok) onDismiss() else error = true
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = ImaxColors.Primary)
                    ) { Text("Doğrula") }
                }
            }
        }
    }
}

// ─── Category Whitelist Bottom Sheet ─────────────────────────────────────────
private val DEFAULT_CATEGORIES = listOf(
    "Haber", "Spor", "Belgesel", "Çocuk", "Eğitim", "Müzik", "Film", "Dizi", "Genel"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryWhitelistSheet(
    whitelisted: Set<String>,
    onToggle: (String, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = ImaxColors.Surface,
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Category, contentDescription = null, tint = ImaxColors.Primary)
                Text("İzin Verilen Kategoriler", style = MaterialTheme.typography.titleMedium, color = ImaxColors.TextPrimary)
            }
            Text(
                "Çocuk kilidi aktifken bu kategoriler görüntülenebilir.",
                style = MaterialTheme.typography.bodySmall,
                color = ImaxColors.TextTertiary,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )
            DEFAULT_CATEGORIES.forEach { cat ->
                val isAllowed = whitelisted.contains(cat.lowercase())
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onToggle(cat, !isAllowed) }
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(cat, style = MaterialTheme.typography.bodyMedium, color = ImaxColors.TextPrimary)
                    Checkbox(
                        checked = isAllowed,
                        onCheckedChange = { onToggle(cat, it) },
                        colors = CheckboxDefaults.colors(checkedColor = ImaxColors.Primary)
                    )
                }
            }
        }
    }
}

// ─── Small action row helper ──────────────────────────────────────────────────
@Composable
private fun ParentalActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    subtitle: String? = null,
    isDanger: Boolean = false,
    onClick: () -> Unit
) {
    val tint = if (isDanger) ImaxColors.Error else ImaxColors.TextPrimary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = tint)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = ImaxColors.TextTertiary, maxLines = 1)
            }
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = ImaxColors.TextTertiary, modifier = Modifier.size(18.dp))
    }
}
