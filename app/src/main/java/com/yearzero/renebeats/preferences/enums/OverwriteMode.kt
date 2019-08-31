package com.yearzero.renebeats.preferences.enums


import java.io.Serializable
import java.util.*

enum class OverwriteMode(val value: String) : Serializable {
    PROMPT("prompt"),
    APPEND("append"),
    OVERWRITE("overwrite");

    companion object {

        @JvmStatic
        val Default = PROMPT

        @JvmStatic
        fun fromValue(value: String?): OverwriteMode = when (value?.toLowerCase(Locale.ENGLISH)) {
            "prompt" -> PROMPT
            "append" -> APPEND
            "overwrite" -> OVERWRITE
            else -> Default
        }
    }
}

