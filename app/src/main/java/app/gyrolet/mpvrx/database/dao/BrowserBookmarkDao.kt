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
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.gyrolet.mpvrx.database.entities.BrowserBookmarkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BrowserBookmarkDao {
  @Query("SELECT EXISTS(SELECT 1 FROM BrowserBookmarkEntity WHERE url = :url)")
  fun observeIsBookmarked(url: String): Flow<Boolean>

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insert(entry: BrowserBookmarkEntity)

  @Query("DELETE FROM BrowserBookmarkEntity WHERE url = :url")
  suspend fun deleteByUrl(url: String)

  @Query("SELECT * FROM BrowserBookmarkEntity ORDER BY createdAt DESC")
  fun observeAll(): Flow<List<BrowserBookmarkEntity>>
}
