package app.rawapp.develop.math

import app.rawapp.develop.FilmLook
import app.rawapp.develop.LookParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FilmLutTest {
    private val look = FilmLook.color(LookParams())

    @Test
    fun lutHasExpectedSizeAndRange() {
        val lut = bakeFilmLut(17, look)
        assertEquals(17 * 17 * 17 * 3, lut.size)
        assertTrue(lut.all { it in 0f..1f })
    }

    @Test
    fun midGreyStaysCloseToNeutral() {
        val out = gradeColor(Vec3(0.18, 0.18, 0.18), look)
        assertEquals(0.18, luminance709(out), 0.01)
        assertTrue(out.max() - out.min() < 0.01)
    }

    @Test
    fun highlightsAreDesaturated() {
        val input = Vec3(1.0, 0.75, 0.6)
        val out = gradeColor(input, look)
        val inSat = (input.max() - input.min()) / input.max()
        val outSat = (out.max() - out.min()) / out.max()
        assertTrue(outSat < inSat)
    }

    @Test
    fun shadowsLeanTeal() {
        val out = gradeColor(Vec3(0.02, 0.02, 0.02), look)
        assertTrue(out.z > out.x)
        assertTrue(out.y > out.x)
    }

    @Test
    fun paleBlueSkyTurnsCream() {
        val out = gradeColor(Vec3(srgbDecode(0.81), srgbDecode(0.89), srgbDecode(0.94)), look)
        assertTrue(out.x >= out.z)
    }

    @Test
    fun highlightsLeanCream() {
        val out = gradeColor(Vec3(0.8, 0.8, 0.8), look)
        assertTrue(out.x > out.y)
        assertTrue(out.y > out.z)
    }

    @Test
    fun saturationParameterScalesChroma() {
        val input = Vec3(0.4, 0.2, 0.1)
        val muted = gradeColor(input, FilmLook.color(LookParams(saturation = 0.5f)))
        val normal = gradeColor(input, look)
        assertTrue(muted.max() - muted.min() < normal.max() - normal.min())
    }
}
