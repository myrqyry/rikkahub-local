package me.rerere.rikkahub.ui.pages.models.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.data.modelregistry.ModelDescriptor
import me.rerere.rikkahub.data.modelregistry.ModelSource

// ponytail: label is derived as a pure string so the composable stays dumb and tests stay trivial.

fun sourceDisplayName(model: ModelDescriptor): String = when (val source = model.source) {
    is ModelSource.Local -> "On device"
    is ModelSource.Cloud -> model.metadata["provider"] ?: source.providerId
}

@Composable
fun SourceBadge(
    model: ModelDescriptor,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = sourceDisplayName(model),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
        )
    }
}
