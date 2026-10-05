package com.xldev.happytalky.wear

import java.net.URL
import java.util.Locale
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

internal data class ParsedPortalForm(
    val pageTitle: String,
    val actionUrl: String,
    val method: String,
    val fields: List<Pair<String, String>>,
    val submitLabel: String,
    val unsupportedFields: List<String>,
)

internal data class ParsedPortalPage(
    val title: String,
    val form: ParsedPortalForm?,
    val metaRefreshUrl: String?,
)

internal object CaptivePortalFormParser {
    private val acceptWords =
        Regex(
            "(?i)\\b(accept|agree|continue|connect|go online|start|internet|online|proceed|surf|browse|hyväksy|hyväksyn|jatka|yhdistä|verkkoon|godkänn|acceptera|fortsätt|anslut|godta|fortsett|tilkob|zustimmen|weiter|verbinden)\\b"
        )

    private val termsWords =
        Regex(
            "(?i)\\b(term|terms|agree|accept|condition|conditions|privacy|policy|ehdot|käyttöehdot|hyväks|villkor|godkänn|integritet|betingelser|vilkår|zustimmen|datenschutz)\\b"
        )

    fun parse(
        html: String,
        baseUrl: String,
    ): ParsedPortalPage {
        val document =
            Jsoup.parse(
                html,
                baseUrl,
            )
        val title =
            document
                .title()
                .trim()
                .take(120)

        val candidates =
            document
                .select("form")
                .map { form ->
                    parseForm(
                        form = form,
                        pageTitle = title,
                        baseUrl = baseUrl,
                    )
                }

        val form =
            candidates
                .sortedWith(
                    compareByDescending<ParsedPortalForm> {
                        score(it)
                    }.thenBy {
                        it.unsupportedFields.size
                    }
                )
                .firstOrNull {
                    score(it) > 0
                }
                ?: parseAcceptLink(
                    document
                        .select("a[href]")
                        .firstOrNull {
                            acceptWords.containsMatchIn(
                                it.text()
                            )
                        },
                    title,
                    baseUrl,
                )

        return ParsedPortalPage(
            title = title,
            form = form,
            metaRefreshUrl =
                parseMetaRefresh(
                    document
                        .select(
                            "meta[http-equiv~=(?i)refresh]"
                        )
                        .firstOrNull()
                        ?.attr("content"),
                    baseUrl,
                ),
        )
    }

    private fun parseForm(
        form: Element,
        pageTitle: String,
        baseUrl: String,
    ): ParsedPortalForm {
        val fields =
            mutableListOf<Pair<String, String>>()
        val unsupported =
            mutableListOf<String>()
        var submitLabel = ""
        var submitField:
            Pair<String, String>? = null

        form.select("input").forEach { input ->
            val name =
                input
                    .attr("name")
                    .trim()
            val type =
                input
                    .attr("type")
                    .ifBlank { "text" }
                    .lowercase(Locale.ROOT)
            val value =
                input
                    .attr("value")

            when (type) {
                "hidden" -> {
                    if (name.isNotBlank()) {
                        fields += name to value
                    }
                }

                "submit",
                "button" -> {
                    val label =
                        value
                            .ifBlank {
                                input.attr("aria-label")
                            }
                            .trim()
                    val isPreferred =
                        acceptWords
                            .containsMatchIn(
                                label
                            )

                    if (
                        submitLabel.isBlank() ||
                        isPreferred
                    ) {
                        submitLabel =
                            label
                        submitField =
                            if (
                                name.isNotBlank() &&
                                value.isNotBlank()
                            ) {
                                name to value
                            } else {
                                null
                            }
                    }
                }

                "checkbox" -> {
                    val context =
                        listOf(
                            name,
                            input.attr("id"),
                            value,
                            input.parent()?.text().orEmpty(),
                        ).joinToString(" ")

                    if (
                        name.isNotBlank() &&
                        termsWords
                            .containsMatchIn(context)
                    ) {
                        fields +=
                            name to
                                value.ifBlank { "on" }
                    } else if (name.isNotBlank()) {
                        unsupported +=
                            "checkbox:$name"
                    }
                }

                else -> {
                    if (name.isNotBlank()) {
                        unsupported +=
                            "$type:$name"
                    }
                }
            }
        }

        form.select("button").forEach { button ->
            val type =
                button
                    .attr("type")
                    .ifBlank { "submit" }
                    .lowercase(Locale.ROOT)
            if (type == "submit") {
                val label =
                    button
                        .text()
                        .trim()
                        .ifBlank {
                            button
                                .attr("aria-label")
                                .trim()
                        }
                val isPreferred =
                    acceptWords
                        .containsMatchIn(
                            label
                        )
                val currentPreferred =
                    acceptWords
                        .containsMatchIn(
                            submitLabel
                        )

                if (
                    submitLabel.isBlank() ||
                    (
                        isPreferred &&
                        !currentPreferred
                    )
                ) {
                    submitLabel =
                        label

                    val name =
                        button
                            .attr("name")
                            .trim()
                    val value =
                        button
                            .attr("value")
                            .trim()
                    submitField =
                        if (
                            name.isNotBlank() &&
                            value.isNotBlank()
                        ) {
                            name to value
                        } else {
                            null
                        }
                }
            }
        }

        submitField?.let {
            fields += it
        }

        form.select("select[name], textarea[name]")
            .forEach {
                unsupported +=
                    "${it.tagName()}:${it.attr("name")}"
            }

        val action =
            form
                .absUrl("action")
                .ifBlank {
                    URL(
                        URL(baseUrl),
                        form.attr("action")
                    ).toString()
                }
                .ifBlank { baseUrl }

        return ParsedPortalForm(
            pageTitle = pageTitle,
            actionUrl = action,
            method =
                form
                    .attr("method")
                    .ifBlank { "GET" }
                    .uppercase(Locale.ROOT),
            fields = fields.distinct(),
            submitLabel =
                submitLabel.ifBlank {
                    "Accept & connect"
                },
            unsupportedFields =
                unsupported.distinct(),
        )
    }

    private fun parseAcceptLink(
        link: Element?,
        pageTitle: String,
        baseUrl: String,
    ): ParsedPortalForm? {
        if (link == null) {
            return null
        }

        val target =
            link
                .absUrl("href")
                .ifBlank {
                    URL(
                        URL(baseUrl),
                        link.attr("href")
                    ).toString()
                }

        if (target.isBlank()) {
            return null
        }

        return ParsedPortalForm(
            pageTitle = pageTitle,
            actionUrl = target,
            method = "GET",
            fields = emptyList(),
            submitLabel =
                link
                    .text()
                    .trim()
                    .ifBlank {
                        "Continue"
                    },
            unsupportedFields = emptyList(),
        )
    }

    private fun parseMetaRefresh(
        content: String?,
        baseUrl: String,
    ): String? {
        if (content.isNullOrBlank()) {
            return null
        }

        val match =
            Regex(
                "(?i)url\\s*=\\s*['\"]?([^'\";]+)"
            ).find(content)
                ?: return null

        val target =
            match
                .groupValues[1]
                .trim()

        return runCatching {
            URL(
                URL(baseUrl),
                target
            ).toString()
        }.getOrNull()
    }

    private fun score(
        form: ParsedPortalForm
    ): Int {
        var score = 0

        if (
            acceptWords
                .containsMatchIn(
                    form.submitLabel
                )
        ) {
            score += 5
        }

        if (
            form.fields.any {
                termsWords
                    .containsMatchIn(
                        it.first
                    )
            }
        ) {
            score += 3
        }

        score -=
            form.unsupportedFields.size * 4

        return score
    }
}
