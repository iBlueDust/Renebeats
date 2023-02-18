package com.yearzero.renebeats.download

import com.google.api.services.youtube.model.SearchResult
import com.google.api.services.youtube.model.ThumbnailDetails
import com.yearzero.renebeats.preferences.Preferences.artist_first
import lombok.AccessLevel
import lombok.Getter
import lombok.Setter
import org.apache.commons.text.StringEscapeUtils
import java.io.Serializable
import java.util.*
import javax.annotation.ParametersAreNullableByDefault

@ParametersAreNullableByDefault
open class Query : Serializable {
    public var youtubeID: String? = null
    public var title = ""
    public var album = ""
    public var artist = ""
    public var year = 0
    public var track = 0
    public var genres = ""

    //region Thumbnail Getters
    public var thumbMax: String? = null
    public var thumbHigh: String? = null
    public var thumbMedium: String? = null
    public var thumbDefault: String? = null
    public var thumbStandard: String? = null

    internal constructor() {}

    internal constructor(id: String?) {
        youtubeID = id
    }

    internal constructor(
        id: String?,
        title: String,
        artist: String,
        album: String,
        year: Int,
        track: Int,
        genres: String
    ) {
        youtubeID = id
        this.title = title
        this.artist = artist
        this.album = album
        this.year = year
        this.track = track
        this.genres = genres
    }

    internal constructor(
        id: String?,
        title: String,
        artist: String,
        album: String,
        year: Int,
        track: Int,
        genres: String,
        thumbMax: String?,
        thumbHigh: String?,
        thumbMedium: String?,
        thumbDefault: String?,
        thumbStandard: String?
    ) : this(id, title, artist, album, year, track, genres) {
        this.thumbMax = thumbMax
        this.thumbHigh = thumbHigh
        this.thumbMedium = thumbMedium
        this.thumbDefault = thumbDefault
        this.thumbStandard = thumbStandard
    }

    constructor(
        id: String?,
        title: String,
        artist: String,
        album: String,
        year: Int,
        track: Int,
        genres: String,
        thumbnail: ThumbnailDetails
    ) : this(id, title, artist, album, year, track, genres) {
        if (thumbnail.maxres != null) thumbMax = thumbnail.maxres.url
        if (thumbnail.high != null) thumbHigh = thumbnail.high.url
        if (thumbnail.medium != null) thumbMedium = thumbnail.medium.url
        if (thumbnail.default != null) thumbDefault = thumbnail.default.url
        if (thumbnail.standard != null) thumbStandard = thumbnail.standard.url
    }

    // Sanitize invalid characters before returning
    val filename: String?
        get() {
            val result: String = if (artist.isEmpty()) {
                if (title.isEmpty()) return null else title.trim { it <= ' ' }
            } else {
                if (title.isEmpty()) artist.trim { it <= ' ' } else if (artist_first) artist.trim { it <= ' ' } + " - " + title.trim { it <= ' ' } else title.trim { it <= ' ' } + " - " + artist.trim { it <= ' ' }
            }
            // Sanitize invalid characters before returning
            return result.replace("\\||\\\\|\\?|\\*|<|\"|:|>|\\+|\\[|]|/'".toRegex(), "_")
        }

    //In Java use overflow/no break switch case
    fun getThumbnail(quality: ThumbnailQuality): String? {
        return when (quality) {
            ThumbnailQuality.MaxRes -> {
                if (thumbMax != null) return thumbMax
                if (thumbHigh != null) return thumbHigh
                if (thumbMedium != null) return thumbMedium
                if (thumbStandard != null) thumbStandard else thumbDefault
            }
            ThumbnailQuality.High -> {
                if (thumbHigh != null) return thumbHigh
                if (thumbMedium != null) return thumbMedium
                if (thumbStandard != null) thumbStandard else thumbDefault
            }
            ThumbnailQuality.Medium -> {
                if (thumbMedium != null) return thumbMedium
                if (thumbStandard != null) thumbStandard else thumbDefault
            }
            ThumbnailQuality.Standard -> {
                if (thumbStandard != null) thumbStandard else thumbDefault
            }
            else -> thumbDefault
        }
    }

    enum class ThumbnailQuality(private val value: String) {
        MaxRes("maxres"), High("high"), Medium("medium"), Default("default"), Standard("standard");

        fun toValue(): String {
            return value
        }

        companion object {
            fun fromValue(value: String): ThumbnailQuality {
                return when (value.toLowerCase(Locale.ROOT)) {
                    "maxres" -> MaxRes
                    "high" -> High
                    "medium" -> Medium
                    "standard" -> Standard
                    else -> Default
                }
            }
        }
    }

    companion object {
        // App code (EA50) - "Class" (ClA5) - Class ID (0E10_llE1)
        private const val serialVersionUID = -0x15af3e5af1efee1fL

		@JvmStatic
		fun castListXML(list: List<SearchResult>): List<Query> {
            val result = ArrayList<Query>()
            for (r in list) {
                val s = r.snippet
                val cal = Calendar.getInstance()
                cal.time = Date(s.publishedAt.value)
                result.add(
                    Query(
                        r.id.videoId,
                        StringEscapeUtils.unescapeXml(s.title),
                        StringEscapeUtils.unescapeXml(s.channelTitle), "",
                        cal[Calendar.YEAR],
                        0,
                        "",
                        s.thumbnails
                    )
                )
            }
            return result
        }
    }
}