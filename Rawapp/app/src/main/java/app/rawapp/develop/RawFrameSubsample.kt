package app.rawapp.develop

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * A smaller Bayer mosaic that keeps one 2×2 tile out of every [factor]×[factor],
 * for fast previews. Colour data and calibration are shared with the original.
 */
fun RawFrame.subsampled(factor: Int): RawFrame {
    if (factor <= 1) return this
    val tilesX = width / 2 / factor
    val tilesY = height / 2 / factor
    val outWidth = tilesX * 2
    val outHeight = tilesY * 2
    val src = pixels.duplicate().order(ByteOrder.nativeOrder()).asShortBuffer()
    val out = ByteBuffer.allocateDirect(outWidth * outHeight * 2).order(ByteOrder.nativeOrder())
    val dst = out.asShortBuffer()
    for (ty in 0 until tilesY) for (dy in 0..1) {
        val srcRow = (ty * factor * 2 + dy) * width
        for (tx in 0 until tilesX) {
            val srcX = tx * factor * 2
            dst.put(src.get(srcRow + srcX))
            dst.put(src.get(srcRow + srcX + 1))
        }
    }
    return RawFrame(
        width = outWidth,
        height = outHeight,
        pixels = out,
        cfa = cfa,
        blackLevels = blackLevels,
        whiteLevel = whiteLevel,
        asShotNeutral = asShotNeutral,
        sensor = sensor,
        lensShading = lensShading,
        rotationDegrees = rotationDegrees,
    )
}
