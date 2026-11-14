package com.example

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.outlined.AltRoute
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.core.i18n.AppLanguage
import com.example.core.i18n.BengaliStrings
import com.example.core.i18n.EnglishStrings
import com.example.core.i18n.LocalStrings
import com.example.ui.AppLockUiState
import com.example.ui.GatewayViewModel
import com.example.ui.components.StatusPill
import com.example.ui.components.gatewayStatusLabelAndTone
import com.example.ui.navigation.Routes
import com.example.ui.screens.*
import com.example.ui.theme.JhGetWayTheme
import com.example.updater.AppUpdateManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val vm: GatewayViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by vm.settings.collectAsState()
            val isInitialLoaded by vm.isInitialSettingsLoaded.collectAsState()
            val isUnlocked by vm.isAppUnlocked.collectAsState()
            val appLockUi by vm.appLockUiState.collectAsState()

            LaunchedEffect(settings.appLockEnabled, settings.appLockAutoLockOnBackground) {
                if (settings.appLockEnabled && settings.appLockAutoLockOnBackground) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            val activeStrings = remember(settings.language) {
                when (settings.language) {
                    AppLanguage.ENGLISH -> EnglishStrings
                    AppLanguage.BENGALI -> BengaliStrings
                    AppLanguage.SYSTEM -> if (Locale.getDefault().language.equals("bn", true)) BengaliStrings else EnglishStrings
                }
            }

            CompositionLocalProvider(LocalStrings provides activeStrings) {
                JhGetWayTheme(themeMode = settings.themeMode) {
                    when {
                        !isInitialLoaded -> Surface(modifier = Modifier.fillMaxSize()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                        !isUnlocked -> AppLockGateScreen(
                            appLockUiState = appLockUi,
                            biometricEnabled = settings.appLockBiometricEnabled,
                            biometricAvailable = remember { isDeviceBiometricAvailable(this@MainActivity) },
                            onSubmitPin = { vm.verifyUnlockPin(it) },
                            onClearError = { vm.clearPinInputError() },
                            onRequestBiometric = {
                                launchPlatformBiometricPrompt(
                                    title = activeStrings.appName,
                                    subtitle = activeStrings.appLockUseBiometric,
                                    cancelLabel = activeStrings.cancel,
                                    onAuthenticated = { vm.unlockWithBiometric() }
                                )
                            }
                        )
                        !settings.onboardingCompleted -> OnboardingScreen(
                            viewModel = vm,
                            onCompleted = { vm.completeOnboarding() }
                        )
                        else -> JhGetWayMainShell(viewModel = vm)
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            vm.onAppBackgrounded()
        }
    }

    private fun isDeviceBiometricAvailable(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val bm = context.getSystemService(BiometricManager::class.java) ?: return false
            return bm.canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS
        }
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)
    }

    private fun launchPlatformBiometricPrompt(
        title: String,
        subtitle: String,
        cancelLabel: String,
        onAuthenticated: () -> Unit
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        val prompt = BiometricPrompt.Builder(this)
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButton(cancelLabel, ContextCompat.getMainExecutor(this)) { _, _ -> }
            .build()
        prompt.authenticate(
            CancellationSignal(),
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                    super.onAuthenticationSucceeded(result)
                    onAuthenticated()
                }
            }
        )
    }
}

private data class NavDestinationItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JhGetWayMainShell(viewModel: GatewayViewModel) {
    val s = LocalStrings.current
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route ?: Routes.DASHBOARD
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val banner by viewModel.uiBannerMessage.collectAsState()
    val runtime by viewModel.gatewayRuntime.collectAsState()
    val pendingApproval by viewModel.pendingMcpApproval.collectAsState()
    val updateState by viewModel.appUpdateUiState.collectAsState()
    val context = LocalContext.current

    val unknownSourcesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (AppUpdateManager.canRequestPackageInstalls(context)) {
            viewModel.launchDownloadedApkInstall()
        }
    }

    LaunchedEffect(banner) {
        banner?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearBanner()
        }
    }

    val bottomNavItems = listOf(
        NavDestinationItem(Routes.DASHBOARD, s.navHome, Icons.Filled.Home, Icons.Outlined.Home),
        NavDestinationItem(Routes.PROVIDERS, s.navProviders, Icons.Filled.CloudQueue, Icons.Outlined.CloudQueue),
        NavDestinationItem(Routes.PLAYGROUND, s.navPlayground, Icons.AutoMirrored.Filled.Chat, Icons.AutoMirrored.Outlined.Chat),
        NavDestinationItem(Routes.ANALYTICS, s.navAnalytics, Icons.Filled.Analytics, Icons.Outlined.Analytics),
        NavDestinationItem(Routes.SETTINGS, s.navSettings, Icons.Filled.Settings, Icons.Outlined.Settings)
    )

    val allDrawerItems = listOf(
        NavDestinationItem(Routes.DASHBOARD, s.navHome, Icons.Filled.Home, Icons.Outlined.Home),
        NavDestinationItem(Routes.PROVIDERS, s.navProviders, Icons.Filled.CloudQueue, Icons.Outlined.CloudQueue),
        NavDestinationItem(Routes.MODELS, s.navModels, Icons.Filled.Memory, Icons.Outlined.Memory),
        NavDestinationItem(Routes.GATEWAY, s.navGateway, Icons.Filled.Hub, Icons.Outlined.Hub),
        NavDestinationItem(Routes.SMART_ROUTER, s.navSmartRouter, Icons.AutoMirrored.Filled.AltRoute, Icons.AutoMirrored.Outlined.AltRoute),
        NavDestinationItem(Routes.CODING_AGENTS, s.navCodingAgents, Icons.Filled.Code, Icons.Outlined.Code),
        NavDestinationItem(Routes.LOCAL_AI, s.navLocalAi, Icons.Filled.Computer, Icons.Outlined.Computer),
        NavDestinationItem(Routes.MCP_SERVERS, s.navMcpServers, Icons.Filled.Extension, Icons.Outlined.Extension),
        NavDestinationItem(Routes.WORKFLOWS, s.navWorkflows, Icons.Filled.AccountTree, Icons.Outlined.AccountTree),
        NavDestinationItem(Routes.PLAYGROUND, s.navPlayground, Icons.AutoMirrored.Filled.Chat, Icons.AutoMirrored.Outlined.Chat),
        NavDestinationItem(Routes.ANALYTICS, s.navAnalytics, Icons.Filled.Analytics, Icons.Outlined.Analytics),
        NavDestinationItem(Routes.DEVICES, s.navDevices, Icons.Filled.Devices, Icons.Outlined.Devices),
        NavDestinationItem(Routes.VAULT, s.navVault, Icons.Filled.VpnKey, Icons.Outlined.VpnKey),
        NavDestinationItem(Routes.ACTIVITY_LOGS, s.navActivityLogs, Icons.Filled.History, Icons.Outlined.History),
        NavDestinationItem(Routes.SETTINGS, s.navSettings, Icons.Filled.Settings, Icons.Outlined.Settings)
    )

    fun navigateTo(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.widthIn(max = 300.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp)
                ) {
                    Text(s.appName, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                    Text(s.orgName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp))
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    allDrawerItems.forEach { item ->
                        NavigationDrawerItem(
                            selected = currentRoute == item.route,
                            onClick = {
                                scope.launch { drawerState.close() }
                                navigateTo(item.route)
                            },
                            icon = { Icon(if (currentRoute == item.route) item.selectedIcon else item.unselectedIcon, contentDescription = null) },
                            label = { Text(item.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            modifier = Modifier.testTag("drawer_${item.route}")
                        )
                    }
                }
            }
        }
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isExpanded = maxWidth >= 840.dp

            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = allDrawerItems.firstOrNull { it.route == currentRoute }?.label ?: s.appName,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = { scope.launch { drawerState.open() } },
                                modifier = Modifier.testTag("open_drawer_btn")
                            ) {
                                Icon(Icons.Default.Menu, contentDescription = s.navMore)
                            }
                        },
                        actions = {
                            val (gwLabel, gwTone) = gatewayStatusLabelAndTone(runtime.status)
                            StatusPill(
                                text = gwLabel,
                                tone = gwTone,
                                modifier = Modifier.padding(end = 12.dp)
                            )
                        }
                    )
                },
                bottomBar = {
                    if (!isExpanded) {
                        NavigationBar {
                            bottomNavItems.forEach { item ->
                                val selected = currentRoute == item.route
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateTo(item.route) },
                                    icon = { Icon(if (selected) item.selectedIcon else item.unselectedIcon, contentDescription = item.label) },
                                    label = { Text(item.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    modifier = Modifier.testTag("bottom_nav_${item.route}")
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (isExpanded) {
                        NavigationRail {
                            bottomNavItems.forEach { item ->
                                val selected = currentRoute == item.route
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { navigateTo(item.route) },
                                    icon = { Icon(if (selected) item.selectedIcon else item.unselectedIcon, contentDescription = item.label) },
                                    label = { Text(item.label, maxLines = 1) }
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        NavHost(
                            navController = navController,
                            startDestination = Routes.DASHBOARD
                        ) {
                            composable(Routes.DASHBOARD) { DashboardScreen(viewModel, ::navigateTo) }
                            composable(Routes.PROVIDERS) { ProvidersScreen(viewModel, ::navigateTo) }
                            composable(Routes.MODELS) { ModelsScreen(viewModel, ::navigateTo) }
                            composable(Routes.GATEWAY) { GatewayScreen(viewModel) }
                            composable(Routes.SMART_ROUTER) { SmartRouterScreen(viewModel) }
                            composable(Routes.CODING_AGENTS) { CodingAgentsScreen(viewModel) }
                            composable(Routes.LOCAL_AI) { LocalAiScreen(viewModel) }
                            composable(Routes.MCP_SERVERS) { McpServersScreen(viewModel) }
                            composable(Routes.WORKFLOWS) { WorkflowsScreen(viewModel) }
                            composable(Routes.PLAYGROUND) { PlaygroundScreen(viewModel, ::navigateTo) }
                            composable(Routes.ANALYTICS) { AnalyticsScreen(viewModel) }
                            composable(Routes.DEVICES) { DevicesScreen(viewModel) }
                            composable(Routes.VAULT) { VaultScreen(viewModel) }
                            composable(Routes.ACTIVITY_LOGS) { ActivityLogsScreen(viewModel) }
                            composable(Routes.SETTINGS) { SettingsScreen(viewModel, ::navigateTo) }
                        }
                    }
                }
            }
        }
    }

    pendingApproval?.let { req ->
        AlertDialog(
            onDismissRequest = { viewModel.resolveMcpApproval(false) },
            title = { Text(s.approvalRequiredTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(s.approvalRequiredMessage)
                    Text("${req.server.name} → ${req.tool.toolName}", style = MaterialTheme.typography.titleSmall)
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.resolveMcpApproval(true) }) { Text(s.approveAndRun) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.resolveMcpApproval(false) }) { Text(s.cancel) }
            }
        )
    }

    if (updateState.showPromptDialog) {
        updateState.availableRelease?.let { release ->
            AlertDialog(
                onDismissRequest = {
                    if (!updateState.isDownloading) viewModel.dismissUpdateDialog()
                },
                title = { Text("${s.updateAvailableTitle} (${release.tagName})") },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    ) {
                        Text(release.releaseTitle, style = MaterialTheme.typography.titleSmall)
                        if (release.releaseNotes.isNotBlank()) {
                            Text(
                                text = "${s.releaseNotesLabel}:\n${release.releaseNotes.take(600)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (updateState.isDownloading) {
                            LinearProgressIndicator(
                                progress = { updateState.downloadProgressPct / 100f },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                "${s.downloadingUpdateLabel} ${updateState.downloadProgressPct}%",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        updateState.errorMessage?.let { err ->
                            Text(err, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                confirmButton = {
                    if (updateState.downloadedApkFile != null) {
                        Button(
                            onClick = { viewModel.launchDownloadedApkInstall() },
                            modifier = Modifier.testTag("update_dialog_install_btn")
                        ) {
                            Text(s.installDownloadedAction)
                        }
                    } else {
                        Button(
                            onClick = { viewModel.downloadAndInstallUpdate() },
                            enabled = !updateState.isDownloading,
                            modifier = Modifier.testTag("update_dialog_download_btn")
                        ) {
                            Text(s.downloadAndInstallAction)
                        }
                    }
                },
                dismissButton = {
                    if (!updateState.isDownloading) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { viewModel.skipCurrentUpdate() }) {
                                Text(s.skipThisVersionLabel)
                            }
                            TextButton(onClick = { viewModel.dismissUpdateDialog() }) {
                                Text(s.laterLabel)
                            }
                        }
                    }
                }
            )
        }
    }

    if (updateState.needsInstallPermission) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissInstallPermissionPrompt() },
            title = { Text(s.installPermissionTitle) },
            text = { Text(s.installPermissionMessage) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.dismissInstallPermissionPrompt()
                        unknownSourcesLauncher.launch(AppUpdateManager.createUnknownSourcesSettingsIntent(context))
                    },
                    modifier = Modifier.testTag("update_open_install_settings_btn")
                ) {
                    Text(s.openSettingsLabel)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissInstallPermissionPrompt() }) {
                    Text(s.cancel)
                }
            }
        )
    }
}

@Composable
private fun AppLockGateScreen(
    appLockUiState: AppLockUiState,
    biometricEnabled: Boolean,
    biometricAvailable: Boolean,
    onSubmitPin: (String) -> Unit,
    onClearError: () -> Unit,
    onRequestBiometric: () -> Unit
) {
    val s = LocalStrings.current
    var pin by remember { mutableStateOf("") }
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(appLockUiState.lockedUntilEpochMs) {
        while (appLockUiState.lockedUntilEpochMs > System.currentTimeMillis()) {
            nowMs = System.currentTimeMillis()
            delay(500L)
        }
        nowMs = System.currentTimeMillis()
    }

    val remainingSec = ((appLockUiState.lockedUntilEpochMs - nowMs).coerceAtLeast(0L) + 999L) / 1000L
    val isLockedOut = remainingSec > 0L

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            Text(s.appName, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = pin,
                onValueChange = {
                    pin = it.filter { c -> c.isDigit() }.take(8)
                    onClearError()
                },
                label = { Text(s.appLockEnterPin) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (!isLockedOut && !appLockUiState.isVerifying && pin.isNotEmpty()) {
                            val submitted = pin
                            pin = ""
                            onSubmitPin(submitted)
                        }
                    }
                ),
                isError = appLockUiState.isInvalid || isLockedOut,
                enabled = !isLockedOut && !appLockUiState.isVerifying,
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 360.dp)
                    .testTag("app_lock_pin_input")
            )
            if (isLockedOut) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "${s.appLockLockedOut} (${remainingSec}s)",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("app_lock_lockout_text")
                )
            } else if (appLockUiState.isInvalid) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = s.appLockIncorrect,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("app_lock_error_text")
                )
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    val submitted = pin
                    pin = ""
                    onSubmitPin(submitted)
                },
                enabled = !isLockedOut && !appLockUiState.isVerifying && pin.isNotEmpty(),
                modifier = Modifier.testTag("app_lock_unlock_btn")
            ) {
                Text(s.appLockUnlock)
            }
            if (biometricEnabled && biometricAvailable && !isLockedOut) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onRequestBiometric,
                    modifier = Modifier.testTag("app_lock_biometric_btn")
                ) {
                    Icon(Icons.Default.Fingerprint, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(s.appLockUseBiometric)
                }
            }
        }
    }
}
