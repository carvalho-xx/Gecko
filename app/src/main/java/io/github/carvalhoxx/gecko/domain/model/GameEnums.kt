package io.github.carvalhoxx.gecko.domain.model

enum class GameStatus {
    WISHLIST,
    BACKLOG,
    PLAYING,
    COMPLETED,
    PAUSED,
    ABANDONED;

    companion object {
        fun fromStorageValue(raw: String): GameStatus = when (raw.trim().uppercase()) {
            "WISHLIST", "QUERO COMPRAR" -> WISHLIST
            "BACKLOG", "BACKLOG / POSSUO" -> BACKLOG
            "PLAYING", "JOGANDO ATUALMENTE", "JOGANDO" -> PLAYING
            "COMPLETED", "ZERADO" -> COMPLETED
            "PAUSED", "PAUSADO" -> PAUSED
            "ABANDONED", "ABANDONADO" -> ABANDONED
            else -> BACKLOG
        }
    }
}

enum class LicenseType {
    OWNED,
    SUBSCRIPTION,
    WISHLIST;

    companion object {
        fun fromStorageValue(raw: String): LicenseType = when (raw.trim().uppercase()) {
            "OWNED", "POSSUO" -> OWNED
            "SUBSCRIPTION", "ASSINATURA TEMPORÁRIA", "ASSINATURA TEMPORARIA", "SAAS" -> SUBSCRIPTION
            "WISHLIST", "QUERO COMPRAR" -> WISHLIST
            else -> OWNED
        }
    }
}

enum class PlayStyle {
    RUSH,
    NORMAL,
    PLATINUM;

    companion object {
        fun fromStorageValue(raw: String): PlayStyle = when (raw.trim().uppercase()) {
            "RUSH", "RUSHAR" -> RUSH
            "NORMAL" -> NORMAL
            "PLATINUM", "PLATINA" -> PLATINUM
            else -> NORMAL
        }
    }
}

enum class ReleasePrecision {
    EXACT,
    MONTH,
    QUARTER,
    YEAR,
    TBA;

    companion object {
        fun fromStorageValue(raw: String?): ReleasePrecision? = raw
            ?.trim()
            ?.uppercase()
            ?.let { normalized -> entries.firstOrNull { it.name == normalized } }
    }
}
