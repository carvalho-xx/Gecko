package io.github.carvalhoxx.gecko.domain.model

/**
 * Regra de negócio transversal RN-BKL-01 (Backlog Visível vs. Backlog Elegível).
 *
 * - [VISIBLE]: usado exclusivamente para exibição/contagem na LibraryScreen e busca local.
 * - [ELIGIBLE]: fonte exclusiva para sugestões e planejamento preditivo (RF15, RF16, SmartScheduler, widgets).
 */
object BacklogPolicy {
    val VISIBLE: Set<GameStatus> = setOf(
        GameStatus.BACKLOG,
        GameStatus.PAUSED,
        GameStatus.ABANDONED
    )

    val ELIGIBLE: Set<GameStatus> = setOf(
        GameStatus.BACKLOG
    )

    val ELIGIBLE_LICENSES: Set<LicenseType> = setOf(
        LicenseType.OWNED,
        LicenseType.SUBSCRIPTION
    )

    fun isVisibleInBacklog(status: GameStatus): Boolean = status in VISIBLE

    fun isEligibleForSuggestions(status: GameStatus, licenseType: LicenseType): Boolean =
        status in ELIGIBLE && licenseType in ELIGIBLE_LICENSES
}
