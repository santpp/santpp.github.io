package app.rawapp.camera

/**
 * Clockwise rotation, in degrees, that makes a back-camera frame upright
 * given the sensor mounting and the physical device rotation.
 */
fun captureRotationDegrees(sensorOrientation: Int, deviceRotation: Int): Int =
    (sensorOrientation + deviceRotation.mod(360)) % 360

/** Snaps an OrientationEventListener angle (0..359) to the nearest multiple of 90. */
fun snapToRightAngle(angle: Int): Int = ((angle + 45) / 90 * 90) % 360
