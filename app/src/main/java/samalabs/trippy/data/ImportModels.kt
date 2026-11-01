package samalabs.trippy.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

@Serializable
data class ImportPreviewRequest(
    val source: String,
    val text: String? = null,
    @SerialName("data_base64") val dataBase64: String? = null,
    val url: String? = null,
    val filename: String? = null,
    @SerialName("csv_kind") val csvKind: String = "flight",
    val mapping: Map<String, String>? = null,
    /** IANA zone for Timeline times that carry no offset (older Takeout files). */
    val timezone: String? = null,
    /** Timeline only: "trips" keeps drives during your trips, "all" keeps every drive. */
    @SerialName("drives_scope") val drivesScope: String? = null,
)

/** Which trip or journey a Timeline drive landed in, by date. */
@Serializable
data class DriveMatch(
    @SerialName("trip_id") val tripId: Int? = null,
    @SerialName("trip_title") val tripTitle: String? = null,
    @SerialName("journey_id") val journeyId: Int? = null,
    @SerialName("journey_title") val journeyTitle: String? = null,
)

@Serializable
data class ImportDuplicate(val kind: String? = null, val id: Int? = null, @SerialName("in_trash") val inTrash: Boolean = false)

@Serializable
data class ImportItem(
    val kind: String,
    val summary: String = "",
    val confidence: String? = null,
    val warnings: List<String> = emptyList(),
    @SerialName("duplicate_of") val duplicateOf: ImportDuplicate? = null,
    @SerialName("duplicate_in_batch") val duplicateInBatch: Int? = null,
    val fields: JsonObject = JsonObject(emptyMap()),
    val match: DriveMatch? = null,
    /** Drives: false when the server would leave it unticked (outside any trip). */
    val suggested: Boolean? = null,
) {
    val isNew: Boolean get() = duplicateOf == null && duplicateInBatch == null
}

@Serializable
data class ImportTargets(val required: List<String> = emptyList(), val optional: List<String> = emptyList()) {
    val all: List<String> get() = required + optional
}

@Serializable
data class ImportPreview(
    @SerialName("needs_mapping") val needsMapping: Boolean = false,
    val items: List<ImportItem> = emptyList(),
    val columns: List<String> = emptyList(),
    @SerialName("sample_rows") val sampleRows: List<JsonElement> = emptyList(),
    @SerialName("row_count") val rowCount: Int? = null,
    val targets: Map<String, ImportTargets> = emptyMap(),
    @SerialName("suggested_mapping") val suggestedMapping: Map<String, String?> = emptyMap(),
    @SerialName("new_count") val newCount: Int? = null,
    val stats: JsonObject? = null,
) {
    /** First non-empty sample for a column. Rows come back as objects or as arrays in column order. */
    fun sample(column: String): String? {
        val idx = columns.indexOf(column)
        for (row in sampleRows) {
            val v = when (row) {
                is JsonObject -> (row[column] as? JsonPrimitive)?.contentOrNull
                is JsonArray -> if (idx >= 0) (row.getOrNull(idx) as? JsonPrimitive)?.contentOrNull else null
                else -> null
            }
            if (!v.isNullOrBlank()) return v
        }
        return null
    }
}

@Serializable
data class ImportCommitItem(val kind: String, val fields: JsonObject)

@Serializable
data class ImportCommitRequest(
    val items: List<ImportCommitItem>,
    @SerialName("trip_id") val tripId: Int? = null,
    @SerialName("auto_trip") val autoTrip: Boolean = true,
)

@Serializable
data class ImportCommitResult(val created: List<JsonObject> = emptyList(), val skipped: List<JsonObject> = emptyList())

class ImportRepository(private val api: ApiClient) {
    suspend fun preview(req: ImportPreviewRequest): ImportPreview =
        TrippyJson.decodeFromString(ImportPreview.serializer(), api.post("/import/preview", TrippyJson.encodeToString(ImportPreviewRequest.serializer(), req)))

    suspend fun commit(req: ImportCommitRequest): ImportCommitResult =
        TrippyJson.decodeFromString(ImportCommitResult.serializer(), api.post("/import/commit", TrippyJson.encodeToString(ImportCommitRequest.serializer(), req)))
}
