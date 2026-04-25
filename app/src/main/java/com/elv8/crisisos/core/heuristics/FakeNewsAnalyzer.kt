package com.elv8.crisisos.core.heuristics

import com.elv8.crisisos.domain.model.Verdict
import com.elv8.crisisos.domain.model.VerificationResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * Deterministic offline heuristic engine for the Fake News Detector.
 *
 * Per spec (Feature 9, "Offline mode"):
 *   - Scans for extreme emotional language designed to cause panic
 *   - Logical inconsistencies / propaganda patterns
 *   - **Never shows "TRUE" / VERIFIED offline** — overclaiming accuracy offline
 *     is more dangerous than showing uncertainty. The strongest possible offline
 *     verdict is UNVERIFIED.
 *
 * The verdict is computed from a weighted score in [0.0, 1.0] where higher = more
 * likely to be misleading/false:
 *   - >= 0.65 → LIKELY_FALSE
 *   - >= 0.40 → MISLEADING
 *   - <  0.40 → UNVERIFIED
 *
 * Confidence is the absolute distance from the boundary, clamped to [0.55, 0.95].
 */
@Singleton
class FakeNewsAnalyzer @Inject constructor() {

    fun analyze(claim: String, dateLabel: String): VerificationResult {
        val normalized = claim.trim()
        if (normalized.isEmpty()) {
            return VerificationResult(
                claimText = claim,
                verdict = Verdict.UNVERIFIED,
                confidenceScore = 0.55f,
                sources = listOf("Offline heuristic"),
                reasoning = "Empty claim — nothing to evaluate.",
                checkedAt = dateLabel
            )
        }

        val signals = mutableListOf<Pair<String, Double>>()

        // 1. ALL-CAPS shouting: shouting > 30% of letters → propaganda signal.
        val letters = normalized.filter { it.isLetter() }
        val capsRatio = if (letters.isEmpty()) 0.0
            else letters.count { it.isUpperCase() }.toDouble() / letters.length
        if (capsRatio > 0.30 && letters.length > 12) {
            signals += "Excessive capitalization (${(capsRatio * 100).toInt()}%)" to (capsRatio * 0.6)
        }

        // 2. Punctuation density: many !!! and ??? → emotional manipulation.
        val excl = normalized.count { it == '!' }
        val ques = normalized.count { it == '?' }
        if (excl >= 2 || ques >= 3) {
            val intensity = min(1.0, (excl + ques) / 6.0)
            signals += "Heavy punctuation (! × $excl, ? × $ques)" to (intensity * 0.45)
        }

        // 3. Emotional / panic vocabulary.
        val panicHits = PANIC_TERMS.count { normalized.contains(it, ignoreCase = true) }
        if (panicHits > 0) {
            val w = min(1.0, panicHits / 4.0)
            signals += "Panic vocabulary ($panicHits term${if (panicHits > 1) "s" else ""})" to (w * 0.55)
        }

        // 4. Absolute / totalizing claims.
        val absHits = ABSOLUTE_TERMS.count { Regex("\\b$it\\b", RegexOption.IGNORE_CASE).containsMatchIn(normalized) }
        if (absHits > 0) {
            signals += "Absolute / totalizing language ($absHits)" to (min(1.0, absHits / 3.0) * 0.40)
        }

        // 5. Urgency markers ("MUST", "NOW", "IMMEDIATELY").
        val urgentHits = URGENCY_TERMS.count { normalized.contains(it, ignoreCase = true) }
        if (urgentHits > 0) {
            signals += "Urgency manipulation ($urgentHits)" to (min(1.0, urgentHits / 2.0) * 0.35)
        }

        // 6. Source citation absence — if no "according to", URL, agency name, etc.
        val hasSource = SOURCE_MARKERS.any { normalized.contains(it, ignoreCase = true) } ||
            Regex("https?://", RegexOption.IGNORE_CASE).containsMatchIn(normalized)
        if (!hasSource && normalized.length > 30) {
            signals += "No cited source" to 0.25
        }

        // 7. Propaganda / manipulation phrasing.
        val propHits = PROPAGANDA_PHRASES.count { normalized.contains(it, ignoreCase = true) }
        if (propHits > 0) {
            signals += "Propaganda phrasing ($propHits)" to (min(1.0, propHits / 2.0) * 0.50)
        }

        val rawScore = signals.sumOf { it.second }
        val normalizedScore = (rawScore / 2.5).coerceIn(0.0, 1.0)

        val verdict = when {
            normalizedScore >= 0.65 -> Verdict.LIKELY_FALSE
            normalizedScore >= 0.40 -> Verdict.MISLEADING
            else                    -> Verdict.UNVERIFIED
        }

        val confidence = when (verdict) {
            Verdict.LIKELY_FALSE -> (0.65f + ((normalizedScore - 0.65f).toFloat() * 0.85f)).coerceIn(0.65f, 0.95f)
            Verdict.MISLEADING   -> (0.55f + ((normalizedScore - 0.40f).toFloat() * 0.80f)).coerceIn(0.55f, 0.85f)
            else                 -> (0.55f + (normalizedScore.toFloat() * 0.40f)).coerceIn(0.55f, 0.75f)
        }

        val reasoning = buildString {
            append("Offline screening (no internet — verify when connected). ")
            if (signals.isEmpty()) {
                append("No strong manipulation markers detected, but offline mode cannot confirm accuracy.")
            } else {
                append("Detected: ")
                append(signals.joinToString("; ") { it.first })
                append(".")
            }
        }

        val sources = listOf("Offline heuristic", "Local pattern bank")

        return VerificationResult(
            claimText = claim,
            verdict = verdict,
            confidenceScore = confidence,
            sources = sources,
            reasoning = reasoning,
            checkedAt = dateLabel
        )
    }

    companion object {
        // Multilingual seed bank. Add more terms over time as we ship language packs.
        private val PANIC_TERMS = listOf(
            "massacre", "slaughter", "annihilated", "wiped out", "exterminated",
            "catastrophe", "apocalypse", "nightmare", "horror", "terrifying",
            "shocking", "outrage", "betrayal", "treason", "conspiracy",
            // Hindi + Arabic transliterated panic seeds
            "tabaahi", "katleaam", "majbooran", "kaarbalah",
            "majzara", "ibada", "ta3eeb"
        )

        private val ABSOLUTE_TERMS = listOf(
            "all", "every", "everyone", "always", "never", "nobody",
            "completely", "totally", "entirely", "absolutely", "100%"
        )

        private val URGENCY_TERMS = listOf(
            "MUST", "NOW", "IMMEDIATELY", "URGENT", "RIGHT NOW",
            "BEFORE IT'S TOO LATE", "ACT NOW", "SHARE NOW", "FORWARD THIS"
        )

        private val SOURCE_MARKERS = listOf(
            "according to", "reuters", "ap news", "bbc", "al jazeera",
            "reported by", "source:", "ngo", "unhcr", "msf", "icrc",
            "press release", "official statement"
        )

        private val PROPAGANDA_PHRASES = listOf(
            "they don't want you to know",
            "the government is hiding",
            "wake up", "open your eyes",
            "do your own research",
            "they will silence",
            "real truth", "hidden truth",
            "mainstream media won't",
            "censored", "suppressed"
        )
    }
}
