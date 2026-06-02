package com.example.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.Session
import com.example.data.Transcript
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.sin

@Composable
fun JaleesAppContent(viewModel: JaleesViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Crossfade(targetState = currentScreen, label = "ScreenTransition") { screen ->
            when (screen) {
                is AppScreen.Onboarding -> OnboardingScreen(viewModel)
                is AppScreen.Main -> MainScreen(viewModel)
                is AppScreen.Detail -> SessionDetailScreen(
                    sessionId = screen.sessionId,
                    viewModel = viewModel
                )
            }
        }
    }
}

// ----------------------------------------------------
// ONBOARDING SCREEN
// ----------------------------------------------------

@Composable
fun OnboardingScreen(viewModel: JaleesViewModel) {
    val context = LocalContext.current
    val progress by viewModel.modelProgress.collectAsState()
    val isModelReady by viewModel.isModelReady.collectAsState()

    var micGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    
    var notifGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    var batteryIgnored by remember {
        mutableStateOf(viewModel.checkBatteryIgnored(context))
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        micGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: micGranted
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifGranted = permissions[Manifest.permission.POST_NOTIFICATIONS] ?: notifGranted
        }
    }

    // Refresh states on launch
    LaunchedEffect(Unit) {
        batteryIgnored = viewModel.checkBatteryIgnored(context)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(OnyxSlate, DeepCharcoal)
                    )
                )
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Elegant Header
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 28.dp)
            ) {
                // Large styled brand icon
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(EmeraldActive)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Jalees Mic",
                        tint = OnyxSlate,
                        modifier = Modifier.size(48.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.welcome_title),
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = EmeraldActive,
                        letterSpacing = 0.5.sp
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.welcome_desc),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = TextLight,
                        lineHeight = 24.sp
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            // Permissions Check & Progress panel
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.permissions_required),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = GoldAccent
                    ),
                    modifier = Modifier.align(Alignment.End)
                )

                // 1. Microphone Permission
                PermissionRow(
                    title = stringResource(R.string.mic_perm_title),
                    desc = stringResource(R.string.mic_perm_desc),
                    isGranted = micGranted,
                    icon = Icons.Default.Mic,
                    onClick = {
                        launcher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                    }
                )

                // 2. Notifications Permission (Oreo+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    PermissionRow(
                        title = stringResource(R.string.notif_perm_title),
                        desc = stringResource(R.string.notif_perm_desc),
                        isGranted = notifGranted,
                        icon = Icons.Default.Notifications,
                        onClick = {
                            launcher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                        }
                    )
                }

                // 3. Battery exemption
                PermissionRow(
                    title = stringResource(R.string.battery_perm_title),
                    desc = stringResource(R.string.battery_perm_desc),
                    isGranted = batteryIgnored,
                    icon = Icons.Default.BatteryChargingFull,
                    onClick = {
                        viewModel.openBatterySettings(context)
                        // Trigger simulation refresh state
                        batteryIgnored = true
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Model downloading visual block
                if (micGranted) {
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = DeepCharcoal.copy(alpha = 0.8f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.model_setup_title),
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldActive
                                ),
                                modifier = Modifier.align(Alignment.End)
                            )
                            Text(
                                text = stringResource(R.string.model_setup_desc),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = TextMuted,
                                    lineHeight = 18.sp
                                ),
                                textAlign = TextAlign.End,
                                modifier = Modifier.fillMaxWidth()
                            )

                            if (progress == 0 && !isModelReady) {
                                Button(
                                    onClick = { viewModel.startModelPreparation() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = EmeraldActive,
                                        contentColor = OnyxSlate
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("init_model_button")
                                ) {
                                    Text(text = "تهيئة حزمة الذكاء الاصطناعي أوفلاين", fontWeight = FontWeight.Bold)
                                }
                            } else if (progress in 1..99) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    LinearProgressIndicator(
                                        progress = { progress / 100f },
                                        color = EmeraldActive,
                                        trackColor = BorderColor,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Text(
                                        text = stringResource(R.string.model_download_progress, progress),
                                        style = MaterialTheme.typography.bodySmall.copy(color = GoldAccent),
                                        modifier = Modifier.align(Alignment.Start)
                                    )
                                }
                            } else if (isModelReady) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Success",
                                        tint = EmeraldActive,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.model_ready),
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = EmeraldLight,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Footer Button
            Button(
                onClick = {
                    if (micGranted && isModelReady) {
                        viewModel.completeOnboarding()
                    } else {
                        launcher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                        Toast.makeText(context, "الرجاء منح الميكروفون وتهيئة الحزمة للمتابعة", Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = micGranted && isModelReady,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("continue_onboarding_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldActive,
                    disabledContainerColor = BorderColor,
                    contentColor = OnyxSlate,
                    disabledContentColor = TextMuted
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = "دخول التطبيق ومتابعة الجلسات الآمنة",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}

@Composable
fun PermissionRow(
    title: String,
    desc: String,
    isGranted: Boolean,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isGranted) EmeraldDark.copy(alpha = 0.2f) else DeepCharcoal)
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End
    ) {
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = if (isGranted) EmeraldLight else TextLight
                ),
                textAlign = TextAlign.End
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = TextMuted
                ),
                textAlign = TextAlign.End
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (isGranted) EmeraldActive else BorderColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isGranted) Icons.Default.Check else icon,
                contentDescription = null,
                tint = if (isGranted) OnyxSlate else TextLight,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ----------------------------------------------------
// MAIN SCREEN
// ----------------------------------------------------

@Composable
fun MainScreen(viewModel: JaleesViewModel) {
    val context = LocalContext.current
    val sessions by viewModel.sessions.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isListening by viewModel.isListening.collectAsState()
    val dbLevel by viewModel.dbLevel.collectAsState()
    val liveDraftText by viewModel.liveDraftText.collectAsState()

    var sessionToDelete by remember { mutableStateOf<Session?>(null) }
    var sessionToRename by remember { mutableStateOf<Session?>(null) }
    var renameInput by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .background(OnyxSlate)
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                // Styled top brand bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Security Privacy badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(EmeraldDark.copy(alpha = 0.3f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Secure",
                                tint = EmeraldActive,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "محلي 100% أوفلاين",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = EmeraldActive,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }

                    // App Title
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = EmeraldActive,
                                letterSpacing = 0.5.sp
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.KeyboardVoice,
                            contentDescription = "Jalees Wave",
                            tint = EmeraldActive,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Search field
                TextField(
                    value = searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    placeholder = {
                        Text(
                            text = stringResource(R.string.search_hint),
                            color = TextMuted,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.End
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .testTag("session_search_field"),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = DeepCharcoal,
                        unfocusedContainerColor = DeepCharcoal,
                        focusedTextColor = TextLight,
                        unfocusedTextColor = TextLight,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = TextMuted
                        )
                    },
                    leadingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear",
                                    tint = TextMuted
                                )
                            }
                        }
                    },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        textAlign = TextAlign.End
                    )
                )
            }
        },
        floatingActionButton = {
            // High fidelity Floating Action button to Record
            ExtendedFloatingActionButton(
                onClick = {
                    if (isListening) {
                        viewModel.stopCurrentListeningSession(context)
                    } else {
                        viewModel.startNewListeningSession(context)
                    }
                },
                modifier = Modifier
                    .padding(bottom = 16.dp)
                    .testTag("record_fab_button"),
                containerColor = if (isListening) Color.Red else EmeraldActive,
                contentColor = if (isListening) Color.White else OnyxSlate,
                shape = FloatingActionButtonDefaults.extendedFabShape
            ) {
                Icon(
                    imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isListening) {
                        stringResource(R.string.stop_recording)
                    } else {
                        stringResource(R.string.start_recording)
                    },
                    fontWeight = FontWeight.Bold
                )
            }
        },
        floatingActionButtonPosition = FabPosition.Center
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(OnyxSlate, DeepCharcoal)
                    )
                )
        ) {
            if (sessions.isEmpty() && !isListening) {
                // Clean illustrated Empty State
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .drawBehind {
                                drawCircle(
                                    color = EmeraldActive.copy(alpha = 0.1f),
                                    radius = size.width / 2f
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SpeakerNotesOff,
                            contentDescription = "No sessions",
                            tint = EmeraldActive.copy(alpha = 0.5f),
                            modifier = Modifier.size(48.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = stringResource(R.string.no_sessions),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextLight
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "اضغط على الزر أدناه لبدء التسجيل وتفريغ النصوص في الحال وبخصوصية تامة.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = TextMuted,
                            lineHeight = 18.sp
                        ),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 100.dp),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(sessions, key = { it.id }) { session ->
                        SessionItemRow(
                            session = session,
                            onClick = { viewModel.navigateTo(AppScreen.Detail(session.id)) },
                            onRename = { 
                                sessionToRename = session
                                renameInput = session.title
                            },
                            onDelete = { sessionToDelete = session }
                        )
                    }
                }
            }

            // LIVE LISTENING OVERLAY
            AnimatedVisibility(
                visible = isListening,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                LiveListeningPanel(
                    dbLevel = dbLevel,
                    draftText = liveDraftText,
                    onStop = { viewModel.stopCurrentListeningSession(context) }
                )
            }
        }
    }

    // Delete confirmation dialog
    if (sessionToDelete != null) {
        AlertDialog(
            onDismissRequest = { sessionToDelete = null },
            title = {
                Text(
                    text = stringResource(R.string.delete_confirm_title),
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.delete_confirm_desc),
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        sessionToDelete?.let { viewModel.deleteSession(it.id) }
                        sessionToDelete = null
                        Toast.makeText(context, context.getString(R.string.session_deleted), Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                ) {
                    Text(text = stringResource(R.string.confirm), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionToDelete = null }) {
                    Text(text = stringResource(R.string.cancel))
                }
            },
            containerColor = DeepCharcoal,
            titleContentColor = TextLight,
            textContentColor = TextMuted
        )
    }

    // Rename Dialog
    if (sessionToRename != null) {
        Dialog(onDismissRequest = { sessionToRename = null }) {
            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(containerColor = DeepCharcoal),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "تعديل عنوان الجلسة",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = EmeraldActive
                        )
                    )

                    TextField(
                        value = renameInput,
                        onValueChange = { renameInput = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .testTag("rename_input_field"),
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = TextLight,
                            unfocusedTextColor = TextLight,
                            focusedContainerColor = OnyxSlate,
                            unfocusedContainerColor = OnyxSlate
                        ),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            textAlign = TextAlign.End
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                sessionToRename?.let { viewModel.renameSession(it.id, renameInput) }
                                sessionToRename = null
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = EmeraldActive,
                                contentColor = OnyxSlate
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = stringResource(R.string.save), fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { sessionToRename = null },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextLight),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = stringResource(R.string.cancel))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SessionItemRow(
    session: Session,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val durationText = remember(session.startTimestamp, session.endTimestamp) {
        val seconds = (session.endTimestamp - session.startTimestamp) / 1000L
        if (seconds < 60) {
            "أقل من دقيقة"
        } else {
            val minutes = seconds / 60
            "$minutes دقيقة"
        }
    }

    val dateFormatted = remember(session.startTimestamp) {
        SimpleDateFormat("yyyy-MM-dd hh:mm a", Locale("ar")).format(Date(session.startTimestamp))
    }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("session_card_${session.id}")
            .clickable { onClick() },
        colors = CardDefaults.elevatedCardColors(
            containerColor = DeepCharcoal
        ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Options buttons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Color(0xFFEF4444)
                    )
                }
                IconButton(onClick = onRename) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Rename",
                        tint = GoldAccent
                    )
                }
            }

            // Session Info
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = session.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextLight
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End
                )
                
                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = durationText,
                        style = MaterialTheme.typography.bodySmall.copy(color = GoldAccent)
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
                    )
                    Text(
                        text = dateFormatted,
                        style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
                    )
                }
            }
        }
    }
}

// Visual sound fluctuation panel
@Composable
fun LiveListeningPanel(
    dbLevel: Float,
    draftText: String,
    onStop: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(OnyxSlate),
        colors = CardDefaults.elevatedCardColors(
            containerColor = OnyxSlate
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header status
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                PulsingRecordDot()
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.recording_active),
                    style = MaterialTheme.typography.titleSmall.copy(
                        color = EmeraldActive,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            // REAL-TIME AUDIO WAVE REPRESENTATION (Waveform Canvas!)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                contentAlignment = Alignment.Center
            ) {
                AudioWaveformCanvas(dbLevel = dbLevel)
            }

            // RUNNING REAL-TIME DRAFT TEXT
            if (draftText.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DeepCharcoal)
                        .padding(14.dp)
                ) {
                    Text(
                        text = draftText,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = TextLight,
                            lineHeight = 22.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                Text(
                    text = "بانتظار كلام المتحدث...",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextMuted),
                    textAlign = TextAlign.Center
                )
            }
            
            Spacer(modifier = Modifier.height(40.dp)) // padding for the main Recording FAB overlaid
        }
    }
}

@Composable
fun PulsingRecordDot() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(Color.Red.copy(alpha = alpha))
    )
}

@Composable
fun AudioWaveformCanvas(dbLevel: Float) {
    val amplitudeScale = remember(dbLevel) {
        // map decibels (15-95) to suitable waveform height ratio (0.1 - 1.5)
        val ratio = (dbLevel - 15f) / 80f
        maxOf(0.1f, minOf(2.0f, ratio))
    }

    // Phase shift animation for ambient flow
    val phaseShift = rememberInfiniteTransition(label = "waveFlow")
    val phase by phaseShift.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "waveShift"
    )

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
    ) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f
        val path = Path()

        path.moveTo(0f, centerY)
        
        // Draw elegant mathematical sine sine waves layered
        val points = 100
        for (i in 0..points) {
            val x = (i.toFloat() / points) * width
            // Gaussian bell envelope to damp edges nicely
            val envelope = sin((i.toFloat() / points) * Math.PI)
            val y = centerY + (centerY * 0.8f * envelope * amplitudeScale * sin(2 * Math.PI * x / width * 2.5 + phase)).toFloat()
            path.lineTo(x, y)
        }

        drawPath(
            path = path,
            color = EmeraldActive,
            style = Stroke(width = 3.dp.toPx())
        )

        // Draw background shadow layer
        val path2 = Path()
        path2.moveTo(0f, centerY)
        for (i in 0..points) {
            val x = (i.toFloat() / points) * width
            val envelope = sin((i.toFloat() / points) * Math.PI)
            val y = centerY + (centerY * 0.5f * envelope * amplitudeScale * sin(2 * Math.PI * x / width * 4.0 - phase)).toFloat()
            path2.lineTo(x, y)
        }
        drawPath(
            path = path2,
            color = GoldAccent.copy(alpha = 0.5f),
            style = Stroke(width = 1.5.dp.toPx())
        )
    }
}

// ----------------------------------------------------
// SESSION DETAIL SCREEN
// ----------------------------------------------------

@Composable
fun SessionDetailScreen(
    sessionId: Int,
    viewModel: JaleesViewModel
) {
    val context = LocalContext.current
    val session by viewModel.selectedSession.collectAsState()
    val transcripts by viewModel.currentTranscripts.collectAsState()
    val detailSearchQuery by viewModel.detailSearchQuery.collectAsState()
    
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameInput by remember { mutableStateOf("") }

    DisposableEffect(sessionId) {
        viewModel.navigateTo(AppScreen.Detail(sessionId))
        onDispose {}
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .background(OnyxSlate)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // Bar Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Actions
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = {
                                if (transcripts.isNotEmpty()) {
                                    val fullText = transcripts.joinToString("\n") {
                                        val m = (it.relativeTime / 1000L) / 60
                                        val s = (it.relativeTime / 1000L) % 60
                                        val timeStr = String.format("[%02d:%02d]", m, s)
                                        "$timeStr ${it.textContent}"
                                    }
                                    
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Jalees Transcript", fullText)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, context.getString(R.string.export_success), Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy text",
                                tint = EmeraldActive
                            )
                        }

                        IconButton(
                            onClick = {
                                if (transcripts.isNotEmpty()) {
                                    val fullText = transcripts.joinToString("\n") {
                                        val m = (it.relativeTime / 1000L) / 60
                                        val s = (it.relativeTime / 1000L) % 60
                                        val timeStr = String.format("[%02d:%02d]", m, s)
                                        "$timeStr ${it.textContent}"
                                    }
                                    
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TITLE, session?.title)
                                        putExtra(Intent.EXTRA_TEXT, fullText)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "مشاركة نصوص جليس"))
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share",
                                tint = GoldAccent
                            )
                        }
                    }

                    // Title with Rename Clickable
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                session?.let {
                                    renameInput = it.title
                                    showRenameDialog = true
                                }
                            }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit title",
                            tint = TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = session?.title ?: "",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextLight
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.End
                        )
                    }

                    // Back
                    IconButton(onClick = { viewModel.goBack() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowForward, // RTL back arrow direction
                            contentDescription = "Back",
                            tint = TextLight
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Inline Search inside Transcript
                TextField(
                    value = detailSearchQuery,
                    onValueChange = { viewModel.updateDetailSearchQuery(it) },
                    placeholder = {
                        Text(
                            text = stringResource(R.string.search_in_transcript),
                            color = TextMuted,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.End
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .testTag("transcript_search_field"),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = DeepCharcoal,
                        unfocusedContainerColor = DeepCharcoal,
                        focusedTextColor = TextLight,
                        unfocusedTextColor = TextLight,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = "Filter",
                            tint = TextMuted
                        )
                    },
                    leadingIcon = {
                        if (detailSearchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateDetailSearchQuery("") }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear",
                                    tint = TextMuted
                                )
                            }
                        }
                    },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        textAlign = TextAlign.End
                    )
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(OnyxSlate, DeepCharcoal)
                    )
                )
        ) {
            if (transcripts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (detailSearchQuery.isNotEmpty()) {
                            "لا توجد نتائج مطابقة لبحثك في تفريغ المحادثة."
                        } else {
                            "لم يتم تفريغ أي نصوص لهذه الجلسة."
                        },
                        style = MaterialTheme.typography.bodyMedium.copy(color = TextMuted),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(transcripts, key = { it.id }) { transcript ->
                        TranscriptRowItem(
                            transcript = transcript,
                            highlightKeyword = detailSearchQuery
                        )
                    }
                }
            }
        }
    }

    // Rename Inline Dialog
    if (showRenameDialog) {
        Dialog(onDismissRequest = { showRenameDialog = false }) {
            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(containerColor = DeepCharcoal),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "تعديل عنوان الجلسة",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = EmeraldActive
                        )
                    )

                    TextField(
                        value = renameInput,
                        onValueChange = { renameInput = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp)),
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = TextLight,
                            unfocusedTextColor = TextLight,
                            focusedContainerColor = OnyxSlate,
                            unfocusedContainerColor = OnyxSlate
                        ),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            textAlign = TextAlign.End
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.renameSession(sessionId, renameInput)
                                showRenameDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = EmeraldActive,
                                contentColor = OnyxSlate
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = stringResource(R.string.save), fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { showRenameDialog = false },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextLight),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = stringResource(R.string.cancel))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TranscriptRowItem(
    transcript: Transcript,
    highlightKeyword: String
) {
    val durationFormatted = remember(transcript.relativeTime) {
        val totalSec = transcript.relativeTime / 1000L
        val min = totalSec / 60
        val sec = totalSec % 60
        String.format("[%02d:%02d]", min, sec)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DeepCharcoal.copy(alpha = 0.5f))
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.End
    ) {
        // Text Bubble content
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.End
        ) {
            // Speaker tag & Time
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = durationFormatted,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = GoldAccent,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = transcript.speakerTag,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = EmeraldActive,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Text text with Highlighted search matches
            if (highlightKeyword.isNotEmpty() && transcript.textContent.contains(highlightKeyword, ignoreCase = true)) {
                val annotatedString = buildAnnotatedString {
                    val fullText = transcript.textContent
                    var startIdx = 0
                    while (true) {
                        val matchIdx = fullText.indexOf(highlightKeyword, startIdx, ignoreCase = true)
                        if (matchIdx == -1) {
                            append(fullText.substring(startIdx))
                            break
                        }
                        append(fullText.substring(startIdx, matchIdx))
                        withStyle(style = SpanStyle(background = GoldAccent.copy(alpha = 0.4f), color = GoldAccent, fontWeight = FontWeight.Bold)) {
                            append(fullText.substring(matchIdx, matchIdx + highlightKeyword.length))
                        }
                        startIdx = matchIdx + highlightKeyword.length
                    }
                }
                Text(
                    text = annotatedString,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        lineHeight = 22.sp,
                        color = TextLight
                    ),
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text(
                    text = transcript.textContent,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        lineHeight = 22.sp,
                        color = TextLight
                    ),
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
