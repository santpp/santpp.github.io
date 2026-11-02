package app.rawapp.camera

/**
 * Clockwise rotation, in degrees, that makes a captured frame upright given
 * the sensor mounting and the physical device rotation.
 */
fun captureRotationDegrees(sensorOrientation: Int, deviceRotation: Int, frontFacing: Boolean): Int {
    val device = deviceRotation.mod(360)
    return if (frontFacing) (sensorOrientation - device).mod(360) else (sensorOrientation + device) % 360
}

/** Snaps an OrientationEventListener angle (0..359) to the nearest multiple of 90. */
fun snapToRightAngle(angle: Int): Int = ((angle + 45) / 90 * 90) % 360
