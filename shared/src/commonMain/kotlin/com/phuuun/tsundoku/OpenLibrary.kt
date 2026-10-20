package com.phuuun.tsundoku

import io.ktor.client.HttpClient
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val client = HttpClient { expectSuccess = true }

/** Strips spaces and dashes; null unless it's a 13-digit or 10-character ISBN. */
fun cleanIsbn(input: String): String? {
    val s = input.uppercase().filter { it.isDigit() || it == 'X' }
    val ok = (s.length == 13 && s.all { it.isDigit() }) ||
        (s.length == 10 && s.dropLast(1).all { it.isDigit() })
    return s.takeIf { ok }
}

/** Null when Open Library doesn't know the ISBN; throws when it can't be reached. */
suspend fun lookup(isbn: String): Book? = parseSearch(
    isbn,
    client.get("https://openlibrary.org/search.json?isbn=$isbn&fields=title,author_name,cover_i&limit=1").bodyAsText(),
)

internal fun parseSearch(isbn: String, body: String): Book? {
    val doc = Json.parseToJsonElement(body).jsonObject["docs"]?.jsonArray?.firstOrNull()?.jsonObject ?: return null
    return Book(
        isbn = isbn,
        title = doc["title"]?.jsonPrimitive?.content ?: return null,
        // First name only: Open Library lists translators and illustrators as authors too.
        author = doc["author_name"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.content.orEmpty(),
        coverUrl = doc["cover_i"]?.jsonPrimitive?.intOrNull?.let { "https://covers.openlibrary.org/b/id/$it-L.jpg" },
    )
}

/** Page count and size of this exact edition. Null when Open Library doesn't know the ISBN; throws when it can't be reached. */
suspend fun lookupSize(isbn: String): Pair<Int?, BookSize>? {
    val response = client.get("https://openlibrary.org/isbn/$isbn.json") { expectSuccess = false }
    if (response.status == HttpStatusCode.NotFound) return null
    check(response.status == HttpStatusCode.OK) { "Open Library: ${response.status}" }
    return parseEdition(response.bodyAsText())
}

internal fun parseEdition(body: String): Pair<Int?, BookSize> {
    val edition = Json.parseToJsonElement(body).jsonObject
    val format = edition["physical_format"]?.jsonPrimitive?.content.orEmpty().lowercase()
    return edition["number_of_pages"]?.jsonPrimitive?.intOrNull to when {
        "hard" in format -> BookSize.Tall // Hardcover, hardback
        "mass market" in format || "pocket" in format -> BookSize.Pocket
        else -> BookSize.Regular // paperback, or Open Library doesn't say
    }
}
