package app.yuxino.mimi.android

/** Native visual-line allocation, using measured glyph heights rather than character counts. */
internal fun captionLaneBudget(height: Int, source: List<Int>, translation: List<Int>): Pair<Int, Int> {
    fun fit(heights: List<Int>, space: Int): Int = if (heights.isEmpty()) 0 else
        heights.indexOfLast { it <= space }.let { if (it < 0) 1 else it + 1 }
    if (source.isEmpty()) return 0 to fit(translation, height)
    if (translation.isEmpty()) return fit(source, height) to 0
    // Start with equal space. Short text yields its unused space to the other lane.
    var original = fit(source, height / 2)
    var translated = fit(translation, height - source[original - 1])
    original = fit(source, height - translation[translated - 1])
    translated = fit(translation, height - source[original - 1])
    return original to translated
}
