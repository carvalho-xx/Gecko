package io.github.carvalhoxx.gecko.domain.model

data class GameItem(
    val id: String,
    val steamAppId: Long? = null,
    val psnTitleId: String? = null,
    val title: String,
    val releaseDateMs: Long? = null,
    val status: GameStatus,
    val licenseType: LicenseType,
    val isDayOne: Boolean = false,
    val hltbMainStoryMs: Long = 0L,
    val hltbMainSidesMs: Long = 0L,
    val hltbCompletionistMs: Long = 0L,
    val selectedPlayStyle: PlayStyle = PlayStyle.NORMAL,
    val platformReportedTimeMs: Long = 0L,
    val localLoggedTimeMs: Long = 0L,
    val userRating: Float? = null,
    val costPaid: Float? = null,
    val coverUrl: String? = null,
    val backdropUrl: String? = null,
    val synopsis: String? = null,
    val developer: String? = null,
    val publisher: String? = null,
    val genres: List<String> = emptyList(),
    val subscriptionExpiryMs: Long? = null,
    val statusUpdatedAtMs: Long? = null,
    val releasePrecision: ReleasePrecision? = null,
    val retroAchievementsId: Long? = null
) {
    /**
     * Delta Baseline consolidado exclusivamente em memória (RF07).
     */
    val totalConsolidatedTimeMs: Long
        get() = platformReportedTimeMs + localLoggedTimeMs

    fun targetHltbTimeMs(style: PlayStyle = selectedPlayStyle): Long = when (style) {
        PlayStyle.RUSH -> hltbMainStoryMs
        PlayStyle.NORMAL -> hltbMainSidesMs
        PlayStyle.PLATINUM -> hltbCompletionistMs
    }
}
