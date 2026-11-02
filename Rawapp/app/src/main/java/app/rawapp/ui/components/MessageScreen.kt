package app.rawapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.rawapp.ui.theme.RawappColors

/** Full-screen state: title, explanation and an optional action. */
@Composable
fun MessageScreen(
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = RawappColors.TextPrimary, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyLarge, color = RawappColors.TextSecondary, textAlign = TextAlign.Center)
        if (actionLabel != null) {
            GlassButton(actionLabel, onAction, Modifier.padding(top = 16.dp))
        }
    }
}
