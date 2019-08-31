package com.yearzero.renebeats.preferences.enums

import java.util.*

enum class ProcessSpeed(val value: String) {

    ULTRAFAST("ultrafast"),
    SUPERFAST("superfast"),
    VERYFAST("veryfast"),
    FASTER("faster"),
    FAST("fast"),
    MEDIUM("medium"),
    SLOW("slow"),
    SLOWER("slower"),
    VERYSLOW("veryslow");

    companion object {
        @JvmStatic
        val Default = MEDIUM

        @JvmStatic
        fun fromValue(value: String?): ProcessSpeed = when(value?.toLowerCase(Locale.ENGLISH)) {
                "ultrafast" -> ULTRAFAST
                "superfast" -> SUPERFAST
                "veryfast" -> VERYFAST
                "faster" -> FASTER
                "fast" -> FAST
                "slow" -> SLOW
                "slower" -> SLOWER
                "veryslow" -> VERYSLOW
                else -> Default
        }
    }
}
