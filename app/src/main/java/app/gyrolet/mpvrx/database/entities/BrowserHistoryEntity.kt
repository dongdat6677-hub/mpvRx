/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package app.gyrolet.mpvrx.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(indices = [Index(value = ["visitedAt"])])
data class BrowserHistoryEntity(
  @PrimaryKey(autoGenerate = true) val id: Int = 0,
  val url: String,
  val title: String?,
  val visitedAt: Long,
)
