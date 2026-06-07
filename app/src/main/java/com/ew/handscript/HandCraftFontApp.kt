package com.ew.handscript

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import org.opencv.android.OpenCVLoader
import timber.log.Timber
import javax.inject.Inject

/**
 * HandCraft Font 应用入口
 *
 * 初始化内容：
 * 1. Timber日志系统
 * 2. OpenCV库加载
 * 3. Hilt依赖注入
 * 4. WorkManager配置
 */
@HiltAndroidApp
class HandCraftFontApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()

        // 1. 初始化日志系统
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        // 2. 加载OpenCV库
        if (!OpenCVLoader.initLocal()) {
            Timber.e("OpenCV 初始化失败！")
        } else {
            Timber.i("OpenCV 初始化成功")
        }

        Timber.i("HandCraft Font 应用启动")
    }

    /**
     * WorkManager配置 - 使用HiltWorkerFactory支持Worker的依赖注入
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
}
