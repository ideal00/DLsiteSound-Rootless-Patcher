package org.lsposed.lspatch.ui.activity

import android.content.Context
import android.content.Intent
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
import org.lsposed.lspatch.data.repository.PatchRequestStore
import org.lsposed.lspatch.lspApp

private const val TARGET_PACKAGE = "jp.co.eisys.dlsitesound"
private const val MODULE_PACKAGE = "io.github.ariinyume.dlsitesoundfloat"
private const val MODULE_ASSET = "quickpatch/DLsiteFloat-2.1.0-debug.apk"
private const val MODULE_SHA256 = "c7c15e16f8afd7b3266ed6d38d08b0380e8ae9fc80a8a5e8a05a039fa86eede1"
private const val VERIFIED_VERSION_NAME = "2.19.0"
private const val VERIFIED_VERSION_CODE = 573L

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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lspApp.startBackgroundWork()
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    QuickPatchScreen(
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
    openAdvanced: () -> Unit,
    openOverlaySettings: () -> Unit,
    launchTarget: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val patchStep by PatchJobHost.step.collectAsState()

    var target by remember { mutableStateOf<TargetSnapshot?>(null) }
    var moduleFile by remember { mutableStateOf<File?>(null) }
    var startupError by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }

    suspend fun refresh() {
        loading = true
        startupError = null
        runCatching {
            val prepared = withContext(Dispatchers.IO) { prepareBundledModule(context) }
            val detected = withContext(Dispatchers.IO) { detectTarget(context) }
            prepared to detected
        }.onSuccess { (module, detected) ->
            moduleFile = module
            target = detected
        }.onFailure {
            startupError = it.message ?: it.javaClass.simpleName
        }
        loading = false
    }

    LaunchedEffect(Unit) { refresh() }

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

            StatusCard(title = "DLsiteFloat") {
                when {
                    moduleFile != null -> {
                        Text("v2.1.0 ✓")
                        Text(MODULE_PACKAGE)
                    }
                    loading -> Text("正在准备内置模块……")
                    else -> Text("模块准备失败")
                }
            }

            StatusCard(title = "固定修补参数") {
                Text("Integrated / 集成模式")
                Text("Signature bypass: Level 2")
                Text("模块: DLsiteFloat 2.1.0")
                Text("不修补 SystemUI；目标是悬浮字幕")
            }

            startupError?.let {
                StatusCard(title = "准备失败") { Text(it) }
            }

            PatchStateCard(
                step = patchStep,
                onInstall = { PatchJobHost.install() },
                onConfirmUninstall = { PatchJobHost.install(uninstallFirst = true) },
                onRetry = { PatchJobHost.retry() },
            )

            val canStart =
                !loading &&
                    target != null &&
                    moduleFile != null &&
                    target?.alreadyPatched == false &&
                    (patchStep is PatchStep.Idle || patchStep is PatchStep.Failed || patchStep is PatchStep.Done)

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
                OutlinedButton(modifier = Modifier.weight(1f), onClick = { scope.launch { refresh() } }) {
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
                Text("继续会先卸载官方 DLsiteSound，并清除它的本地应用数据。")
                Button(modifier = Modifier.fillMaxWidth(), onClick = onConfirmUninstall) {
                    Text("我已备份，确认卸载并安装")
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

private fun prepareBundledModule(context: Context): File {
    val dir = File(context.noBackupFilesDir, "quickpatch").apply { mkdirs() }
    val out = File(dir, "DLsiteFloat-2.1.0-debug.apk")
    if (!out.exists() || sha256(out) != MODULE_SHA256) {
        context.assets.open(MODULE_ASSET).use { input ->
            out.outputStream().use { output -> input.copyTo(output) }
        }
    }
    val digest = sha256(out)
    check(digest == MODULE_SHA256) {
        "DLsiteFloat 校验失败：期望 $MODULE_SHA256，实际 $digest"
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
