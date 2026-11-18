/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package app.gyrolet.mpvrx.domain.webbrowser.repository

import app.gyrolet.mpvrx.database.entities.BrowserBookmarkEntity
import kotlinx.coroutines.flow.Flow

interface BrowserBookmarkRepository {
  fun isBookmarked(url: String): Flow<Boolean>

  suspend fun toggle(url: String, title: String?)

  fun observeBookmarks(): Flow<List<BrowserBookmarkEntity>>

  suspend fun remove(url: String)
}
