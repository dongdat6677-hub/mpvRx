/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package app.gyrolet.mpvrx.ui.webbrowser

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.gyrolet.mpvrx.R

@Composable
fun BrowserConfirmNavigationDialog(
  pending: PendingNavigation,
  onAllow: () -> Unit,
  onDeny: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDeny,
    title = { Text(pending.pageTitle?.takeIf { it.isNotBlank() } ?: pending.url) },
    text = { Text(pending.url) },
    confirmButton = {
      TextButton(onClick = onAllow) { Text(stringResource(R.string.ui_web_browser_allow)) }
    },
    dismissButton = {
      TextButton(onClick = onDeny) { Text(stringResource(R.string.ui_web_browser_deny)) }
    },
  )
}
