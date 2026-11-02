package app.rawapp.develop.math

import org.junit.Assert.assertEquals
import org.junit.Test

class Mat3Test {

    @Test
    fun inverseTimesMatrixIsIdentity() {
        val m = PIXEL_MAIN.colorMatrix1
        val product = m * m.inverse()
        for (r in 0..2) for (c in 0..2) assertEquals(if (r == c) 1.0 else 0.0, product[r, c], 1e-9)
    }

    @Test
    fun glFloatsAreColumnMajor() {
        val m = Mat3.of(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0)
        assertEquals(listOf(1f, 4f, 7f, 2f, 5f, 8f, 3f, 6f, 9f), m.toGlFloats().toList())
    }

    @Test(expected = IllegalArgumentException::class)
    fun singularMatrixIsRejected() {
        Mat3.of(1.0, 2.0, 3.0, 2.0, 4.0, 6.0, 0.0, 0.0, 1.0).inverse()
    }
}
