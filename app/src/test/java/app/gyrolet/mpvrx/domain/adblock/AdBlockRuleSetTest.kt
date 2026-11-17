/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.gyrolet.mpvrx.domain.adblock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdBlockRuleSetTest {
  @Test
  fun `blocks exact domain match`() {
    val rules = AdBlockRuleSet.parse(sequenceOf("||ads.example.com^"))
    assertTrue(rules.shouldBlock("http://ads.example.com/x.js", null))
  }

  @Test
  fun `blocks subdomain of a rule domain`() {
    val rules = AdBlockRuleSet.parse(sequenceOf("||example.com^"))
    assertTrue(rules.shouldBlock("https://ads.example.com/x.js", null))
  }

  @Test
  fun `does not block unrelated host containing the domain as substring`() {
    val rules = AdBlockRuleSet.parse(sequenceOf("||ads.example.com^"))
    assertFalse(rules.shouldBlock("http://notads.example.com/x.js", null))
    assertFalse(rules.shouldBlock("http://example.com/x.js", null))
  }

  @Test
  fun `ignores comment and blank lines`() {
    val rules = AdBlockRuleSet.parse(sequenceOf("! a comment", "", "||ads.example.com^"))
    assertTrue(rules.shouldBlock("http://ads.example.com/x.js", null))
  }

  @Test
  fun `ignores exception rules without crashing`() {
    val rules = AdBlockRuleSet.parse(sequenceOf("@@||example.com^", "||ads.example.com^"))
    assertTrue(rules.shouldBlock("http://ads.example.com/x.js", null))
  }

  @Test
  fun `malformed request url is not blocked`() {
    val rules = AdBlockRuleSet.parse(sequenceOf("||ads.example.com^"))
    assertFalse(rules.shouldBlock("not a url", null))
  }
}
