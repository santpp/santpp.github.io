package app.rawapp.develop.dng

import app.rawapp.develop.CfaPattern
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DngParserTest {

    private val identity = doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0)

    private fun dng(order: ByteOrder, cfa: IntArray = intArrayOf(1, 0, 2, 1), opcodes: ByteArray? = null): ByteArray {
        val b = TestDng(order)
            .longs(256, 4).longs(257, 2).shorts(258, 16).shorts(259, 1).shorts(262, 32803)
            .shorts(274, 6)
            .shorts(33421, 2, 2).bytes(33422, *cfa)
            .rationals(50714, 64.0, 65.0, 66.0, 67.0).longs(50717, 1023)
            .srationals(50721, *identity).srationals(50722, *identity)
            .srationals(50964, *identity).srationals(50965, *identity)
            .rationals(50728, 0.5, 1.0, 0.6)
            .shorts(50778, 17).shorts(50779, 21)
        opcodes?.let { b.undefined(51009, it) }
        return b.build(shortArrayOf(100, 200, 300, 400, 500, 600, 700, 1023))
    }

    @Test
    fun parsesLittleAndBigEndianIdentically() {
        for (order in listOf(ByteOrder.LITTLE_ENDIAN, ByteOrder.BIG_ENDIAN)) {
            val frame = parseDng(dng(order))
            assertEquals(4, frame.width)
            assertEquals(2, frame.height)
            assertEquals(CfaPattern.GRBG, frame.cfa)
            assertArrayEquals(floatArrayOf(64f, 65f, 66f, 67f), frame.blackLevels, 1e-3f)
            assertEquals(1023f, frame.whiteLevel)
            assertEquals(0.5, frame.asShotNeutral.x, 1e-4)
            assertEquals(2856.0, frame.sensor.illuminant1Cct, 0.0)
            assertEquals(6504.0, frame.sensor.illuminant2Cct, 0.0)
            assertEquals(90, frame.rotationDegrees)
            val px = frame.pixels.duplicate().order(ByteOrder.nativeOrder()).asShortBuffer()
            assertEquals(100, px.get(0).toInt())
            assertEquals(1023, px.get(7).toInt())
        }
    }

    @Test
    fun readsBayerPatternsFromCfaTag() {
        assertEquals(CfaPattern.RGGB, parseDng(dng(ByteOrder.LITTLE_ENDIAN, intArrayOf(0, 1, 1, 2))).cfa)
        assertEquals(CfaPattern.BGGR, parseDng(dng(ByteOrder.LITTLE_ENDIAN, intArrayOf(2, 1, 1, 0))).cfa)
    }

    @Test(expected = DngFormatException::class)
    fun rejectsNonTiff() {
        parseDng("hello world!".toByteArray())
    }

    @Test
    fun mergesFourGainMapsIntoLensShading() {
        val shading = parseDng(dng(ByteOrder.LITTLE_ENDIAN, opcodes = gainMaps())).lensShading
        assertNotNull(shading)
        shading!!
        assertEquals(2, shading.columns)
        assertEquals(2, shading.rows)
        // GRBG: red at (1,0) → phase value 2.0; blue at (0,1) → 3.0; greens 1.0 / 4.0.
        assertArrayEquals(floatArrayOf(2f, 1f, 4f, 3f), shading.gains.copyOfRange(0, 4), 0f)
    }

    /** OpcodeList2 with one GainMap per Bayer phase, each constant: (0,0)=1, (1,0)=2, (0,1)=3, (1,1)=4. */
    private fun gainMaps(): ByteArray {
        val phases = listOf(Triple(0, 0, 1f), Triple(0, 1, 2f), Triple(1, 0, 3f), Triple(1, 1, 4f))
        val params = 4 * 8 + 4 * 2 + 8 * 4 + 4 + 4 * 4
        val b = ByteBuffer.allocate(4 + phases.size * (16 + params)).order(ByteOrder.BIG_ENDIAN)
        b.putInt(phases.size)
        for ((top, left, gain) in phases) {
            b.putInt(9).putInt(0x01030000).putInt(0).putInt(params)
            b.putInt(top).putInt(left).putInt(2).putInt(4)
            b.putInt(0).putInt(1).putInt(2).putInt(2)
            b.putInt(2).putInt(2)
            b.putDouble(1.0).putDouble(1.0).putDouble(0.0).putDouble(0.0)
            b.putInt(1)
            repeat(4) { b.putFloat(gain) }
        }
        return b.array()
    }
}
