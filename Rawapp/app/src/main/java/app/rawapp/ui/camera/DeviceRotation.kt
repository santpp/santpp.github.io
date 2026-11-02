package app.rawapp.ui.camera

import android.view.OrientationEventListener
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import app.rawapp.camera.snapToRightAngle

/** Physical device rotation (0, 90, 180, 270) while the UI stays locked to portrait. */
@Composable
fun rememberDeviceRotation(): State<Int> {
    val context = LocalContext.current
    val rotation = remember { mutableIntStateOf(0) }
    DisposableEffect(context) {
        val listener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(angle: Int) {
                if (angle != ORIENTATION_UNKNOWN) rotation.intValue = snapToRightAngle(angle)
            }
        }
        listener.enable()
        onDispose { listener.disable() }
    }
    return rotation
}
