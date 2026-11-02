package app.rawapp.camera

import kotlin.math.roundToInt

/** "0,5×" for wide lenses, whole numbers ("1×", "2×") otherwise. */
fun formatZoom(zoom: Float): String =
    if (zoom < 0.95f) {
        "0," + (zoom * 10).roundToInt().coerceIn(1, 9) + "×"
    } else {
        "${zoom.roundToInt()}×"
    }
