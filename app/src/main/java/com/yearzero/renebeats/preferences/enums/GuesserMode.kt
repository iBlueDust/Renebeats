package com.yearzero.renebeats.preferences.enums

import java.util.*

enum class GuesserMode constructor(val value: String) {
    OFF("off"),
    TITLE_ONLY("title"),
    TITLE_UPLOADER("simple"),
    PREDICT("predict");


    companion object {

        @JvmStatic
        val Default = PREDICT

        @JvmStatic
        fun fromValue(value: String?): GuesserMode = when (value?.toLowerCase(Locale.ENGLISH)) {
            "off" -> OFF
            "title" -> TITLE_ONLY
            "simple" -> TITLE_UPLOADER
            "predict" -> PREDICT
            else -> Default
        }
    }
}
