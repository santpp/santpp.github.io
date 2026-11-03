package app.rawapp.gallery

import app.rawapp.develop.LookParams
import org.junit.Assert.assertEquals
import org.junit.Test

class SidecarCodecTest {

    @Test
    fun roundTripsAllAdjustments() {
        val params = LookParams(exposureEv = -1.25f, temperatureMired = 30f, contrast = 1.2f, saturation = 0.8f, grain = 0.1f, halation = 0f)
        assertEquals(params, SidecarCodec.decode(SidecarCodec.encode(params)))
    }

    @Test
    fun missingKeysUseDefaults() {
        assertEquals(LookParams(exposureEv = 1f), SidecarCodec.decode("""{"exposureEv": 1}"""))
    }

    @Test
    fun garbageYieldsDefaults() {
        assertEquals(LookParams(), SidecarCodec.decode("not json"))
    }
}
