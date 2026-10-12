package app.yuxino.mimi.android

internal const val CAPTION_PANEL_MAX_LANE_LINES = 3

/** Fixed native slots depend on the viewport and typography, never the current words. */
internal fun captionLaneSlots(height: Int, sourceLine: Int, translationLine: Int): Pair<Int, Int> {
    val original = sourceLine.coerceAtLeast(1)
    val translated = translationLine.coerceAtLeast(1)
    val maximumSource = original * CAPTION_PANEL_MAX_LANE_LINES
    val maximumTranslation = translated * CAPTION_PANEL_MAX_LANE_LINES
    val available = height.coerceIn(0, maximumSource + maximumTranslation)
    if (available < original + translated) {
        val source = minOf(original, available / 2)
        return source to available - source
    }
    val minimumTranslation = if (original + translated * 2 <= available) translated * 2 else translated
    var source = (available / 2).coerceIn(original, available - minimumTranslation).coerceAtMost(maximumSource)
    val translation = (available - source).coerceAtMost(maximumTranslation)
    source = (available - translation).coerceAtMost(maximumSource)
    return source to translation
}

/** Fit complete native tail lines within one lane without borrowing its neighbour's slot. */
internal fun captionLineBudget(height: Int, tailHeights: List<Int>): Int = if (tailHeights.isEmpty()) 0 else
    tailHeights.indexOfLast { it <= height }.let { if (it < 0) 1 else it + 1 }

/** Content-sized allocation retained for single-language and manual reading paths. */
internal fun captionLaneBudget(height: Int, source: List<Int>, translation: List<Int>): Pair<Int, Int> {
    if (source.isEmpty()) return 0 to captionLineBudget(height, translation)
    if (translation.isEmpty()) return captionLineBudget(height, source) to 0
    val minimumTranslation = if (translation.size >= 2 && source[0] + translation[1] <= height) 2 else 1
    val originalSpace = height - translation[minimumTranslation - 1]
    var original = captionLineBudget(minOf(height / 2, originalSpace), source)
    var translated = captionLineBudget(height - source[original - 1], translation)
    original = captionLineBudget(minOf(height - translation[translated - 1], originalSpace), source)
    translated = captionLineBudget(height - source[original - 1], translation)
    return original to translated
}
