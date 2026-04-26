package com.elv8.crisisos.core.util

/**
 * Allow-list of news + humanitarian domains we treat as credible
 * corroborators for the Fake News Detector and CrisisNews ingest.
 *
 * Includes wire services, major international broadcasters, and
 * UN-family / Red-Cross-family humanitarian publishers.
 */
object CredibleDomains {

    private val allowed: Set<String> = setOf(
        // Wire services
        "reuters.com", "apnews.com", "ap.org", "afp.com", "bloomberg.com",
        // Major international broadcasters / papers
        "bbc.com", "bbc.co.uk", "aljazeera.com", "aljazeera.net",
        "theguardian.com", "guardian.co.uk", "nytimes.com",
        "washingtonpost.com", "npr.org", "pbs.org", "dw.com",
        "france24.com", "rfi.fr", "abc.net.au", "cbc.ca",
        // UN family + humanitarian
        "un.org", "unicef.org", "unhcr.org", "ohchr.org", "who.int",
        "wfp.org", "ocha.un.org", "reliefweb.int",
        // Red Cross family + MSF
        "icrc.org", "ifrc.org", "redcross.org", "msf.org",
        "doctorswithoutborders.org",
        // Conflict trackers
        "acleddata.com", "crisisgroup.org", "hrw.org", "amnesty.org"
    )

    fun isCredible(domain: String): Boolean {
        val d = domain.trim().lowercase().removePrefix("www.")
        if (d.isEmpty()) return false
        return allowed.any { d == it || d.endsWith(".$it") }
    }
}
