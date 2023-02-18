package com.yearzero.renebeats.download

import com.yearzero.renebeats.preferences.Preferences
import com.yearzero.renebeats.preferences.enums.OverwriteMode
import lombok.AccessLevel
import lombok.Getter
import lombok.Setter
import java.io.Serializable
import java.util.*
import javax.annotation.ParametersAreNullableByDefault

class Download : Query, Serializable {
    var bitrate: Short = 0
    var start: Int? = null
    var end: Int? = null
    var normalize = false
    var convert = false
    var current: Long = 0
    var indeterminate : Boolean = false
    var total: Long = 0
    var size: Long = 0

    // This class is accessed in some Kotlin files. Unfortunately, Kotlin does not cooperate well
    // with Lombok, so some manual getters and setters have to be defined
    // TODO: wait for better Kotlin and Lombok interoperability
    var id = UUID.randomUUID().leastSignificantBits
    var exception: Exception? = null
    var downloadId = 0
    var format = Preferences.format
    var url: String? = null
    var down: String? = null
    var availableFormat: String? = null
    var conv: String? = null
    var mtdt: String? = null
    var overwrite = Preferences.overwrite === OverwriteMode.OVERWRITE
    var status = Status()
    var assigned: Date? = null
    var completeDate: Date? = null

    //Note: the field names are linked to Commons.java:112
    internal constructor() {}
    internal constructor(query: Query, bitrate: Short, format: String) : super(
        query.youtubeID,
        query.title,
        query.artist,
        query.album,
        query.year,
        query.track,
        query.genres,
        query.thumbMax,
        query.thumbHigh,
        query.thumbMedium,
        query.thumbDefault,
        query.thumbStandard
    ) {
        this.bitrate = bitrate
        this.format = format
        assigned = Date()
        status = Status()
    }

    internal constructor(
        query: Query,
        bitrate: Short,
        format: String,
        url: String?,
        start: Int?,
        end: Int?,
        normalize: Boolean,
        size: Long
    ) : this(query, bitrate, format) {
        this.url = url
        this.start = start
        this.end = end
        this.normalize = normalize
        this.size = size
    }

    val filenameWithExt: String
        get() = "$filename.$format"

    companion object {
        // App code (EA50) - "Class" (ClA5) - Class ID (D010_AD00)
        private const val serialVersionUID = -0x15af3e5a2fef5300L
    }
}