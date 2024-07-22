package com.lepu.vtm01

import android.app.Application
import android.os.Build
import io.getstream.log.CompositeStreamLogger
import io.getstream.log.StreamLog
import io.getstream.log.file.FileStreamLogger
import io.getstream.log.kotlin.KotlinStreamLogger


/**
 *
 *  说明:
 *  zrj 2024/7/1 10:56
 *
 */
class App : Application() {

    override fun onCreate() {
        super.onCreate()
//        AndroidStreamLogger.installOnDebuggableApp(this)

        val info = packageManager?.getPackageInfo(packageName, 0)
        StreamLog.install(
            CompositeStreamLogger(
                KotlinStreamLogger(), FileStreamLogger(
                    FileStreamLogger.Config(
                        maxLogSize = 1024 * 1024 * 50, // 50MB
                        filesDir = this.filesDir,
                        externalFilesDir = this.getExternalFilesDir(null),
                        app = FileStreamLogger.Config.App(
                            versionCode = info?.versionCode?.toLong() ?: -1L,
                            versionName = info?.versionName ?: ""
                        ),
                        device = FileStreamLogger.Config.Device(
                            model = "%s %s".format(Build.MANUFACTURER, Build.DEVICE),
                            androidApiLevel = Build.VERSION.SDK_INT
                        )
                    )
                )
            )
        )
    }
}