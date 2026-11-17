package io.github.kalpa0448.taskmgr.compose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import androidx.core.view.WindowCompat

/** Compose 版主界面（新架构） */
class ComposeMainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)

        val sp = getSharedPreferences("tm", MODE_PRIVATE)
        val theme = ThemeState(sp)
        theme.reload(this)

        setContent {
            val t = remember { theme }
            AppRoot(t)
        }
    }
}
