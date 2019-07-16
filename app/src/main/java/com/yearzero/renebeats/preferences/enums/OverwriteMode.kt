package com.yearzero.renebeats.preferences.enums


import java.io.Serializable

enum class OverwriteMode(val value: String) : Serializable {
    PROMPT("prompt"),
    APPEND("append"),
    OVERWRITE("overwrite");

    companion object {

        @JvmStatic
        val Default = PROMPT

        @JvmStatic
        fun fromValue(value: String?): OverwriteMode = when (value?.toLowerCase()) {
            "prompt" -> PROMPT
            "append" -> APPEND
            "overwrite" -> OVERWRITE
            else -> Default
        }
    }
}

