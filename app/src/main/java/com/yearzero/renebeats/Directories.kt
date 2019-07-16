package com.yearzero.renebeats

import android.content.Context
import android.os.Environment
import android.util.Log
import com.yearzero.renebeats.download.Download
import com.yearzero.renebeats.preferences.Preferences
import org.apache.commons.io.FileUtils
import java.io.*

object Directories {
    @JvmStatic private val TAG = "Directories"
    @JvmStatic var CACHE = File(Environment.getExternalStorageDirectory().absolutePath, BuildConfig.Dir_Permanent_ChildPath)
    @JvmStatic var PERMANENT = File(Environment.getExternalStorageDirectory().absolutePath, BuildConfig.Dir_Permanent_ChildPath)
    @JvmStatic var BIN = File(CACHE, BuildConfig.Dir_BIN_ChildPath)
    @JvmStatic var LOGS = File(CACHE, BuildConfig.Dir_LOGS_ChildPath)
    @JvmStatic var HISTORY = File(PERMANENT, BuildConfig.Dir_History_ChildPath)
    @JvmStatic var DOWNLOADS = File(CACHE, BuildConfig.Dir_Downloads_File)
    @JvmStatic var MUSIC = File(Environment.getExternalStorageDirectory(), "/Music/")

    @JvmStatic val isExternalStorageAvailable: Boolean
        get() = Environment.MEDIA_MOUNTED == Environment.getExternalStorageState()

    @JvmStatic val cacheSize: Long
        get() = BIN.length()

    @JvmStatic val historySize: Long
        get() = HISTORY.length()

    @JvmStatic val logsSize: Long
        get() = LOGS.length()

    @JvmStatic val musicPath: String
        get() = MUSIC.absolutePath

    @JvmStatic val logsList: Array<String>
        get() = if(LOGS.exists()) LOGS.list { _, name -> name.endsWith(".log") } else arrayOf()

    @JvmStatic fun reinitialize(context: Context) {
        CACHE = context.cacheDir
        PERMANENT = context.filesDir
        BIN = File(CACHE, "/bin/")
        HISTORY = File(PERMANENT, "/history/")
        DOWNLOADS = File(CACHE, "/queue.dat")
    }

    @JvmStatic fun clearCache(): IOException? = deleteDir(BIN)

    @JvmStatic fun deleteHistory(): IOException? = deleteDir(HISTORY)

    @JvmStatic fun clearLogs(): IOException? = deleteDir(LOGS)

    @JvmStatic private fun deleteDir(dir: File): IOException? = try {
            FileUtils.deleteDirectory(dir)
            null
        } catch (e: IOException) {
            e.printStackTrace()
            e
        }

    @JvmStatic fun isCacheExists(file: String): Boolean = File(BIN, file).exists()

    @JvmStatic fun setOutputDir(path: String) = File(path).let{
        if (it.isDirectory) {
            MUSIC = it
            Preferences.save()
        }
    }

    @JvmStatic fun readLog(path: String): String? {
        val file = File(LOGS, path)
        return if (file.exists()) file.readText(Charsets.UTF_8) else null
    }

    @JvmStatic internal fun saveQueue(array: Array<Download>): Boolean {
        try {
            if (DOWNLOADS.exists() || (Directories.CACHE.exists() || Directories.CACHE.mkdirs()) && DOWNLOADS.createNewFile()) {
                val file = FileOutputStream(DOWNLOADS)
                val stream = ObjectOutputStream(file)
                //            stream.writeObject(new DownloadQueuePackage(queue, completed));
                stream.writeObject(array)
                stream.close()
                file.close()
                return true
            } else
                Log.e(TAG, "Failed to create queue file/dir")
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return false
    }

    @JvmStatic fun loadQueue(): Array<Download>? {
        if (!DOWNLOADS.exists()) return null
        try {
            val file = FileInputStream(DOWNLOADS)
            val stream = ObjectInputStream(file)
            @Suppress("UNCHECKED_CAST") val data: Array<Download> = stream.readObject() as Array<Download>
            stream.close()
            file.close()

            return data
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
}
