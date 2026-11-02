package app.rawapp.camera

import org.junit.Assert.assertEquals
import org.junit.Test

class CaptureOrientationTest {

    @Test
    fun portraitWithSensorAt90NeedsQuarterTurn() {
        assertEquals(90, captureRotationDegrees(sensorOrientation = 90, deviceRotation = 0, frontFacing = false))
    }

    @Test
    fun landscapeLeftCancelsSensorRotation() {
        assertEquals(0, captureRotationDegrees(sensorOrientation = 90, deviceRotation = 270, frontFacing = false))
    }

    @Test
    fun rotationWrapsPast360() {
        assertEquals(180, captureRotationDegrees(sensorOrientation = 270, deviceRotation = 270, frontFacing = false))
    }

    @Test
    fun negativeDeviceRotationIsNormalised() {
        assertEquals(0, captureRotationDegrees(sensorOrientation = 90, deviceRotation = -90, frontFacing = false))
    }

    @Test
    fun frontCameraRotatesAgainstDevice() {
        assertEquals(180, captureRotationDegrees(sensorOrientation = 270, deviceRotation = 90, frontFacing = true))
        assertEquals(270, captureRotationDegrees(sensorOrientation = 270, deviceRotation = 0, frontFacing = true))
    }

    @Test
    fun anglesSnapToNearestRightAngle() {
        assertEquals(0, snapToRightAngle(10))
        assertEquals(90, snapToRightAngle(100))
        assertEquals(180, snapToRightAngle(224))
        assertEquals(270, snapToRightAngle(250))
        assertEquals(0, snapToRightAngle(350))
    }
}
