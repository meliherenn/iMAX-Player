package com.imax.player.core.model

enum class PlayerEngineType {
    EXOPLAYER,
    VLC;

    companion object {
        fun fromStoredValue(value: String?): PlayerEngineType {
            return entries.firstOrNull { engineType ->
                engineType.name.equals(value?.trim(), ignoreCase = true)
            } ?: EXOPLAYER
        }
    }
}
