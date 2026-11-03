package app.rawapp.develop

import app.rawapp.develop.math.Mat3
import app.rawapp.develop.math.SensorColor
import app.rawapp.develop.math.Vec3
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class RawFrameSubsampleTest {

    private fun frame(width: Int, height: Int): RawFrame {
        val buf = ByteBuffer.allocateDirect(width * height * 2).order(ByteOrder.nativeOrder())
        val shorts = buf.asShortBuffer()
        for (i in 0 until width * height) shorts.put(i.toShort())
        val m = Mat3.IDENTITY
        return RawFrame(
            width, height, buf, CfaPattern.RGGB, FloatArray(4), 1023f, Vec3(1.0, 1.0, 1.0),
            SensorColor(m, m, m, m, m, m, 2856.0, 6504.0), null, 0,
        )
    }

    @Test
    fun keepsWholeBayerTiles() {
        val src = frame(8, 4)
        val small = src.subsampled(2)
        assertEquals(4, small.width)
        assertEquals(2, small.height)
        val px = small.pixels.duplicate().order(ByteOrder.nativeOrder()).asShortBuffer()
        // Row 0 keeps tiles at x = 0..1 and 4..5; row 1 is the second row of those tiles.
        assertEquals(listOf(0, 1, 4, 5, 8, 9, 12, 13), (0 until 8).map { px.get(it).toInt() })
        assertEquals(CfaPattern.RGGB, small.cfa)
    }

    @Test
    fun factorOneReturnsSameFrame() {
        val src = frame(4, 4)
        assertEquals(src, src.subsampled(1))
    }
}
