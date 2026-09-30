package com.phuuun.tsundoku

import kotlinx.datetime.LocalDate
import kotlin.math.roundToInt

/** A file someone picked in Import library: a Tsundoku backup, or a Goodreads or StoryGraph export. */
class Incoming(val source: String, val backup: Backup)

/** Throws if [text] is none of the three. */
fun readImport(text: String): Incoming {
    val body = text.trimStart('﻿', ' ', '\t', '\r', '\n') // Goodreads' CSV starts with a byte-order mark
    if (body.startsWith("{")) return Incoming("Tsundoku backup", readBackup(body))

    val rows = parseCsv(body)
    val header = rows.firstOrNull().orEmpty().map { it.trim() }
    val records = rows.drop(1)
        .filter { row -> row.any { it.isNotBlank() } }
        .map { row -> header.withIndex().associate { (i, name) -> name to row.getOrElse(i) { "" }.trim() } }
    return when {
        "Exclusive Shelf" in header -> Incoming("Goodreads", Backup(records.mapNotNull(::fromGoodreads)))
        "Read Status" in header -> Incoming("StoryGraph", Backup(records.mapNotNull(::fromStoryGraph)))
        else -> throw IllegalArgumentException("Not a Goodreads or StoryGraph export")
    }
}

/**
 * Goodreads: one row per book. Exclusive Shelf is read, currently-reading or to-read. ISBNs are wrapped as ="…" so
 * Excel keeps them as text, My Rating 0 means unrated, and there's only one Date Read even when Read Count > 1.
 */
private fun fromGoodreads(row: Map<String, String>): Book? {
    val title = row["Title"].orEmpty().replace(SeriesSuffix, "").trim().ifEmpty { return null }
    val isbn = cleanIsbn(row["ISBN13"].orEmpty()) ?: cleanIsbn(row["ISBN"].orEmpty())
    val finished = row["Exclusive Shelf"] == "read"
    val finishedOn = parseDate(row["Date Read"]) ?: parseDate(row["Date Added"]) ?: today()
    return Book(
        isbn = isbn ?: "gr-" + row["Book Id"].orEmpty().ifEmpty { title }.filter { it.isLetterOrDigit() },
        title = title,
        author = row["Author"].orEmpty(),
        coverUrl = isbn?.let(::coverByIsbn),
        finished = finished,
        reads = if (finished) listOf(Read(finishedOn, row["My Rating"]?.toIntOrNull()?.takeIf { it in 1..5 }, plainText(row["My Review"]))) else emptyList(),
    )
}

/**
 * StoryGraph: Read Status is read, currently-reading, to-read or did-not-finish. Dates Read has one start-end span per
 * read ("2025/01/05-2025/01/20, 2026/03/01-2026/03/09"), Star Rating goes in quarter stars, and ISBN/UID isn't
 * always an ISBN.
 */
private fun fromStoryGraph(row: Map<String, String>): Book? {
    val title = row["Title"].orEmpty().ifEmpty { return null }
    val uid = row["ISBN/UID"].orEmpty()
    val isbn = cleanIsbn(uid)
    val finished = row["Read Status"] == "read"
    val finishDates = row["Dates Read"].orEmpty().split(",")
        .mapNotNull { span -> DatePattern.findAll(span).lastOrNull()?.let { toDate(it) } } // a span ends on its finish date
        .sorted()
        .ifEmpty { listOf(parseDate(row["Last Date Read"]) ?: parseDate(row["Date Added"]) ?: today()) }
    // One rating and review per book, so they belong to the latest read; earlier reads keep just their date.
    val rating = row["Star Rating"]?.toDoubleOrNull()?.roundToInt()?.takeIf { it in 1..5 }
    val reads = finishDates.mapIndexed { i, day -> if (i == finishDates.lastIndex) Read(day, rating, plainText(row["Review"])) else Read(day) }
    return Book(
        isbn = isbn ?: "sg-" + uid.ifEmpty { title }.filter { it.isLetterOrDigit() },
        title = title,
        author = row["Authors"].orEmpty().substringBefore(",").trim(),
        coverUrl = isbn?.let(::coverByIsbn),
        finished = finished,
        // ponytail: past reads of a book you're rereading or DNF'd are dropped (its open span has no finish date to tell them apart); split spans on "-" if anyone misses them
        reads = if (finished) reads else emptyList(),
    )
}

// ponytail: Open Library rate-limits covers looked up by ISBN (100 per 5 minutes per IP); Coil's disk cache means each
// is fetched once, so a big import just fills in over a few minutes. Resolve cover IDs through search.json if that bites.
private fun coverByIsbn(isbn: String) = "https://covers.openlibrary.org/b/isbn/$isbn-L.jpg?default=false"

/** "Red Rising (Red Rising Saga, #1)" → "Red Rising" */
private val SeriesSuffix = Regex("""\s*\([^()]*#\s*\d+(\.\d+)?\)\s*$""")

private val DatePattern = Regex("""(\d{4})[/-](\d{1,2})[/-](\d{1,2})""")

private fun parseDate(text: String?): LocalDate? = text?.let { DatePattern.find(it) }?.let { toDate(it) }

private fun toDate(match: MatchResult): LocalDate? {
    val (year, month, day) = match.destructured
    return runCatching { LocalDate(year.toInt(), month.toInt(), day.toInt()) }.getOrNull()
}

/** Goodreads reviews are HTML: keep the line breaks, drop the tags. */
private fun plainText(html: String?): String? = html
    ?.replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
    ?.replace(Regex("<[^>]+>"), "")
    ?.replace("&quot;", "\"")?.replace("&#39;", "'")?.replace("&lt;", "<")?.replace("&gt;", ">")?.replace("&amp;", "&")
    ?.trim()
    ?.ifEmpty { null }

/** RFC 4180 CSV: quoted fields may hold commas, line breaks and "" for a quote. */
internal fun parseCsv(text: String): List<List<String>> {
    val rows = mutableListOf<List<String>>()
    var row = mutableListOf<String>()
    val field = StringBuilder()
    var quoted = false
    var i = 0
    while (i < text.length) {
        val c = text[i]
        when {
            quoted && c == '"' && text.getOrNull(i + 1) == '"' -> { field.append('"'); i++ }
            c == '"' -> quoted = !quoted
            !quoted && c == ',' -> { row.add(field.toString()); field.clear() }
            !quoted && (c == '\n' || c == '\r') -> {
                if (c == '\r' && text.getOrNull(i + 1) == '\n') i++
                row.add(field.toString()); field.clear()
                rows.add(row); row = mutableListOf()
            }
            else -> field.append(c)
        }
        i++
    }
    if (field.isNotEmpty() || row.isNotEmpty()) { row.add(field.toString()); rows.add(row) }
    return rows
}
