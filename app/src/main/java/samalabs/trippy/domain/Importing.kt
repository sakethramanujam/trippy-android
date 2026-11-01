package samalabs.trippy.domain

import samalabs.trippy.data.ImportCommitItem
import samalabs.trippy.data.ImportCommitRequest
import samalabs.trippy.data.ImportItem
import samalabs.trippy.data.ImportPreviewRequest
import java.util.Base64

const val MAX_IMPORT_BYTES = 5 * 1024 * 1024

/** What the importer can read, in the order the picker shows them (same as iOS). */
enum class ImportSource(val id: String, val title: String, val detail: String, val file: Boolean, val paste: Boolean, val url: Boolean) {
    Ics("ics", "Calendar file", "An .ics from Google, Outlook, Apple Calendar or a TripIt trip", file = true, paste = true, url = false),
    IcsUrl("ics_url", "Calendar feed", "A webcal:// or https:// link, like TripIt's calendar feed", file = false, paste = false, url = true),
    Flighty("flighty_csv", "Flighty", "The CSV from Flighty's flight export", file = true, paste = false, url = false),
    Pkpass("pkpass", "Wallet boarding pass", "A .pkpass file saved from an airline app or email", file = true, paste = false, url = false),
    Email("email", "Confirmation email", "Rental car receipts, flights and hotels: an .eml file or pasted text", file = true, paste = true, url = false),
    Csv("csv", "Spreadsheet (CSV)", "Any CSV: you choose which column is which", file = true, paste = false, url = false),
    GoogleTimeline(
        "google_timeline", "Google Maps Timeline",
        "Drives from Timeline.json: Google Maps → Settings → Location & privacy → Export Timeline data",
        file = true, paste = false, url = false,
    ),
}

/** Timeline import: only drives during your trips, or every drive in the file. */
enum class DrivesScope(val id: String, val label: String) { Trips("trips", "During my trips"), All("all", "Every drive") }

enum class CsvKind(val id: String, val label: String) { Flight("flight", "Flights"), Rental("rental", "Rentals"), Lodging("lodging", "Stays") }

class PickedFile(val name: String, val bytes: ByteArray)

object Importing {
    /** The preview request for whatever the user supplied, or null when there's nothing to send yet. */
    fun previewRequest(
        source: ImportSource, text: String, url: String, file: PickedFile?, csvKind: CsvKind = CsvKind.Flight,
        mapping: Map<String, String>? = null, timezone: String? = null, drivesScope: DrivesScope = DrivesScope.Trips,
    ): ImportPreviewRequest? = when {
        source.url -> url.trim().takeIf { it.isNotEmpty() }?.let { ImportPreviewRequest(source.id, url = it) }
        file != null && source == ImportSource.GoogleTimeline -> ImportPreviewRequest(
            source.id, dataBase64 = Base64.getEncoder().encodeToString(file.bytes), filename = file.name,
            timezone = timezone?.takeIf { it.isNotBlank() && it.length <= 64 }, drivesScope = drivesScope.id,
        )
        file != null -> ImportPreviewRequest(
            source.id, dataBase64 = Base64.getEncoder().encodeToString(file.bytes), filename = file.name,
            csvKind = csvKind.id, mapping = mapping?.filterValues { it.isNotBlank() }?.takeIf { it.isNotEmpty() },
        )
        source.paste && text.isNotBlank() -> ImportPreviewRequest(source.id, text = text, csvKind = csvKind.id)
        else -> null
    }

    /** Preselect what isn't already in Trippy or repeated in the same file (drives: only ones the server suggests). */
    fun initialSelection(items: List<ImportItem>): Set<Int> =
        items.indices.filter { items[it].isNew && items[it].suggested != false }.toSet()

    /** Where a Timeline drive goes: "Trip: Austin", "Journey: Texas loop", or nowhere. */
    fun matchLabel(item: ImportItem): String? {
        if (item.kind != "drive") return null
        val m = item.match
        return when {
            m?.tripTitle != null -> "Trip: ${m.tripTitle}"
            m?.journeyTitle != null -> "Journey: ${m.journeyTitle}"
            else -> "Not during a trip"
        }
    }

    /** Count from the preview's stats, e.g. outside_trips. */
    fun stat(stats: kotlinx.serialization.json.JsonObject?, key: String): Int =
        (stats?.get(key) as? kotlinx.serialization.json.JsonPrimitive)?.content?.toDoubleOrNull()?.toInt() ?: 0

    fun commitRequest(items: List<ImportItem>, selected: Set<Int>, tripId: Int?): ImportCommitRequest =
        ImportCommitRequest(
            items = items.indices.filter { it in selected }.map { ImportCommitItem(items[it].kind, items[it].fields) },
            tripId = tripId, autoTrip = true,
        )

    fun missingRequired(required: List<String>, mapping: Map<String, String>): List<String> =
        required.filter { mapping[it].isNullOrBlank() }

    /** "departure_airport" -> "Departure airport". */
    fun fieldLabel(target: String): String = when (target) {
        "check_in" -> "Check-in"
        "check_out" -> "Check-out"
        "cabin_class" -> "Cabin"
        else -> target.replace('_', ' ').replaceFirstChar { it.uppercase() }
    }

    fun duplicateNote(item: ImportItem): String? = when {
        item.duplicateOf?.inTrash == true -> "In Trash already"
        item.duplicateOf != null -> "Already in Trippy"
        item.duplicateInBatch != null -> "Repeated in this file"
        else -> null
    }
}
