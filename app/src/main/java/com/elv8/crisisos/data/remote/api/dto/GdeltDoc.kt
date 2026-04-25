package com.elv8.crisisos.data.remote.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * GDELT 2.0 DOC API response (`mode=ArtList`, `format=json`).
 * Docs: https://blog.gdeltproject.org/gdelt-doc-2-0-api-debuts/
 */
@Serializable
data class GdeltDocResponse(
    val articles: List<GdeltArticle> = emptyList()
)

@Serializable
data class GdeltArticle(
    val url: String = "",
    @SerialName("url_mobile") val urlMobile: String = "",
    val title: String = "",
    val seendate: String = "",
    val socialimage: String = "",
    val domain: String = "",
    val language: String = "",
    val sourcecountry: String = ""
)
