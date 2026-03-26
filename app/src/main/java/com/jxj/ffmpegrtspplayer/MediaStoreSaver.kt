package com.jxj.ffmpegrtspplayer

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

object MediaStoreSaver {

    private const val APP_ALBUM_DIR = "FfmpegRtspPlayer"

    enum class Collection(
        val relativeDirectory: String,
        val legacyDirectory: String,
        val cacheDirectoryName: String
    ) {
        IMAGE(
            "${Environment.DIRECTORY_PICTURES}/$APP_ALBUM_DIR",
            Environment.DIRECTORY_PICTURES,
            "images"
        ),
        VIDEO(
            "${Environment.DIRECTORY_MOVIES}/$APP_ALBUM_DIR",
            Environment.DIRECTORY_MOVIES,
            "videos"
        )
    }

    data class SavedMedia(
        val uri: Uri,
        val displayPath: String
    )

    fun buildAlbumDisplayPath(collection: Collection, displayName: String): String {
        return "${collection.relativeDirectory}/$displayName"
    }

    fun createPendingFile(context: Context, collection: Collection, displayName: String): File {
        val cacheRoot = context.externalCacheDir ?: context.cacheDir
        val pendingDir = File(cacheRoot, "pending_media/${collection.cacheDirectoryName}")
        if (!pendingDir.exists() && !pendingDir.mkdirs()) {
            throw IOException("无法创建临时目录: ${pendingDir.absolutePath}")
        }
        return File(pendingDir, displayName)
    }

    fun saveToAlbum(
        context: Context,
        sourceFile: File,
        displayName: String,
        mimeType: String,
        collection: Collection
    ): SavedMedia {
        require(sourceFile.exists()) { "源文件不存在: ${sourceFile.absolutePath}" }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveWithMediaStore(context, sourceFile, displayName, mimeType, collection)
        } else {
            saveToLegacyPublicDir(context, sourceFile, displayName, mimeType, collection)
        }
    }

    fun awaitFileReady(
        file: File,
        timeoutMs: Long = TimeUnit.SECONDS.toMillis(3),
        pollIntervalMs: Long = 120L
    ): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        var lastKnownSize = -1L
        var stableChecks = 0

        while (System.currentTimeMillis() < deadline) {
            if (file.exists()) {
                val length = file.length()
                if (length > 0L && length == lastKnownSize) {
                    stableChecks++
                    if (stableChecks >= 2) {
                        return true
                    }
                } else {
                    stableChecks = 0
                    lastKnownSize = length
                }
            }
            Thread.sleep(pollIntervalMs)
        }

        return file.exists() && file.length() > 0L
    }

    private fun saveWithMediaStore(
        context: Context,
        sourceFile: File,
        displayName: String,
        mimeType: String,
        collection: Collection
    ): SavedMedia {
        val resolver = context.contentResolver
        val collectionUri = when (collection) {
            Collection.IMAGE -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            Collection.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, collection.relativeDirectory)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val itemUri = resolver.insert(collectionUri, values)
            ?: throw IOException("创建媒体库条目失败")

        try {
            resolver.openOutputStream(itemUri)?.use { output ->
                sourceFile.inputStream().use { input -> input.copyTo(output) }
            } ?: throw IOException("打开媒体库输出流失败")

            val completedValues = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            resolver.update(itemUri, completedValues, null, null)
            return SavedMedia(itemUri, buildAlbumDisplayPath(collection, displayName))
        } catch (error: Exception) {
            resolver.delete(itemUri, null, null)
            throw error
        }
    }

    @Suppress("DEPRECATION")
    private fun saveToLegacyPublicDir(
        context: Context,
        sourceFile: File,
        displayName: String,
        mimeType: String,
        collection: Collection
    ): SavedMedia {
        val baseDirectory = Environment.getExternalStoragePublicDirectory(collection.legacyDirectory)
        val albumDirectory = File(baseDirectory, APP_ALBUM_DIR)
        if (!albumDirectory.exists() && !albumDirectory.mkdirs()) {
            throw IOException("创建相册目录失败: ${albumDirectory.absolutePath}")
        }

        val targetFile = File(albumDirectory, displayName)
        sourceFile.inputStream().use { input ->
            targetFile.outputStream().use { output -> input.copyTo(output) }
        }

        MediaScannerConnection.scanFile(
            context,
            arrayOf(targetFile.absolutePath),
            arrayOf(mimeType),
            null
        )

        return SavedMedia(Uri.fromFile(targetFile), targetFile.absolutePath)
    }
}
