package app.rawapp.develop

import java.nio.ByteOrder

private const val SAMPLE_STEP = 24

/** Normalised green photosite values on a sparse grid, for exposure metering. */
fun sampleGreen(frame: RawFrame): FloatArray {
    val shorts = frame.pixels.duplicate().order(ByteOrder.nativeOrder()).asShortBuffer()
    val greenX = 1 - frame.cfa.redX
    val samples = ArrayList<Float>()
    for (y in 0 until frame.height step SAMPLE_STEP) {
        val row = y - (y and 1) + frame.cfa.redY
        if (row >= frame.height) continue
        for (x in greenX until frame.width step SAMPLE_STEP) {
            val black = frame.blackLevels[(row and 1) * 2 + (x and 1)]
            val raw = shorts.get(row * frame.width + x).toInt() and 0xFFFF
            samples += ((raw - black) / (frame.whiteLevel - black)).coerceIn(0f, 1f)
        }
    }
    return samples.toFloatArray()
}
