/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package app.gyrolet.mpvrx.database.repository

import app.gyrolet.mpvrx.database.dao.BrowserHistoryDao
import app.gyrolet.mpvrx.database.entities.BrowserHistoryEntity
import app.gyrolet.mpvrx.domain.webbrowser.repository.BrowserHistoryRepository
import kotlinx.coroutines.flow.Flow

class BrowserHistoryRepositoryImpl(
  private val dao: BrowserHistoryDao,
) : BrowserHistoryRepository {
  override suspend fun recordVisit(url: String, title: String?) {
    dao.insert(BrowserHistoryEntity(url = url, title = title, visitedAt = System.currentTimeMillis()))
  }

  override fun observeHistory(limit: Int): Flow<List<BrowserHistoryEntity>> = dao.observeHistory(limit)

  override suspend fun clearAll() = dao.clearAll()
}
