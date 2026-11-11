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
import org.lsposed.lspatch.ui.page.rePatchRequestFor
import org.lsposed.lspatch.util.LSPPackageManager

private const val TARGET_PACKAGE = "jp.co.eisys.dlsitesound"
private const val MODULE_PACKAGE = "io.github.ariinyume.dlsitesoundfloat"
private const val MODULE_ASSET = "quickpatch/DLsiteFloat-2.1.2-rootless-v5.apk"
private const val MODULE_FILE = "DLsiteFloat-2.1.2-rootless-v5.apk"
private const val VERIFIED_VERSION_NAME = "2.19.0"
private const val COMPAT_VERSION_NAME = "2.23.1"
private const val COMPAT_VERSION_CODE = 633L
private const val VERIFIED_VERSION_CODE = 573L
private const val QUICK_PREFS = "quickpatch"
private const val PREF_PENDING_EXTERNAL_UNINSTALL = "pending_external_uninstall"
private const val PREF_INSTALL_BASELINE = "install_baseline_update_time"

private data class TargetSnapshot(
    val label: String,
    val versionName: String,
    val versionCode: Long,
    val apkPaths: List<String>,
    val alreadyPatched: Boolean,
    val lastUpdateTime: Long,
) {
    val verified: Boolean
        get() = (versionName == VERIFIED_VERSION_NAME && versionCode == VERIFIED_VERSION_CODE) ||
            (versionName == COMPAT_VERSION_NAME && versionCode == COMPAT_VERSION_CODE)
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
    var recoveringSource by remember { mutableStateOf(false) }
    var installBaseline by remember {
        mutableStateOf<Long?>(if (prefs.contains(PREF_INSTALL_BASELINE)) prefs.getLong(PREF_INSTALL_BASELINE, 0L) else null)
    }

    fun markInstallAttempt() {
        if (installBaseline == null) {
            val baseline = target?.lastUpdateTime ?: 0L
            installBaseline = baseline
            prefs.edit().putLong(PREF_INSTALL_BASELINE, baseline).apply()
        }
    }

    fun clearInstallAttempt() {
        installBaseline = null
        prefs.edit().remove(PREF_INSTALL_BASELINE).apply()
    }

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
        markInstallAttempt()
        recoveryBusy = true
        recoveryInstallStatus = "正在打开系统安装器……"
        val (status, message) = LSPPackageManager.installFiles(files, useShizuku = false)
        if (status == PackageInstaller.STATUS_SUCCESS) {
            clearInstallAttempt()
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
        val baseline = installBaseline
        if (installed?.alreadyPatched == true && baseline != null && installed.lastUpdateTime > baseline) {
            clearInstallAttempt()
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
                            Text("已安装修补版：更新时会恢复内嵌原包和全部分包，再生成新版；签名相同时可覆盖安装。")
                        }
                    }
                }
            }

            StatusCard(title = "内置模块 · DLsiteFloat") {
                when {
                    moduleFile != null -> {
                        Text("v2.1.2 + Rootless Controls v5 ✓")
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
                Text("模块: DLsiteFloat 2.1.2 + Rootless Controls v5")
                Text("默认透明点击穿透；边缘控制柄进入编辑模式；不修补 SystemUI")
            }

            StatusCard(title = "Kikoeru.Extras 汉化字幕") {
                Text("先将对应音轨的 .lrc 保存到手机。播放页显示“无字幕”时，长按字幕胶囊选取文件；已有悬浮字幕时，点 ✎ → ⚙ 导入。")
                Text("导入后缓存在 DLsiteSound 本地，播放字幕不依赖网络。")
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
                    if (ensureInstallPermission(context)) {
                        markInstallAttempt()
                        PatchJobHost.install()
                    }
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
                    !recoveringSource &&
                    target != null &&
                    moduleFile != null &&
                    (patchStep is PatchStep.Idle || patchStep is PatchStep.Failed)

            Button(
                enabled = canStart,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val t = target ?: return@Button
                    val module = moduleFile ?: return@Button
                    scope.launch {
                        recoveringSource = true
                        startupError = null
                        try {
                            val modules = listOf(ModuleRef(MODULE_PACKAGE, module.absolutePath, ModuleOrigin.Picked))
                            val request =
                                if (t.alreadyPatched) {
                                    val app = withContext(Dispatchers.IO) {
                                        context.packageManager.getApplicationInfo(TARGET_PACKAGE, PackageManager.GET_META_DATA)
                                    }
                                    rePatchRequestFor(
                                        LSPPackageManager.AppInfo(app, t.label),
                                        mode = PatchMode.Integrated,
                                        modules = modules,
                                    ).copy(sigBypassLevel = 2, injectDex = false)
                                } else {
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
                                        modules = modules,
                                        extractNativeLibs = false,
                                        usesCleartextTraffic = false,
                                        injectDocumentsProvider = false,
                                    )
                                }
                            PatchRequestStore.put(request)
                            PatchJobHost.start(request)
                        } catch (error: Exception) {
                            startupError = "恢复或准备原始安装包失败：${error.message ?: error.javaClass.simpleName}"
                        } finally {
                            recoveringSource = false
                        }
                    }
                },
            ) {
                Text(if (recoveringSource) "正在恢复原始安装包……" else if (target?.alreadyPatched == true) "更新字幕模块并重新生成" else "生成无 Root 字幕版")
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
        lastUpdateTime = pkg.lastUpdateTime,
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
