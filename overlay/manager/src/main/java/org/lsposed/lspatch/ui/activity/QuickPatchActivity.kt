package org.lsposed.lspatch.ui.activity

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.lsposed.lspatch.data.model.ModuleOrigin
import org.lsposed.lspatch.data.model.ModuleRef
import org.lsposed.lspatch.data.model.PatchMode
import org.lsposed.lspatch.data.model.PatchRequest
import org.lsposed.lspatch.data.model.PatchStage
import org.lsposed.lspatch.data.model.PatchStep
import org.lsposed.lspatch.data.model.PatchTarget
import org.lsposed.lspatch.data.repository.PatchJobHost
import org.lsposed.lspatch.data.repository.PatchOutputStore
import org.lsposed.lspatch.data.repository.PatchRequestStore
import org.lsposed.lspatch.util.LSPPackageManager

private const val TARGET_PACKAGE = "jp.co.eisys.dlsitesound"
private const val MODULE_PACKAGE = "io.github.ariinyume.dlsitesoundfloat"
private const val MODULE_ASSET = "quickpatch/DLsiteFloat-2.1.0-rootless-v1.apk"
private const val MODULE_FILE = "DLsiteFloat-2.1.0-rootless-v1.apk"
private const val VERIFIED_VERSION_NAME = "2.19.0"
private const val VERIFIED_VERSION_CODE = 573L
private const val QUICK_PREFS = "quickpatch"
private const val PREF_PENDING_EXTERNAL_UNINSTALL = "pending_external_uninstall"

private data class TargetSnapshot(
    val label: String,
    val versionName: String,
    val versionCode: Long,
    val apkPaths: List<String>,
    val alreadyPatched: Boolean,
) {
    val verified: Boolean
        get() = versionName == VERIFIED_VERSION_NAME && versionCode == VERIFIED_VERSION_CODE
}

class QuickPatchActivity : ComponentActivity() {
    private val resumeTick = mutableIntStateOf(0)

    override fun onResume() {
        super.onResume()
        resumeTick.intValue++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    QuickPatchScreen(
                        resumeTick = resumeTick.intValue,
                        openAdvanced = { startActivity(Intent(this, MainActivity::class.java)) },
                        openOverlaySettings = {
                            runCatching {
                                startActivity(
                                    Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:$TARGET_PACKAGE"),
                                    )
                                )
                            }
                        },
                        launchTarget = {
                            packageManager.getLaunchIntentForPackage(TARGET_PACKAGE)?.let(::startActivity)
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickPatchScreen(
    resumeTick: Int,
    openAdvanced: () -> Unit,
    openOverlaySettings: () -> Unit,
    launchTarget: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val patchStep by PatchJobHost.step.collectAsState()
    val prefs = remember { context.getSharedPreferences(QUICK_PREFS, Context.MODE_PRIVATE) }

    var target by remember { mutableStateOf<TargetSnapshot?>(null) }
    var moduleFile by remember { mutableStateOf<File?>(null) }
    var recoveredOutputs by remember { mutableStateOf<List<File>>(emptyList()) }
    var recoveryInstallStatus by remember { mutableStateOf<String?>(null) }
    var recoveryBusy by remember { mutableStateOf(false) }
    var externalUninstallPending by remember {
        mutableStateOf(prefs.getBoolean(PREF_PENDING_EXTERNAL_UNINSTALL, false))
    }
    var startupError by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }

    suspend fun refresh() {
        loading = true
        startupError = null
        runCatching {
            val prepared = withContext(Dispatchers.IO) { prepareBundledModule(context) }
            val detected = withContext(Dispatchers.IO) { detectTarget(context) }
            val outputs = PatchOutputStore.outputs(TARGET_PACKAGE)
            Triple(prepared, detected, outputs)
        }.onSuccess { (module, detected, outputs) ->
            moduleFile = module
            target = detected
            recoveredOutputs = outputs
        }.onFailure {
            startupError = it.message ?: it.javaClass.simpleName
        }
        loading = false
    }

    suspend fun installRecoveredOutputs(files: List<File>) {
        if (files.isEmpty() || recoveryBusy) return
        recoveryBusy = true
        recoveryInstallStatus = "正在打开系统安装器……"
        val (status, message) = LSPPackageManager.installFiles(files, useShizuku = false)
        if (status == PackageInstaller.STATUS_SUCCESS) {
            recoveryInstallStatus = "安装成功 ✓"
            PatchOutputStore.discard(TARGET_PACKAGE)
            recoveredOutputs = emptyList()
            refresh()
        } else {
            recoveryInstallStatus =
                "安装失败：${message ?: "PackageInstaller status $status"}"
        }
        recoveryBusy = false
    }

    suspend fun continueAfterExternalUninstallIfReady() {
        if (!prefs.getBoolean(PREF_PENDING_EXTERNAL_UNINSTALL, false)) return
        val detected = withContext(Dispatchers.IO) { detectTarget(context) }
        if (detected != null) return

        prefs.edit().putBoolean(PREF_PENDING_EXTERNAL_UNINSTALL, false).apply()
        externalUninstallPending = false

        val current = PatchJobHost.step.value
        if (current is PatchStep.NeedsUninstall) {
            // The user already confirmed the destructive step and the package is now absent.
            // Calling install(false) cannot ask for uninstall again, so it proceeds straight to
            // the split-aware PackageInstaller session using the files already produced.
            PatchJobHost.install(uninstallFirst = false)
            return
        }

        // Process/activity recreation loses PatchJobHost's in-memory state, but the patched APKs
        // deliberately live in noBackupFilesDir. Recover them instead of forcing a full re-patch.
        val outputs = PatchOutputStore.outputs(TARGET_PACKAGE)
        recoveredOutputs = outputs
        if (outputs.isNotEmpty()) {
            installRecoveredOutputs(outputs)
        } else {
            recoveryInstallStatus = "官方版已卸载，但没有找到可恢复的修补输出。"
        }
    }

    LaunchedEffect(resumeTick) {
        refresh()
        continueAfterExternalUninstallIfReady()

        // Some OEM installers complete the install but omit the final callback. The package itself
        // is the authoritative result, so recognise it on return and clean stale output.
        val installed = withContext(Dispatchers.IO) { detectTarget(context) }
        if (installed?.alreadyPatched == true) {
            target = installed
            if (recoveredOutputs.isNotEmpty()) {
                PatchOutputStore.discard(TARGET_PACKAGE)
                recoveredOutputs = emptyList()
            }
            if (recoveryBusy || recoveryInstallStatus != null) {
                recoveryInstallStatus = "安装成功 ✓"
                recoveryBusy = false
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("DLsiteSound 无 Root 字幕补丁器") }) },
    ) { padding ->
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 18.dp)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(2.dp))

            StatusCard(title = "DLsiteSound") {
                when {
                    loading -> Text("正在检测已安装应用……")
                    target == null && recoveredOutputs.isNotEmpty() -> {
                        Text("当前未安装 DLsiteSound。")
                        Text("检测到上一次已生成的修补输出：基础包 + ${recoveredOutputs.size - 1} 个分包 ✓")
                        Text("可以直接继续安装，无需重新安装官方版再修补。")
                    }
                    target == null -> Text("未检测到 $TARGET_PACKAGE。请先从官方渠道安装 DLsiteSound。")
                    else -> {
                        val t = target!!
                        Text("${t.versionName} (${t.versionCode})")
                        Text("基础包 + ${t.apkPaths.size - 1} 个分包")
                        Text(if (t.verified) "已验证版本 ✓" else "未验证版本 ⚠ 仍可自行尝试")
                        if (t.alreadyPatched) {
                            Text("当前 DLsiteSound 已经是 LSPatch 修补版。Quick 模式为避免嵌套修补不会继续。")
                        }
                    }
                }
            }

            StatusCard(title = "内置模块 · DLsiteFloat") {
                when {
                    moduleFile != null -> {
                        Text("v2.1.0 + 悬浮播放器增强 ✓")
                        Text("已打包在补丁器内，无需单独安装 DLsiteFloat")
                        Text(MODULE_PACKAGE)
                    }
                    loading -> Text("正在准备内置模块……")
                    else -> Text("内置模块准备失败")
                }
            }

            StatusCard(title = "固定修补参数") {
                Text("Integrated / 集成模式")
                Text("Signature bypass: Level 2")
                Text("模块: DLsiteFloat 2.1.0 + Rootless Controls v1")
                Text("悬浮窗支持播放控制；不修补 SystemUI")
            }

            startupError?.let {
                StatusCard(title = "准备失败") { Text(it) }
            }

            if (target == null && recoveredOutputs.isNotEmpty()) {
                StatusCard(title = "可恢复的修补结果") {
                    Text("共 ${recoveredOutputs.size} 个 APK，文件仍保存在补丁器私有目录。")
                    recoveryInstallStatus?.let { Text(it) }
                    Button(
                        enabled = !recoveryBusy,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            if (!ensureInstallPermission(context)) return@Button
                            val files = recoveredOutputs
                            scope.launch { installRecoveredOutputs(files) }
                        },
                    ) {
                        Text(if (recoveryBusy) "正在安装……" else "继续安装已修补版本")
                    }
                }
            }

            PatchStateCard(
                step = patchStep,
                onInstall = {
                    if (ensureInstallPermission(context)) PatchJobHost.install()
                },
                onConfirmUninstall = {
                    if (!ensureInstallPermission(context)) return@PatchStateCard
                    prefs.edit().putBoolean(PREF_PENDING_EXTERNAL_UNINSTALL, true).apply()
                    externalUninstallPending = true
                    val intent =
                        Intent(Intent.ACTION_DELETE, Uri.parse("package:$TARGET_PACKAGE"))
                            .putExtra(Intent.EXTRA_RETURN_RESULT, true)
                    val opened = runCatching { context.startActivity(intent) }.isSuccess
                    if (!opened) {
                        prefs.edit().putBoolean(PREF_PENDING_EXTERNAL_UNINSTALL, false).apply()
                        externalUninstallPending = false
                        recoveryInstallStatus = "无法打开系统卸载界面。"
                    }
                },
                onRetry = { PatchJobHost.retry() },
                externalUninstallPending = externalUninstallPending,
            )

            val canStart =
                !loading &&
                    target != null &&
                    moduleFile != null &&
                    target?.alreadyPatched == false &&
                    (patchStep is PatchStep.Idle || patchStep is PatchStep.Failed)

            Button(
                enabled = canStart,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val t = target ?: return@Button
                    val module = moduleFile ?: return@Button
                    scope.launch {
                        val request =
                            PatchRequest(
                                token = UUID.randomUUID().toString(),
                                target =
                                    PatchTarget.InstalledApp(
                                        packageName = TARGET_PACKAGE,
                                        label = t.label,
                                        apkPaths = t.apkPaths,
                                    ),
                                mode = PatchMode.Integrated,
                                debuggable = false,
                                sigBypassLevel = 2,
                                injectDex = false,
                                modules =
                                    listOf(
                                        ModuleRef(
                                            packageName = MODULE_PACKAGE,
                                            apkPath = module.absolutePath,
                                            origin = ModuleOrigin.Picked,
                                        )
                                    ),
                                extractNativeLibs = false,
                                usesCleartextTraffic = false,
                                injectDocumentsProvider = false,
                            )
                        PatchRequestStore.put(request)
                        PatchJobHost.start(request)
                    }
                },
            ) {
                Text("生成无 Root 字幕版")
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        scope.launch {
                            if (patchStep is PatchStep.Done || patchStep is PatchStep.Failed) {
                                PatchJobHost.acknowledge()
                            }
                            refresh()
                        }
                    },
                ) {
                    Text("重新检测")
                }
                OutlinedButton(modifier = Modifier.weight(1f), onClick = openAdvanced) {
                    Text("高级 LSPatch")
                }
            }

            if (patchStep is PatchStep.Done) {
                Button(modifier = Modifier.fillMaxWidth(), onClick = openOverlaySettings) {
                    Text("开启“显示在其他应用上层”")
                }
                OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = launchTarget) {
                    Text("打开 DLsiteSound")
                }
            }

            Text(
                "注意：官方版与修补版签名不同。需要卸载官方版时，Android 会清除 DLsiteSound 的本地应用数据、登录状态及应用内离线内容；补丁器不会在未确认时自动卸载。",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "在执行安装或卸载前，补丁器会先检查“允许来自此来源安装应用”权限。未开启时只会打开系统权限页，不会先卸载 DLsiteSound；开启后返回再点一次即可。",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "本工具不包含 DLsiteSound APK，只处理设备上已安装的副本。",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StatusCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(title, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun PatchStateCard(
    step: PatchStep,
    onInstall: () -> Unit,
    onConfirmUninstall: () -> Unit,
    onRetry: () -> Unit,
    externalUninstallPending: Boolean,
) {
    if (step is PatchStep.Idle) return

    StatusCard(title = "修补状态") {
        when (step) {
            is PatchStep.Idle -> Unit
            is PatchStep.Preparing -> {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("正在准备……")
            }
            is PatchStep.Running -> {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(stageLabel(step.stage))
                Text("APK ${step.apkIndex}/${step.apkCount}")
            }
            is PatchStep.Patched -> {
                Text("修补完成 ✓  共 ${step.files.size} 个 APK")
                Button(modifier = Modifier.fillMaxWidth(), onClick = onInstall) { Text("安装修补版") }
            }
            is PatchStep.NeedsUninstall -> {
                Text("检测到官方签名版本。Android 不允许不同签名直接覆盖安装。")
                Text("继续会先打开系统卸载界面；卸载完成返回后，补丁器会确认包已消失，再自动进入修补版安装。")
                if (externalUninstallPending) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("已请求卸载。请完成系统确认；返回本页后会自动继续安装。")
                }
                Button(modifier = Modifier.fillMaxWidth(), onClick = onConfirmUninstall) {
                    Text(if (externalUninstallPending) "重新打开卸载界面" else "我已备份，确认卸载并安装")
                }
            }
            is PatchStep.Uninstalling -> {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("等待卸载确认……")
            }
            is PatchStep.Installing -> {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("正在安装修补版……")
            }
            is PatchStep.Confirming -> {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("请在 Android 系统安装界面确认。")
            }
            is PatchStep.Done -> Text("安装成功 ✓")
            is PatchStep.Failed -> {
                Text("失败：${step.reason ?: "未知错误"}")
                OutlinedButton(onClick = onRetry) { Text("重试") }
            }
            is PatchStep.Restoring -> {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("正在恢复……")
            }
        }
    }
}

private fun stageLabel(stage: PatchStage): String =
    when (stage) {
        PatchStage.ReadingApk -> "读取 APK"
        PatchStage.SigningSetup -> "签名准备"
        PatchStage.RewritingManifest -> "重写清单"
        PatchStage.InjectingLoader -> "注入加载器"
        PatchStage.EmbeddingModules -> "嵌入 DLsiteFloat"
        PatchStage.PackingSplit -> "处理 Split APK"
        PatchStage.WritingAndSigning -> "写入并签名（通常最耗时）"
        PatchStage.Finished -> "完成"
    }

private fun detectTarget(context: Context): TargetSnapshot? {
    val pm = context.packageManager
    val app = runCatching { pm.getApplicationInfo(TARGET_PACKAGE, PackageManager.GET_META_DATA) }.getOrNull()
        ?: return null
    val pkg = pm.getPackageInfo(TARGET_PACKAGE, 0)
    val versionName = pkg.versionName ?: "?"
    val versionCode = pkg.longVersionCode
    val paths = listOf(app.sourceDir) + (app.splitSourceDirs ?: emptyArray())
    return TargetSnapshot(
        label = pm.getApplicationLabel(app).toString(),
        versionName = versionName,
        versionCode = versionCode,
        apkPaths = paths,
        alreadyPatched = app.metaData?.containsKey("lspatch") == true,
    )
}


private fun ensureInstallPermission(context: Context): Boolean {
    if (context.packageManager.canRequestPackageInstalls()) return true
    val opened =
        runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}"),
                )
            )
            true
        }.getOrDefault(false)
    // If an OEM removed the per-source settings activity, let PackageInstaller try its own
    // confirmation path rather than making installation impossible from this UI.
    return !opened
}

private fun prepareBundledModule(context: Context): File {
    val dir = File(context.noBackupFilesDir, "quickpatch").apply { mkdirs() }
    val out = File(dir, MODULE_FILE)
    if (!out.exists()) {
        context.assets.open(MODULE_ASSET).use { input ->
            out.outputStream().use { output -> input.copyTo(output) }
        }
    }
    return out
}

private fun sha256(file: File): String {
    val md = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n <= 0) break
            md.update(buffer, 0, n)
        }
    }
    return md.digest().joinToString("") { "%02x".format(it) }
}
