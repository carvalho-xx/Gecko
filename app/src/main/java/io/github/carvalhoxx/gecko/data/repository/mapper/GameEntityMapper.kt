package io.github.carvalhoxx.gecko.data.repository.mapper

import io.github.carvalhoxx.gecko.data.local.Game
import io.github.carvalhoxx.gecko.domain.model.GameItem
import io.github.carvalhoxx.gecko.domain.model.GameStatus
import io.github.carvalhoxx.gecko.domain.model.LicenseType
import io.github.carvalhoxx.gecko.domain.model.PlayStyle
import io.github.carvalhoxx.gecko.domain.model.ReleasePrecision

fun Game.toDomain(): GameItem = GameItem(
    id = id,
    steamAppId = steam_app_id,
    psnTitleId = psn_title_id,
    title = title,
    releaseDateMs = release_date_ms,
    status = GameStatus.fromStorageValue(status),
    licenseType = LicenseType.fromStorageValue(license_type),
    isDayOne = is_day_one,
    hltbMainStoryMs = hltb_main_story_ms,
    hltbMainSidesMs = hltb_main_sides_ms,
    hltbCompletionistMs = hltb_completionist_ms,
    selectedPlayStyle = PlayStyle.fromStorageValue(selected_play_style),
    platformReportedTimeMs = platform_reported_time_ms,
    localLoggedTimeMs = local_logged_time_ms,
    userRating = user_rating,
    costPaid = cost_paid,
    coverUrl = cover_url,
    backdropUrl = backdrop_url,
    synopsis = synopsis,
    developer = developer,
    publisher = publisher,
    genres = genres_csv
        ?.split(",")
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        .orEmpty(),
    subscriptionExpiryMs = subscription_expiry_ms,
    statusUpdatedAtMs = status_updated_at_ms,
    releasePrecision = ReleasePrecision.fromStorageValue(release_precision),
    retroAchievementsId = retro_achievements_id
)

fun GameItem.toEntity(): Game = Game(
    id = id,
    steam_app_id = steamAppId,
    psn_title_id = psnTitleId,
    title = title,
    release_date_ms = releaseDateMs,
    status = status.name,
    license_type = licenseType.name,
    is_day_one = isDayOne,
    hltb_main_story_ms = hltbMainStoryMs,
    hltb_main_sides_ms = hltbMainSidesMs,
    hltb_completionist_ms = hltbCompletionistMs,
    selected_play_style = selectedPlayStyle.name,
    platform_reported_time_ms = platformReportedTimeMs,
    local_logged_time_ms = localLoggedTimeMs,
    user_rating = userRating,
    cost_paid = costPaid,
    cover_url = coverUrl,
    backdrop_url = backdropUrl,
    synopsis = synopsis,
    developer = developer,
    publisher = publisher,
    genres_csv = genres
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .takeIf { it.isNotEmpty() }
        ?.joinToString(","),
    subscription_expiry_ms = subscriptionExpiryMs,
    status_updated_at_ms = statusUpdatedAtMs,
    release_precision = releasePrecision?.name,
    retro_achievements_id = retroAchievementsId
)
