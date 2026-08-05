package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import me.rerere.rikkahub.R

@Composable
fun ArtifactImportReviewDialog(
    kind: String,
    source: String,
    details: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.setting_home_import_review_title, kind)) },
        text = { Text(stringResource(R.string.setting_home_import_review_body, source, details)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.setting_home_import_review_install))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
