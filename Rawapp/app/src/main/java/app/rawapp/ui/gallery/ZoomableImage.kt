package app.rawapp.ui.gallery

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale

private const val MAX_ZOOM = 6f

/** Pinch to zoom, drag to pan, double tap to reset. */
@Composable
fun ZoomableImage(bitmap: Bitmap, modifier: Modifier = Modifier) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val scale = remember { mutableFloatStateOf(1f) }
    val offset = remember { mutableStateOf(Offset.Zero) }
    Image(
        bitmap = image,
        contentDescription = "Foto",
        contentScale = ContentScale.Fit,
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    scale.floatValue = 1f
                    offset.value = Offset.Zero
                })
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale.floatValue * zoom).coerceIn(1f, MAX_ZOOM)
                    val limitX = size.width * (newScale - 1f) / 2f
                    val limitY = size.height * (newScale - 1f) / 2f
                    val moved = offset.value + pan * newScale
                    scale.floatValue = newScale
                    offset.value = Offset(moved.x.coerceIn(-limitX, limitX), moved.y.coerceIn(-limitY, limitY))
                }
            }
            .graphicsLayer {
                scaleX = scale.floatValue
                scaleY = scale.floatValue
                translationX = offset.value.x
                translationY = offset.value.y
            },
    )
}
