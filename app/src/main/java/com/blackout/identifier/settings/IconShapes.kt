package com.blackout.identifier.settings

/** Icon drawings, in 108×108 adaptive-icon units. Generated together with the app drawer icons so both match. */
object IconShapes {
    class Shape(val stroke: Float, val onBackground: Boolean, val path: String)
    val STYLES: Map<String, Pair<Boolean, List<Shape>>> = mapOf(
        "A" to Pair(true, listOf(
            Shape(5f, false, "M54,29L76,37L76,54C76,67 66,76 54,81C42,76 32,67 32,54L32,37Z"),
            Shape(4f, false, "M46,54a8,8 0 1 0 16,0a8,8 0 1 0 -16,0z"),
            Shape(0f, false, "M51,54a3,3 0 1 0 6,0a3,3 0 1 0 -6,0z"),
        )),
        "B" to Pair(false, listOf(
            Shape(0f, false, "M54,29L76,37L76,54C76,67 66,76 54,81C42,76 32,67 32,54L32,37Z"),
            Shape(3.5f, true, "M46,54a8,8 0 1 0 16,0a8,8 0 1 0 -16,0z"),
            Shape(0f, true, "M51,54a3,3 0 1 0 6,0a3,3 0 1 0 -6,0z"),
        )),
        "C" to Pair(true, listOf(
            Shape(2.4f, false, "M22.5,54a31.5,31.5 0 1 0 63,0a31.5,31.5 0 1 0 -63,0z"),
            Shape(3.5f, false, "M54,38.9L67.6,43.8L67.6,54.4C67.6,62.4 61.4,68 54,71.1C46.6,68 40.4,62.4 40.4,54.4L40.4,43.8Z"),
            Shape(0f, false, "M51.2,55a2.8,2.8 0 1 0 5.6,0a2.8,2.8 0 1 0 -5.6,0z"),
        )),
        "D" to Pair(true, listOf(
            Shape(5f, false, "M22,54C34,38 74,38 86,54C74,70 34,70 22,54Z"),
            Shape(4f, false, "M45,54a9,9 0 1 0 18,0a9,9 0 1 0 -18,0z"),
            Shape(0f, false, "M50.5,54a3.5,3.5 0 1 0 7,0a3.5,3.5 0 1 0 -7,0z"),
        )),
    )
    val NAMES = mapOf("A" to "Shield", "B" to "Solid", "C" to "Ring", "D" to "Watcher")
}
