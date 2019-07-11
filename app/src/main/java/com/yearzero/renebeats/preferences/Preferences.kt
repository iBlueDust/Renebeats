package com.yearzero.renebeats.preferences

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import android.text.format.DateFormat
import com.yearzero.renebeats.Directories
import com.yearzero.renebeats.download.Query
import com.yearzero.renebeats.preferences.enums.GuesserMode
import com.yearzero.renebeats.preferences.enums.OverwriteMode
import com.yearzero.renebeats.preferences.enums.ProcessSpeed
import java.io.File
import java.util.*

object Preferences {
    @JvmStatic private lateinit var SharedPref: SharedPreferences

    private const val loc_always_log_failed = "always log failed"
    private const val loc_artist_first = "file format artist_first"
    private const val loc_bitrate = "bitrate"
    private const val loc_concurrency = "concurrent downloads"
    private const val loc_format = "format"
    private const val loc_guesser_mode = "guesser mode"
    private const val loc_mobile_data = "mobiledata"
    private const val loc_normalize = "enable notifications"
    private const val loc_overwrite = "overwrite mode"
    private const val loc_process_speed = "process speed preset"
    private const val loc_query_amount = "query amount"
    private const val loc_restore = "restore all settings"
    private const val loc_timeout = "master timeout"

    private const val loc_IMG_query = "ThumbQ query"
    private const val loc_IMG_queryLarge = "ThumbQ queryLarge"
    private const val loc_IMG_download = "ThumbQ download"

    private const val loc_notifications = "notifications"
    private const val loc_notifications_queue = "notifications queue"
    private const val loc_notifications_running = "notifications running"
    private const val loc_notifications_completed = "notifications completed"

    private const val loc_outputdir = "output directory"
    
    @JvmStatic var BITRATES = shortArrayOf(64, 96, 128, 192, 256, 320)
    @JvmStatic var always_log_failed: Boolean = false
    @JvmStatic var artist_first: Boolean = false
    @JvmStatic var bitrate: Short = 128
    @JvmStatic var concurrency: Short = 1 ;internal set
    @JvmStatic var process_speed = ProcessSpeed.MEDIUM
    @JvmStatic var format: String = "mp3"
    @JvmStatic var guesser_mode = GuesserMode.Default
    @JvmStatic var mobiledata: Boolean = false
    @JvmStatic var normalize = true
    @JvmStatic var overwrite = OverwriteMode.Default
    @JvmStatic var query_amount: Short = 10
    @JvmStatic var restore: Boolean = false
    @JvmStatic var timeout = 30000
    @JvmStatic var queryImage: Query.ThumbnailQuality = Query.ThumbnailQuality.High
    @JvmStatic var queryImageLarge: Query.ThumbnailQuality = Query.ThumbnailQuality.MaxRes
    @JvmStatic var downloadImage: Query.ThumbnailQuality = Query.ThumbnailQuality.High
    @JvmStatic var notifications = true
    @JvmStatic var notifications_queue = true
    @JvmStatic var notifications_running = true
    @JvmStatic var notifications_completed = true

    @JvmStatic fun initialize(context: Context) {
        SharedPref = context.getSharedPreferences("com.yearzero.renebeats", MODE_PRIVATE)
        load()
    }

    @JvmStatic fun save() {
        if (!::SharedPref.isInitialized) return
        val editor = SharedPref.edit()
        editor.putBoolean(loc_always_log_failed, always_log_failed)
        editor.putBoolean(loc_artist_first,      artist_first)
        editor.putInt    (loc_bitrate,           bitrate.toInt())
        editor.putInt    (loc_concurrency,       concurrency.toInt())
        editor.putString (loc_format,            format)
        editor.putString (loc_guesser_mode,      guesser_mode.value)
        editor.putBoolean(loc_mobile_data,       mobiledata)
        editor.putBoolean(loc_normalize,         normalize)
        editor.putString (loc_overwrite,         overwrite.value)
        editor.putString (loc_process_speed,     overwrite.value)
        editor.putInt    (loc_query_amount,      query_amount.toInt())
        editor.putBoolean(loc_restore,           restore)
        editor.putInt    (loc_timeout,           timeout)

        editor.putString(loc_IMG_query,      queryImage.toValue())
        editor.putString(loc_IMG_queryLarge, queryImageLarge.toValue())
        editor.putString(loc_IMG_download,   downloadImage.toValue())

        editor.putBoolean(loc_notifications,           notifications)
        editor.putBoolean(loc_notifications_queue,     notifications_queue)
        editor.putBoolean(loc_notifications_running,   notifications_running)
        editor.putBoolean(loc_notifications_completed, notifications_completed)

        editor.putString(loc_outputdir, Directories.MUSIC.absolutePath)
        editor.apply()
    }

    @JvmStatic private fun load() {
        if (!::SharedPref.isInitialized) return
        restore = SharedPref.getBoolean(loc_restore, false)
        if (restore) {
            restore = false
            save()
            return
        }

        always_log_failed = SharedPref.getBoolean(loc_always_log_failed, always_log_failed)
        artist_first      = SharedPref.getBoolean(loc_artist_first, artist_first)
        bitrate           = SharedPref.getInt(loc_bitrate, bitrate.toInt()).toShort()
        concurrency       = SharedPref.getInt(loc_concurrency, concurrency.toInt()).toShort()
        format            = SharedPref.getString(loc_format, format) ?: format
        mobiledata        = SharedPref.getBoolean(loc_mobile_data, mobiledata)
        normalize         = SharedPref.getBoolean(loc_normalize, normalize)
        overwrite         = OverwriteMode.fromValue(SharedPref.getString(loc_overwrite, null))
        query_amount      = SharedPref.getInt(loc_query_amount, query_amount.toInt()).toShort()
        timeout           = SharedPref.getInt(loc_timeout, timeout)

//        queryImage      = Query.ThumbnailQuality.fromValue(SharedPref.getString(loc_IMG_query, null))
//        queryImageLarge = Query.ThumbnailQuality.fromValue(SharedPref.getString(loc_IMG_queryLarge, null))
//        downloadImage   = Query.ThumbnailQuality.fromValue(SharedPref.getString(loc_IMG_download, null))

        notifications           = SharedPref.getBoolean(loc_notifications, notifications)
        notifications_queue     = SharedPref.getBoolean(loc_notifications, notifications)
        notifications_running   = SharedPref.getBoolean(loc_notifications, notifications_running)
        notifications_completed = SharedPref.getBoolean(loc_notifications, notifications_completed)

        val path = SharedPref.getString(loc_outputdir, null)
        if (path != null) {
            val file = File(path)
            if (file.isDirectory)
                Directories.MUSIC = file
        }
    }

    @JvmStatic fun formatDateLong(context: Context, date: Date): String {
        return DateFormat.getLongDateFormat(context).format(date)
    }

    @JvmStatic fun formatDateMedium(context: Context, date: Date): String {
        return DateFormat.getMediumDateFormat(context).format(date)
    }

    @JvmStatic fun formatDate(context: Context, date: Date): String {
        return DateFormat.getDateFormat(context).format(date)
    }

    @JvmStatic fun formatTime(context: Context, date: Date): String {
        return android.text.format.DateFormat.getTimeFormat(context).format(date)
    }
}
