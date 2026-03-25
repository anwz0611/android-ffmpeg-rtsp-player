package com.jxj.ffmpegrtspplayer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/**
 * 为示例页面统一处理状态栏、刘海屏和导航栏内边距。
 */
abstract class BaseInsetsActivity : AppCompatActivity() {

    private var pendingLegacyStorageGrantedAction: (() -> Unit)? = null
    private var pendingLegacyStorageDeniedAction: (() -> Unit)? = null

    private val legacyStoragePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pendingLegacyStorageGrantedAction?.invoke()
        } else {
            pendingLegacyStorageDeniedAction?.invoke()
                ?: Toast.makeText(this, "未授予外部存储权限，无法保存到系统相册", Toast.LENGTH_SHORT).show()
        }
        pendingLegacyStorageGrantedAction = null
        pendingLegacyStorageDeniedAction = null
    }

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

    protected fun runWithMediaStoreWriteAccess(
        onGranted: () -> Unit,
        onDenied: (() -> Unit)? = null
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            onGranted()
            return
        }

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            onGranted()
            return
        }

        pendingLegacyStorageGrantedAction = onGranted
        pendingLegacyStorageDeniedAction = onDenied
        legacyStoragePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }
}
