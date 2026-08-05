package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.R
import me.rerere.rikkahub.skills.imports.ImportCandidate

@Composable
fun ArtifactImportReviewDialog(
    candidate: ImportCandidate,
    details: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(
                R.string.setting_home_import_review_title,
                candidate.kind.name.lowercase(),
            ))
        },
        text = {
            Column {
                Text(candidate.name)
                if (candidate.description.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(candidate.description)
                }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(
                    R.string.setting_home_import_review_body,
                    candidate.provenance.source,
                    details,
                ))
            }
        },
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
