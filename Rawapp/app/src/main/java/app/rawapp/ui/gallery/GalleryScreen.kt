package app.rawapp.ui.gallery

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.rawapp.gallery.Photo
import app.rawapp.ui.components.GlassIconButton
import app.rawapp.ui.components.MessageScreen
import app.rawapp.ui.theme.RawappColors

@Composable
fun GalleryScreen(viewModel: GalleryViewModel, onBack: () -> Unit, onOpen: (Photo) -> Unit) {
    val state by viewModel.gallery.collectAsStateWithLifecycle()
    val thumbnails by viewModel.thumbnails.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refresh() }

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Volver a la cámara", onBack)
            Text("Galería", style = MaterialTheme.typography.titleLarge, color = RawappColors.TextPrimary)
        }
        when (val s = state) {
            GalleryState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = RawappColors.Accent, strokeWidth = 2.dp)
            }
            GalleryState.Empty -> MessageScreen(
                title = "Sin fotos todavía",
                body = "Las fotos que hagas con Rawapp aparecerán aquí, reveladas con el look.",
                actionLabel = "Ir a la cámara",
                onAction = onBack,
            )
            is GalleryState.Error -> MessageScreen("No se pudo leer la galería", s.message, "Reintentar", viewModel::refresh)
            is GalleryState.Loaded -> LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(s.photos, key = Photo::id) { photo -> Thumbnail(photo, thumbnails[photo.id], onOpen) }
            }
        }
    }
}

@Composable
private fun Thumbnail(photo: Photo, bitmap: android.graphics.Bitmap?, onOpen: (Photo) -> Unit) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(RawappColors.GlassFill)
            .clickable(role = Role.Image, onClickLabel = "Abrir ${photo.displayName}") { onOpen(photo) },
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap == null) {
            CircularProgressIndicator(color = RawappColors.TextSecondary, strokeWidth = 1.5.dp, modifier = Modifier.padding(36.dp))
        } else {
            val image = remember(bitmap) { bitmap.asImageBitmap() }
            Image(image, contentDescription = photo.displayName, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}
