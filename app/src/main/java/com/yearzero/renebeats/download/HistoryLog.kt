package com.yearzero.renebeats.download

import com.yearzero.renebeats.BuildConfig
import com.yearzero.renebeats.preferences.Preferences
import com.yearzero.renebeats.preferences.Preferences.artist_first
import java.io.Serializable
import java.util.*
import javax.annotation.ParametersAreNullableByDefault

@ParametersAreNullableByDefault
class HistoryLog : Serializable {
    // This class is accessed in some Kotlin files. Unfortunately, Kotlin does not cooperate well
    // with Lombok, so some manual getters and setters have to be defined
    // TODO: wait for better Kotlin and Lombok interoperability
    //TODO: Merge with Download and use custom Serializer or Annotations?
    var version: String? = null
        private set
    private var versionCode = 0
    var isConvert = false
        private set
    var isNormalize = false
        private set
    private var overwrite = false
    var bitrate: Short = 0
        private set
    private var track = 0
    var year = 0
        private set
    private var downloadId = 0
    var id: Long = 0
        private set
    var start: Int? = null
        private set
    var end: Int? = null
        private set
    private var album: String? = null
    private var artist: String? = null
    private var availableFormat: String? = null
    private var conv: String? = null
    private var down: String? = null
    var format: String? = null
        private set
    private var mtdt: String? = null
    var title: String? = null
        private set
    private var url: String? = null
    private var youtubeID: String? = null
    private var genres: String? = null
    var assigned: Date? = null
        private set
    var completed: Date? = null
        private set
    var statusDownload: String? = null
        private set
    var statusConvert: String? = null
        private set
    var statusMeta: Boolean? = null
        private set
    private var invalid = false
    var exception: Exception? = null
        private set
    val filename: String?
        get() {
            if (artist != null && title != null) return if (artist_first) "$artist - $title" else "$title - $artist"
            if (artist != null) return artist
            return if (title != null) title else null
        }
    val status: Status
        get() = Status(
            Status.Download.fromValue(statusDownload),
            Status.Convert.fromValue(statusConvert),
            statusMeta
        )

    fun uncast(): Download {
        val d = Download(
            Query(youtubeID, title!!, artist!!, album!!, year, track, genres!!),
            bitrate,
            (if (format == null) Preferences.format else format!!),
            url,
            start,
            end,
            isNormalize,
            0L
        )
        d.convert = isConvert
        d.overwrite = overwrite
        d.downloadId = downloadId
        d.id = id
        d.availableFormat = availableFormat
        d.conv = conv
        d.down = down
        d.mtdt = mtdt
        d.assigned = assigned
        d.completeDate = completed
        d.status = status
        d.exception = exception
        return d
    }

    companion object {
        // Appcode (EA50) - "Class" (ClA5) - Class ID (415C_1066)
        private const val serialVersionUID = -0x15af3e5abea3ef9aL
        @JvmStatic
		fun generate(data: Download): HistoryLog {
            val log = cast(data)
            log.version = BuildConfig.VERSION_NAME
            log.versionCode = BuildConfig.VERSION_CODE
            return log
        }

        private fun cast(data: Download): HistoryLog {
            val log = HistoryLog()
            log.isConvert = data.convert
            log.isNormalize = data.normalize
            log.overwrite = data.overwrite
            log.bitrate = data.bitrate
            log.track = data.track
            log.year = data.year
            log.downloadId = data.downloadId
            log.id = data.id
            log.start = data.start
            log.end = data.end
            log.album = data.album
            log.artist = data.artist
            log.availableFormat = data.availableFormat
            log.conv = data.conv
            log.down = data.down
            log.format = data.format
            log.mtdt = data.mtdt
            log.title = data.title
            log.url = data.url
            log.youtubeID = data.youtubeID
            log.genres = data.genres
            log.assigned = data.assigned
            log.completed = data.completeDate
            log.statusDownload = data.status.download?.value
            log.statusConvert = data.status.convert?.value
            log.statusMeta = data.status.metadata
            log.invalid = data.status.isInvalid
            log.exception = data.exception
            return log
        }
    }
}