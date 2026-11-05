package com.cx330.toolbox

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.cx330.toolbox.ui.AppListScreen
import com.cx330.toolbox.ui.BuiltinTools
import com.cx330.toolbox.ui.DiagnosticsScreen
import com.cx330.toolbox.ui.InfoScreen
import com.cx330.toolbox.ui.MainMenuScreen
import com.cx330.toolbox.ui.RuleImportScreen
import com.cx330.toolbox.ui.RuleStudioScreen
import com.cx330.toolbox.ui.SplashScreen
import com.cx330.toolbox.ui.ToolDetailHost
import com.cx330.toolbox.ui.ToolNavigation
import com.cx330.toolbox.ui.ToolRegistry
import com.cx330.toolbox.ui.theme.Cx330toolboxTheme
import kotlinx.coroutines.delay

/**
 * 导航目的地。
 *
 * 工具页只记 id（[Screen.Tool]），具体工具由 ToolRegistry 按 id 查出来再渲染；
 * 所以新增工具时这里不需要再加分支，主流程保持不变。
 */
private sealed interface Screen {
    data object Menu : Screen
    data object Studio : Screen
    data class Tool(val id: String) : Screen
    data object Apps : Screen
    data object Rules : Screen
    data object Diag : Screen
    data object Info : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Cx330toolboxTheme {
                var showSplash by remember { mutableStateOf(true) }
                val localView = LocalView.current
                LaunchedEffect(showSplash) {
                    val activity = localView.context as? Activity ?: return@LaunchedEffect
                    val controller = WindowCompat.getInsetsController(activity.window, localView)
                    if (showSplash) {
                        controller.hide(WindowInsetsCompat.Type.systemBars())
                        controller.systemBarsBehavior =
                            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    } else {
                        controller.show(WindowInsetsCompat.Type.systemBars())
                    }
                }
                LaunchedEffect(Unit) {
                    delay(SPLASH_DURATION_MS)
                    showSplash = false
                }

                if (showSplash) {
                    SplashScreen()
                } else {
                    val registry = remember { ToolRegistry() }
                    var screen by remember { mutableStateOf<Screen>(Screen.Menu) }
                    // 记录“最近打开的工具”：应用列表/规则/诊断返回到它，而不是写死某个工具。
                    var currentToolId by remember { mutableStateOf(BuiltinTools.SKIP_SPLASH_ID) }

                    // 主页卡片只回传 id，路由统一走这里。
                    fun openTool(id: String) {
                        currentToolId = id
                        screen = Screen.Tool(id)
                    }

                    BackHandler(enabled = screen != Screen.Menu) {
                        screen = when (screen) {
                            Screen.Apps, Screen.Rules, Screen.Diag -> Screen.Tool(currentToolId)
                            else -> Screen.Menu
                        }
                    }

                    when (val current = screen) {
                        Screen.Menu -> MainMenuScreen(
                            onOpenTool = ::openTool,
                            onOpenStudio = { screen = Screen.Studio },
                            onOpenInfo = { screen = Screen.Info },
                            registry = registry,
                        )

                        is Screen.Tool -> {
                            val tool = registry.find(current.id)
                            if (tool == null) {
                                // id 失效（比如旧状态残留）：回主页，避免白屏。
                                LaunchedEffect(current.id) { screen = Screen.Menu }
                            } else {
                                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(innerPadding),
                                    ) {
                                        ToolDetailHost(
                                            tool = tool,
                                            navigation = ToolNavigation(
                                                onBack = { screen = Screen.Menu },
                                                onOpenApps = { screen = Screen.Apps },
                                                onOpenRules = { screen = Screen.Rules },
                                                onOpenDiagnostics = { screen = Screen.Diag },
                                            ),
                                        )
                                    }
                                }
                            }
                        }

                        Screen.Studio -> RuleStudioScreen(onBack = { screen = Screen.Menu })
                        Screen.Apps -> AppListScreen(onBack = { screen = Screen.Tool(currentToolId) })
                        Screen.Rules -> RuleImportScreen(onBack = { screen = Screen.Tool(currentToolId) })
                        Screen.Diag -> DiagnosticsScreen(onBack = { screen = Screen.Tool(currentToolId) })
                        Screen.Info -> InfoScreen(onBack = { screen = Screen.Menu })
                    }
                }
            }
        }
    }

    private companion object {
        const val SPLASH_DURATION_MS = 1_200L
    }
}
