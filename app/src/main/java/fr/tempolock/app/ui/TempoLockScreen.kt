package fr.tempolock.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import fr.tempolock.app.BuildConfig
import fr.tempolock.app.receiver.TempoLockDeviceAdminReceiver
import fr.tempolock.app.TempoLockUiState
import fr.tempolock.app.domain.InstalledApp
import fr.tempolock.app.domain.LockPhase
import fr.tempolock.app.domain.LockSession
import fr.tempolock.app.domain.LockStatus
import fr.tempolock.app.ui.theme.Amber
import fr.tempolock.app.ui.theme.Mint
import fr.tempolock.app.ui.theme.Night
import fr.tempolock.app.ui.theme.Sky
import fr.tempolock.app.ui.theme.TempoLockTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TempoLockApp(
    state: TempoLockUiState,
    onRefresh: () -> Unit,
    onSelectApp: (InstalledApp) -> Unit,
    onDurationChange: (Long) -> Unit,
    onStartLock: () -> Unit,
    onDismissError: () -> Unit,
    onRequestExactAlarm: () -> Unit,
) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var showConfirmation by rememberSaveable { mutableStateOf(false) }

    val baseBackground = MaterialTheme.colorScheme.background
    val background = Brush.verticalGradient(
        listOf(
            baseBackground,
            baseBackground,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.07f).compositeOver(baseBackground),
        ),
    )

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) { scaffoldPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(background)
                .padding(scaffoldPadding),
        ) {
            LazyColumn(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxHeight()
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .testTag("main_screen"),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 18.dp,
                    bottom = 28.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                item { TempoHeader(status = state.status) }

                state.errorMessage?.let { message ->
                    item {
                        ErrorCard(message = message, onDismiss = onDismissError)
                    }
                }

                when (val status = state.status) {
                    LockStatus.OwnerRequired -> item {
                        ProvisioningCard(onRefresh = onRefresh)
                    }

                    LockStatus.Idle -> {
                        item { ReadyHero() }
                        item {
                            TargetSelector(
                                selectedApp = state.selectedApp,
                                onClick = { showPicker = true },
                            )
                        }
                        item {
                            DurationSelector(
                                minutes = state.durationMinutes,
                                onMinutesChange = onDurationChange,
                            )
                        }
                        if (!state.exactAlarmsAllowed) {
                            item { ExactAlarmCard(onRequestExactAlarm) }
                        }
                        item {
                            ArmButton(
                                enabled = state.selectedApp != null &&
                                    state.exactAlarmsAllowed &&
                                    !state.isWorking,
                                isWorking = state.isWorking,
                                onClick = { showConfirmation = true },
                            )
                        }
                    }

                    is LockStatus.Active -> item {
                        ActiveLockCard(status.session, status.remainingMillis)
                    }

                    is LockStatus.Fault -> item {
                        FaultCard(message = status.message, onRefresh = onRefresh)
                    }
                }

                item { Footer() }
            }
        }
    }

    if (showPicker) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showPicker = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            AppPicker(
                apps = state.installedApps,
                onSelect = {
                    onSelectApp(it)
                    showPicker = false
                },
            )
        }
    }

    if (showConfirmation) {
        ConfirmationDialog(
            app = state.selectedApp,
            durationMinutes = state.durationMinutes,
            onDismiss = { showConfirmation = false },
            onConfirm = {
                showConfirmation = false
                onStartLock()
            },
        )
    }
}

@Composable
private fun TempoHeader(status: LockStatus) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShieldMark(modifier = Modifier.size(42.dp), locked = status is LockStatus.Active)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "TempoLock",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = "DÉCISION PROTÉGÉE",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.4.sp,
            )
        }
        StatusPill(
            text = when (status) {
                LockStatus.OwnerRequired -> "À ACTIVER"
                LockStatus.Idle -> "PRÊT"
                is LockStatus.Active -> "VERROUILLÉ"
                is LockStatus.Fault -> "À VÉRIFIER"
            },
            color = when (status) {
                LockStatus.OwnerRequired -> Amber
                LockStatus.Idle -> Mint
                is LockStatus.Active -> Sky
                is LockStatus.Fault -> MaterialTheme.colorScheme.error
            },
        )
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.13f),
        contentColor = color,
        shape = RoundedCornerShape(100),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelLarge,
            fontSize = 11.sp,
            letterSpacing = 0.8.sp,
        )
    }
}

@Composable
private fun ReadyHero() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Choisis maintenant.\nNégocie plus tard : impossible.",
            style = MaterialTheme.typography.headlineLarge,
        )
        Text(
            text = "Une fois confirmé, le délai ne peut plus être raccourci ni annulé.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun ProvisioningCard(onRefresh: () -> Unit) {
    val context = LocalContext.current
    val command =
        "adb shell dpm set-device-owner ${BuildConfig.APPLICATION_ID}/" +
            TempoLockDeviceAdminReceiver::class.java.name

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
            ),
            shape = RoundedCornerShape(28.dp),
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("Activation forte", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Android doit confier à TempoLock la gestion complète de cet appareil. " +
                        "Une simple autorisation administrateur ne suffit pas.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                SecurityLine("Installe l'APK sur un téléphone neuf ou réinitialisé.")
                SecurityLine("Ne connecte encore aucun compte Google.")
                SecurityLine("Exécute ensuite la commande ADB ci-dessous.")
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(18.dp),
        ) {
            Text(
                text = command,
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(16.dp),
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                maxLines = 1,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Commande TempoLock", command))
                },
                modifier = Modifier.weight(1f),
            ) {
                Text("Copier la commande")
            }
            OutlinedButton(onClick = onRefresh) {
                Text("Vérifier")
            }
        }

        Text(
            "Important : un effacement complet par recovery, le root ou un bootloader déverrouillé " +
                "restent hors du contrôle de toute APK.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun SecurityLine(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .padding(top = 7.dp)
                .size(7.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
        Spacer(Modifier.width(10.dp))
        Text(text, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun TargetSelector(selectedApp: InstalledApp?, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("target_selector"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(
                packageName = selectedApp?.packageName,
                fallback = selectedApp?.label?.firstOrNull()?.uppercase() ?: "+",
                modifier = Modifier.size(54.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "APPLICATION CIBLE",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp,
                )
                Text(
                    selectedApp?.label ?: "Choisir une application",
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                selectedApp?.let {
                    Text(
                        it.packageName,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text("CHOISIR", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DurationSelector(minutes: Long, onMinutesChange: (Long) -> Unit) {
    val presets = listOf(
        1L to "1 min",
        15L to "15 min",
        30L to "30 min",
        60L to "1 h",
        120L to "2 h",
        240L to "4 h",
        720L to "12 h",
        1_440L to "1 j",
        4_320L to "3 j",
        10_080L to "7 j",
    )

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "DURÉE DU VERROU",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.sp,
                    )
                    Text(formatDuration(minutes), style = MaterialTheme.typography.headlineMedium)
                }
                Text(
                    "modifiable avant confirmation",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }

            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(presets, key = { it.first }) { (value, label) ->
                    FilterChip(
                        selected = minutes == value,
                        onClick = { onMinutesChange(value) },
                        label = { Text(label) },
                    )
                }
            }

            Column(modifier = Modifier.padding(horizontal = 18.dp)) {
                Text(
                    "Réglage fin : ${minutes.coerceAtMost(1_440)} min",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
                Slider(
                    value = minutes.coerceIn(1L, 1_440L).toFloat(),
                    onValueChange = { onMinutesChange(it.roundToLong()) },
                    valueRange = 1f..1_440f,
                    steps = 286,
                    modifier = Modifier.testTag("duration_slider"),
                )
            }
        }
    }
}

@Composable
private fun ExactAlarmCard(onRequest: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.13f),
        ),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Fin automatique à autoriser", style = MaterialTheme.typography.titleLarge)
            Text(
                "Sans l'autorisation d'alarme exacte, Android peut retarder le déblocage. " +
                    "TempoLock n'autorise donc pas le démarrage du cycle.",
            )
            Button(onClick = onRequest) { Text("Autoriser l'alarme exacte") }
        }
    }
}

@Composable
private fun ArmButton(enabled: Boolean, isWorking: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .testTag("prepare_lock_button"),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
    ) {
        if (isWorking) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Text("Préparer le verrouillage", style = MaterialTheme.typography.labelLarge)
        }
    }
    Text(
        "Après la confirmation finale, il n'y aura ni bouton d'arrêt, ni code secret, ni raccourci.",
        modifier = Modifier.padding(top = 9.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        fontSize = 13.sp,
    )
}

@Composable
private fun ActiveLockCard(session: LockSession, remainingMillis: Long) {
    val progress = (remainingMillis.toFloat() / session.durationMillis.toFloat()).coerceIn(0f, 1f)
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(
                packageName = session.targetPackage,
                fallback = session.targetLabel.firstOrNull()?.uppercase() ?: "A",
                modifier = Modifier.size(58.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column {
                Text("VERROU ACTIF", color = Sky, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text(session.targetLabel, style = MaterialTheme.typography.headlineMedium)
                Text(
                    session.targetPackage,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth(0.78f)
                .widthIn(max = 330.dp)
                .aspectRatio(1f)
                .semantics { contentDescription = "Temps restant ${formatCountdown(remainingMillis)}" }
                .testTag("countdown_ring"),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 15.dp.toPx()
                val inset = stroke / 2
                drawArc(
                    color = Color.White.copy(alpha = 0.08f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                drawArc(
                    brush = Brush.sweepGradient(listOf(Sky, Mint, Sky)),
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "TEMPS RESTANT",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.4.sp,
                )
                Text(
                    formatCountdown(remainingMillis),
                    fontFamily = FontFamily.Monospace,
                    fontSize = if (remainingMillis >= 24 * 60 * 60 * 1_000L) 31.sp else 38.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Fin prévue", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    formatDeadline(session.deadlineEpochMillis),
                    style = MaterialTheme.typography.titleLarge,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                SecurityLine("Lancement et notifications de l'application suspendus")
                SecurityLine("Désinstallation, arrêt forcé et effacement des données bloqués")
                SecurityLine("Heure, débogage USB, mode sans échec et nouveaux utilisateurs verrouillés")
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.10f),
            shape = RoundedCornerShape(18.dp),
        ) {
            Text(
                "Ce délai est désormais immuable. TempoLock libérera automatiquement " +
                    "${session.targetLabel} à l'échéance.",
                modifier = Modifier.padding(16.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun FaultCard(message: String, onRefresh: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.13f),
        ),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Protection en attente", style = MaterialTheme.typography.headlineMedium)
            Text(message)
            Text(
                "Par sécurité, aucune action de déblocage manuel n'est proposée.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onRefresh) { Text("Réessayer la vérification") }
        }
    }
}

@Composable
private fun ErrorCard(message: String, onDismiss: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
        ),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(message, modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text("Fermer") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppPicker(apps: List<InstalledApp>, onSelect: (InstalledApp) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val filtered = remember(apps, query) {
        apps.filter {
            it.label.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        Text("Choisir l'application", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            "Seules les applications utilisateur lançables sont affichées.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("app_search"),
            label = { Text("Rechercher") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            shape = RoundedCornerShape(16.dp),
        )
        Spacer(Modifier.height(10.dp))

        if (filtered.isEmpty()) {
            Text(
                if (apps.isEmpty()) {
                    "Aucune application utilisateur compatible n'a été trouvée."
                } else {
                    "Aucun résultat."
                },
                modifier = Modifier.padding(vertical = 28.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(modifier = Modifier.height(420.dp)) {
                items(filtered, key = InstalledApp::packageName) { app ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(app) }
                            .padding(vertical = 11.dp)
                            .testTag("app_${app.packageName}"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIcon(
                            packageName = app.packageName,
                            fallback = app.label.firstOrNull()?.uppercase() ?: "A",
                            modifier = Modifier.size(44.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(app.label, fontWeight = FontWeight.SemiBold)
                            Text(
                                app.packageName,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ConfirmationDialog(
    app: InstalledApp?,
    durationMinutes: Long,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    var phrase by rememberSaveable { mutableStateOf("") }
    val deadline = System.currentTimeMillis() + durationMinutes * 60_000L

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirmation irrévocable") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "${app?.label ?: "L'application"} sera inaccessible jusqu'au " +
                        formatDeadline(deadline) + ".",
                )
                Text(
                    "Le délai ne pourra plus être raccourci, même depuis TempoLock.",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                )
                Text("Saisis VERROUILLER pour confirmer.")
                OutlinedTextField(
                    value = phrase,
                    onValueChange = { phrase = it.uppercase(Locale.FRENCH) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("confirmation_input"),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = phrase.trim() == CONFIRMATION_PHRASE,
                modifier = Modifier.testTag("confirm_lock_button"),
            ) {
                Text("Verrouiller maintenant")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Revenir") }
        },
    )
}

@Composable
private fun AppIcon(packageName: String?, fallback: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap = remember(packageName) {
        packageName?.let {
            runCatching {
                context.packageManager.getApplicationIcon(it).toBitmap(128, 128).asImageBitmap()
            }.getOrNull()
        }
    }

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.13f),
        shape = RoundedCornerShape(16.dp),
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = packageName?.let { "Icône de $it" },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(5.dp),
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    fallback,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp,
                )
            }
        }
    }
}

@Composable
private fun ShieldMark(modifier: Modifier = Modifier, locked: Boolean) {
    val primary = if (locked) Sky else Mint
    val background = MaterialTheme.colorScheme.surface
    Canvas(
        modifier = modifier.semantics {
            contentDescription = if (locked) "TempoLock verrouillé" else "Logo TempoLock"
        },
    ) {
        val w = size.width
        val h = size.height
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.5f, h * 0.04f)
            lineTo(w * 0.9f, h * 0.2f)
            lineTo(w * 0.86f, h * 0.63f)
            quadraticTo(w * 0.76f, h * 0.84f, w * 0.5f, h * 0.97f)
            quadraticTo(w * 0.24f, h * 0.84f, w * 0.14f, h * 0.63f)
            lineTo(w * 0.1f, h * 0.2f)
            close()
        }
        drawPath(path, primary)
        drawCircle(background, radius = w * 0.16f, center = Offset(w * 0.5f, h * 0.53f))
        drawRoundRect(
            color = background,
            topLeft = Offset(w * 0.37f, h * 0.51f),
            size = Size(w * 0.26f, h * 0.24f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.04f),
        )
        drawArc(
            color = background,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(w * 0.39f, h * 0.34f),
            size = Size(w * 0.22f, h * 0.28f),
            style = Stroke(width = w * 0.07f, cap = StrokeCap.Round),
        )
    }
}

@Composable
private fun Footer() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "TempoLock ${BuildConfig.VERSION_NAME}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
        )
        Text(
            "100 % local · aucune donnée envoyée",
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
            fontSize = 11.sp,
        )
    }
}

private fun formatDuration(minutes: Long): String = when {
    minutes < 60 -> "$minutes min"
    minutes < 24 * 60 -> {
        val hours = minutes / 60
        val remainder = minutes % 60
        if (remainder == 0L) "$hours h" else "$hours h $remainder min"
    }
    else -> {
        val days = minutes / (24 * 60)
        val hours = (minutes % (24 * 60)) / 60
        if (hours == 0L) "$days j" else "$days j $hours h"
    }
}

fun formatCountdown(milliseconds: Long): String {
    val totalSeconds = (milliseconds.coerceAtLeast(0L) + 999L) / 1_000L
    val days = totalSeconds / 86_400L
    val hours = (totalSeconds % 86_400L) / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (days > 0) {
        "%dj %02d:%02d:%02d".format(Locale.ROOT, days, hours, minutes, seconds)
    } else {
        "%02d:%02d:%02d".format(Locale.ROOT, hours, minutes, seconds)
    }
}

private fun formatDeadline(epochMillis: Long): String =
    DateTimeFormatter
        .ofPattern("EEEE d MMMM yyyy 'à' HH:mm", Locale.FRENCH)
        .format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.FRENCH) else it.toString() }

private const val CONFIRMATION_PHRASE = "VERROUILLER"

@Preview(name = "Accueil sombre", widthDp = 400, heightDp = 1000, showBackground = true)
@Composable
private fun TempoLockIdlePreview() {
    TempoLockTheme(darkTheme = true) {
        TempoLockApp(
            state = TempoLockUiState(
                status = LockStatus.Idle,
                selectedApp = InstalledApp("com.snapchat.android", "Snapchat"),
                durationMinutes = 120,
                exactAlarmsAllowed = true,
                isWorking = false,
            ),
            onRefresh = {},
            onSelectApp = {},
            onDurationChange = {},
            onStartLock = {},
            onDismissError = {},
            onRequestExactAlarm = {},
        )
    }
}

@Preview(name = "Verrou actif", widthDp = 400, heightDp = 1000, showBackground = true)
@Composable
private fun TempoLockActivePreview() {
    val now = System.currentTimeMillis()
    TempoLockTheme(darkTheme = true) {
        TempoLockApp(
            state = TempoLockUiState(
                status = LockStatus.Active(
                    session = LockSession(
                        targetPackage = "com.snapchat.android",
                        targetLabel = "Snapchat",
                        durationMillis = 7_200_000,
                        startedAtEpochMillis = now,
                        startedAtElapsedMillis = 0,
                        bootCountAtStart = 1,
                        deadlineEpochMillis = now + 7_200_000,
                        deadlineElapsedMillis = 7_200_000,
                        phase = LockPhase.ACTIVE,
                    ),
                    remainingMillis = 5_247_000,
                ),
                isWorking = false,
            ),
            onRefresh = {},
            onSelectApp = {},
            onDurationChange = {},
            onStartLock = {},
            onDismissError = {},
            onRequestExactAlarm = {},
        )
    }
}
