/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package app.gyrolet.mpvrx.database.repository

import app.gyrolet.mpvrx.database.dao.BrowserBookmarkDao
import app.gyrolet.mpvrx.database.entities.BrowserBookmarkEntity
import app.gyrolet.mpvrx.domain.webbrowser.repository.BrowserBookmarkRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class BrowserBookmarkRepositoryImpl(
  private val dao: BrowserBookmarkDao,
) : BrowserBookmarkRepository {
  override fun isBookmarked(url: String): Flow<Boolean> = dao.observeIsBookmarked(url)

  override suspend fun toggle(url: String, title: String?) {
    val bookmarked = dao.observeIsBookmarked(url).first()
    if (bookmarked) {
      dao.deleteByUrl(url)
    } else {
      dao.insert(BrowserBookmarkEntity(url = url, title = title, createdAt = System.currentTimeMillis()))
    }
  }

  override fun observeBookmarks(): Flow<List<BrowserBookmarkEntity>> = dao.observeAll()

  override suspend fun remove(url: String) = dao.deleteByUrl(url)
}
