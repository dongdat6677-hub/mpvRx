/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package app.gyrolet.mpvrx.domain.webbrowser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamSnifferTest {
  @Test
  fun `detects hls playlist`() {
    val result = StreamSniffer.classify("https://cdn.example.com/video/master.m3u8")
    assertEquals(StreamType.HLS, result?.type)
  }

  @Test
  fun `detects progressive mp4`() {
    val result = StreamSniffer.classify("https://cdn.example.com/video_720p.mp4")
    assertEquals(StreamType.PROGRESSIVE, result?.type)
    assertEquals("720p", result?.qualityLabel)
  }

  @Test
  fun `non stream url returns null`() {
    assertNull(StreamSniffer.classify("https://example.com/page.html"))
  }

  @Test
  fun `tags ad-looking segment`() {
    val result = StreamSniffer.classify("https://cdn.example.com/adbreak/segment1.m3u8")
    assertTrue(result?.isLikelyAd == true)
  }

  @Test
  fun `ordinary stream is not tagged as ad`() {
    val result = StreamSniffer.classify("https://cdn.example.com/video/master.m3u8")
    assertTrue(result?.isLikelyAd == false)
  }

  @Test
  fun `sorts streams closest to 1080p first`() {
    val streams =
      listOf(
        SniffedStream("a", StreamType.PROGRESSIVE, "240p", false),
        SniffedStream("b", StreamType.PROGRESSIVE, "1080p", false),
        SniffedStream("c", StreamType.PROGRESSIVE, null, false),
        SniffedStream("d", StreamType.PROGRESSIVE, "720p", false),
      )
    val sorted = StreamSniffer.sortedByPreferredQuality(streams)
    assertEquals(listOf("b", "d", "a", "c"), sorted.map { it.url })
  }
}
