package com.jxj.ffmpegrtspplayer

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/**
 * 为示例页面统一处理状态栏、刘海屏和导航栏内边距。
 */
abstract class BaseInsetsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
    }

    protected fun applyEdgeToEdge(root: View, top: Boolean = true, bottom: Boolean = false) {
        val initialPaddingStart = root.paddingStart
        val initialPaddingTop = root.paddingTop
        val initialPaddingEnd = root.paddingEnd
        val initialPaddingBottom = root.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.updatePadding(
                left = initialPaddingStart + insets.left,
                top = initialPaddingTop + if (top) insets.top else 0,
                right = initialPaddingEnd + insets.right,
                bottom = initialPaddingBottom + if (bottom) insets.bottom else 0
            )
            WindowInsetsCompat.Builder(windowInsets)
                .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.NONE)
                .build()
        }
        ViewCompat.requestApplyInsets(root)
    }
}
