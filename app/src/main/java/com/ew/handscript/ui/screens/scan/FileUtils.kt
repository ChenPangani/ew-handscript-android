package com.ew.handscript.ui.screens.scan

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*

object FileUtils {

    /**
     * 创建临时拍照文件
     */
    fun createImageFile(context: Context): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        // 优先使用外部存储目录，如果不可用则使用内部存储目录
        val storageDir: File = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) 
            ?: context.filesDir
        return File.createTempFile(
            "JPEG_${timeStamp}_",
            ".jpg",
            storageDir
        ).apply {
            deleteOnExit()
        }
    }

    /**
     * 获取FileProvider的Uri
     */
    fun getFileProviderUri(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    /**
     * 从Uri获取文件路径（处理Android 10+的限制）
     */
    fun getRealPathFromUri(context: Context, uri: Uri): String? {
        val contentResolver = context.contentResolver
        
        // 如果是文件Uri，直接返回路径
        if (uri.scheme == "file") {
            return uri.path
        }
        
        // 处理content://类型的Uri
        if (uri.scheme == "content") {
            // 尝试通过MediaStore获取路径
            val projection = arrayOf(MediaStore.Images.Media.DATA)
            try {
                val cursor = contentResolver.query(uri, projection, null, null, null)
                cursor?.use {
                    val columnIndex = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                    if (it.moveToFirst()) {
                        return it.getString(columnIndex)
                    }
                }
            } catch (e: Exception) {
                // MediaStore方式失败，尝试复制文件
            }
            
            // 复制文件到应用私有目录
            return copyUriToPrivateFile(context, uri)
        }
        
        return null
    }

    /**
     * 将Uri指向的文件复制到应用私有目录
     */
    private fun copyUriToPrivateFile(context: Context, uri: Uri): String? {
        return try {
            val contentResolver = context.contentResolver
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            inputStream?.use { input ->
                val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val outputFile = File(context.filesDir, "selected_${timeStamp}.jpg")
                FileOutputStream(outputFile).use { output ->
                    input.copyTo(output)
                }
                outputFile.absolutePath
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 从Uri读取Bitmap
     */
    fun decodeBitmapFromUri(contentResolver: ContentResolver, uri: Uri): Bitmap? {
        return try {
            val parcelFileDescriptor = contentResolver.openFileDescriptor(uri, "r")
            parcelFileDescriptor?.use { pfd ->
                val fileDescriptor: FileDescriptor = pfd.fileDescriptor
                val bitmap = BitmapFactory.decodeFileDescriptor(fileDescriptor)
                bitmap
            }
        } catch (e: Exception) {
            null
        }
    }
}
