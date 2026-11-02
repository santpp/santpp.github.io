package app.rawapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.rawapp.ui.theme.RawappColors

/**
 * Translucent "light glass" surface: a pale fill, a soft top sheen and a
 * bright-to-dim rim. Camera2 preview cannot be backdrop-blurred from Compose,
 * so the glass effect relies on translucency and edge light only.
 */
fun Modifier.glass(shape: Shape = RoundedCornerShape(24.dp)): Modifier = this
    .clip(shape)
    .background(RawappColors.GlassFill, shape)
    .background(Brush.verticalGradient(listOf(RawappColors.GlassSheen, Color.Transparent)), shape)
    .border(
        width = 1.dp,
        brush = Brush.linearGradient(listOf(RawappColors.GlassEdgeBright, RawappColors.GlassEdgeDim)),
        shape = shape,
    )

@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(52.dp)
            .glass(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = RawappColors.TextPrimary)
    }
}

@Composable
fun GlassPill(text: String, modifier: Modifier = Modifier, color: Color = RawappColors.TextPrimary) {
    Box(modifier = modifier.glass(RoundedCornerShape(50))) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

@Composable
fun GlassButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .glass(RoundedCornerShape(50))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 28.dp, vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = RawappColors.Accent)
    }
}
