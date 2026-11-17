/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package app.gyrolet.mpvrx.domain.adblock

import java.net.URI

/**
 * A minimal EasyList-compatible rule engine, limited to the `||domain^`
 * network-rule subset. Cosmetic rules, regex rules and `@@` exception
 * rules are intentionally not implemented in Phase 1 and are skipped
 * during [parse] rather than causing an error, so a full EasyList/ABPVN
 * snapshot can be dropped in later, in the same per-line format, without a
 * parse failure or a code change.
 *
 * Matching is host-exact or subdomain-of only — never a substring match —
 * so a rule for "ads.example.com" can never block "example.com" itself or
 * an unrelated "notads.example.com".
 */
class AdBlockRuleSet private constructor(
  private val blockedDomains: Set<String>,
) {
  /** [pageUrl] is accepted for future per-page allow-listing; unused for now. */
  fun shouldBlock(requestUrl: String, pageUrl: String?): Boolean {
    val host = runCatching { URI(requestUrl).host }.getOrNull()?.lowercase() ?: return false
    return blockedDomains.any { domain -> host == domain || host.endsWith(".$domain") }
  }

  companion object {
    private val DOMAIN_RULE = Regex("^\\|\\|([a-zA-Z0-9.-]+)\\^$")

    fun parse(lines: Sequence<String>): AdBlockRuleSet {
      val domains =
        lines
          .map { it.trim() }
          .filter { it.isNotEmpty() && !it.startsWith("!") && !it.startsWith("@@") }
          .mapNotNull { DOMAIN_RULE.find(it)?.groupValues?.get(1)?.lowercase() }
          .toSet()
      return AdBlockRuleSet(domains)
    }
  }
}
