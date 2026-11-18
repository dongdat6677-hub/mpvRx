/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package app.gyrolet.mpvrx.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import app.gyrolet.mpvrx.database.entities.BrowserHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BrowserHistoryDao {
  @Insert
  suspend fun insert(entry: BrowserHistoryEntity)

  @Query("SELECT * FROM BrowserHistoryEntity ORDER BY visitedAt DESC LIMIT :limit")
  fun observeHistory(limit: Int): Flow<List<BrowserHistoryEntity>>

  @Query("DELETE FROM BrowserHistoryEntity")
  suspend fun clearAll()
}
