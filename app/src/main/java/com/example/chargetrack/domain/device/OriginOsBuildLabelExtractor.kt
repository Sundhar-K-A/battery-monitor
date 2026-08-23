package com.example.chargetrack.domain.device

/**
 * Attempts to extract a best-effort OriginOS build label from public
 * [android.os.Build] fields.
 *
 * ## Method
 * Scans [BuildInfo.buildDisplay] and [BuildInfo.buildIncremental] for known
 * OriginOS version string patterns. No private or hidden Build extras are accessed.
 *
 * ## Reliability
 * This is explicitly **best-effort**. vivo/iQOO devices do not guarantee that
 * OriginOS version strings appear in public Build fields. A null result means
 * no recognisable pattern was found — it does not mean the device lacks OriginOS.
 *
 * Any value returned should be stored with the label "OriginOS (best-effort)" —
 * never presented as authoritative OS version information.
 */
object OriginOsBuildLabelExtractor {

    /**
     * Patterns tried in order against each Build field candidate.
     * Ordered from most specific to least specific.
     */
    private val PATTERNS: List<Regex> = listOf(
        // "OriginOS 5.0.1", "OriginOS_5", "OriginOS5.1" etc.
        Regex("""OriginOS[\s_]?\d+[\w.]*""", RegexOption.IGNORE_CASE),
        // vivo-style incremental build codes like "OS5.0.1.2.W..." on some builds
        Regex("""OS\d+\.\d+[\w.]*""")
    )

    private val CANDIDATES_FIELDS: (BuildInfo) -> List<String> = { info ->
        listOf(info.buildDisplay, info.buildIncremental)
    }

    /**
     * Scans public Build fields for a recognisable OriginOS version string.
     *
     * @return A matched string (e.g. "OriginOS 5.0") if found; null otherwise.
     */
    fun extract(buildInfo: BuildInfo): String? {
        for (candidate in CANDIDATES_FIELDS(buildInfo)) {
            for (pattern in PATTERNS) {
                val match = pattern.find(candidate)
                if (match != null) return match.value
            }
        }
        return null
    }
}
