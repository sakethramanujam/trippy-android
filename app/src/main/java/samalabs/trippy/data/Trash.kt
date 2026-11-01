package samalabs.trippy.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.add
import samalabs.trippy.domain.Fmt
import samalabs.trippy.domain.displayTitle
import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.ceil
import kotlin.math.max

/** What's on a trip, for the delete sheet and Recently Deleted (app/trash.py `counts`). */
@Serializable
data class TripCounts(
    val flights: Int = 0, val stays: Int = 0, val rentals: Int = 0, val expenses: Int = 0,
    val activities: Int = 0, val documents: Int = 0, val settlements: Int = 0, val travelers: Int = 0,
) {
    operator fun plus(o: TripCounts) = TripCounts(
        flights + o.flights, stays + o.stays, rentals + o.rentals, expenses + o.expenses,
        activities + o.activities, documents + o.documents, settlements + o.settlements, travelers + o.travelers,
    )

    /** "3 flights, 2 stays, 1 rental" (zeros left out), like iOS. */
    val summary: String
        get() {
            fun p(n: Int, one: String, many: String) = if (n == 0) null else "$n ${if (n == 1) one else many}"
            val parts = listOfNotNull(
                p(flights, "flight", "flights"), p(stays, "stay", "stays"), p(rentals, "rental", "rentals"),
                p(expenses, "expense", "expenses"), p(activities, "activity", "activities"), p(documents, "document", "documents"),
            )
            return if (parts.isEmpty()) "No bookings or expenses" else parts.joinToString(", ")
        }
}

@Serializable
data class DeletePreview(
    @SerialName("trip_id") val tripId: Int,
    val title: String? = null,
    @SerialName("is_owner") val isOwner: Boolean = false,
    val action: String = "delete",
    val counts: TripCounts = TripCounts(),
    val unsettled: Boolean = false,
    @SerialName("unsettled_people") val unsettledPeople: Int = 0,
    @SerialName("unsettled_cents") val unsettledCents: Long = 0,
    val journey: Journey? = null,
    @SerialName("keep_days") val keepDays: Int = 30,
) {
    @Serializable
    data class Journey(val id: Int, val title: String = "", @SerialName("trips_left") val tripsLeft: Int = 0, @SerialName("journey_deleted") val journeyDeleted: Boolean = false)
}

@Serializable
data class TrashTrip(
    val id: Int,
    val title: String? = null,
    @SerialName("start_date") val startDate: String? = null,
    @SerialName("end_date") val endDate: String? = null,
    @SerialName("deleted_at") val deletedAt: String? = null,
    @SerialName("purge_at") val purgeAt: String? = null,
    val counts: TripCounts = TripCounts(),
) {
    /** Whole days until the server purges it (rounded up, never negative). */
    fun daysLeft(now: LocalDateTime): Int? {
        val p = Fmt.parse(purgeAt) ?: return null
        return max(0, ceil(Duration.between(now, p).seconds / 86_400.0).toInt())
    }

    val subtitle: String get() = listOfNotNull(Fmt.parse(startDate)?.let(Fmt::monthDayYear), counts.summary).joinToString(" · ")
}

@Serializable
data class TrashList(@SerialName("keep_days") val keepDays: Int = 30, val trips: List<TrashTrip> = emptyList())

@Serializable
data class BulkDeleteResult(val deleted: List<Deleted> = emptyList(), val skipped: List<Skipped> = emptyList(), @SerialName("keep_days") val keepDays: Int = 30) {
    @Serializable data class Deleted(val id: Int, val permanent: Boolean = false)
    @Serializable data class Skipped(val id: Int, val reason: String = "")
}

/** The Undo toast after a soft delete. [token] makes two deletes of the same trip distinct. */
data class UndoDelete(
    val ids: List<Int>,
    val text: String,
    val subtitle: String = "In Recently Deleted for 30 days",
    val token: Long = System.nanoTime(),
    val customUndo: (suspend () -> Unit)? = null,
)

/** What the delete sheet should say for a set of previews. Pure, so it's unit-tested. */
data class DeletePlan(val previews: List<DeletePreview>, val requested: Int) {
    val owned = previews.filter { it.isOwner }
    val notOwned = previews.filter { !it.isOwner }
    val isLeave = requested == 1 && previews.firstOrNull()?.isOwner == false
    val counts: TripCounts get() = owned.fold(TripCounts()) { a, p -> a + p.counts }
    val unsettled = (if (isLeave) previews else owned).filter { it.unsettled }
    val journeys: List<DeletePreview.Journey> get() = owned.mapNotNull { it.journey }.distinctBy { it.id }.sortedBy { it.id }
    val keepDays = previews.firstOrNull()?.keepDays ?: 30

    val name: String
        get() = if (requested == 1) previews.firstOrNull()?.title?.let { "“$it”" } ?: "this trip" else "${owned.size} trips"
    val title: String get() = if (isLeave) "Leave $name?" else "Delete $name?"
    val button: String get() = when { isLeave -> "Leave trip"; owned.size > 1 -> "Delete ${owned.size} trips"; else -> "Delete trip" }
    fun journeyNote(j: DeletePreview.Journey) =
        if (j.journeyDeleted) "“${j.title}” has no other trips, so the journey goes too."
        else "Leaves the journey “${j.title}”, which keeps its other trips."
    val skippedNote: String?
        get() = notOwned.takeIf { it.isNotEmpty() && !isLeave }?.let { "${it.size} selected trip${if (it.size == 1) " belongs" else "s belong"} to someone else and will be skipped." }
    fun unsettledNote(): String? {
        if (unsettled.isEmpty()) return null
        if (isLeave) return "You still have an unsettled balance on this trip. Settle up first, or leave anyway."
        val cents = unsettled.sumOf { it.unsettledCents }
        val people = unsettled.maxOf { it.unsettledPeople }
        return "Balances aren't settled: $people people still owe or are owed ${Fmt.money(cents / 100.0)}. Deleting removes those expenses too."
    }
}

/** Delete (to Recently Deleted), leave, restore and purge. Lists refresh through [TripsRepository]. */
class TrashRepository(private val api: ApiClient, private val trips: TripsRepository, private val journeys: JourneysRepository) {
    private val _undo = MutableStateFlow<UndoDelete?>(null)
    val undo: StateFlow<UndoDelete?> = _undo.asStateFlow()

    fun showCustomUndo(text: String, subtitle: String = "Tap Undo to restore", onUndo: suspend () -> Unit) {
        _undo.value = UndoDelete(emptyList(), text, subtitle = subtitle, customUndo = onUndo)
    }

    suspend fun preview(ids: List<Int>): Pair<DeletePlan?, String?> = try {
        DeletePlan(ids.map { TrippyJson.decodeFromString(DeletePreview.serializer(), api.get("/trips/$it/delete-preview")) }, ids.size) to null
    } catch (e: Exception) { null to (e.message ?: "Couldn't check this trip.") }

    /** Soft-deletes the trips the user owns and shows Undo. Returns an error message or null. */
    suspend fun delete(ids: List<Int>, permanent: Boolean = false): String? {
        val titles = ids.associateWith { trips.trip(it)?.displayTitle }
        val result = try {
            val body = buildJsonObject { putJsonArray("trip_ids") { ids.forEach { add(it) } }; put("permanent", permanent) }
            TrippyJson.decodeFromString(BulkDeleteResult.serializer(), api.post("/trips/bulk-delete", body.toString()))
        } catch (e: Exception) { return e.message ?: "Couldn't delete." }
        val deleted = result.deleted.map { it.id }
        if (deleted.isNotEmpty() && !permanent) _undo.value = UndoDelete(deleted, undoText(deleted, titles))
        afterChange()
        return if (deleted.isEmpty() && result.skipped.isNotEmpty()) "Only the trip's owner can delete it." else null
    }

    /** For travelers who don't own the trip. [force] leaves even with an unsettled balance. */
    suspend fun leave(id: Int, force: Boolean): String? = try {
        api.post("/trips/$id/leave" + if (force) "?force=true" else ""); afterChange(); null
    } catch (e: Exception) { e.message ?: "Couldn't leave this trip." }

    suspend fun restore(ids: List<Int>): String? = try {
        if (ids.size == 1) api.post("/trash/trips/${ids[0]}/restore")
        else api.post("/trash/trips/restore", buildJsonObject { putJsonArray("trip_ids") { ids.forEach { add(it) } } }.toString())
        if (_undo.value?.ids?.any { it in ids } == true) _undo.value = null
        afterChange(); null
    } catch (e: Exception) { e.message ?: "Couldn't restore." }

    suspend fun list(): Pair<TrashList?, String?> = try {
        TrippyJson.decodeFromString(TrashList.serializer(), api.get("/trash/trips")) to null
    } catch (e: Exception) { null to (e.message ?: "Couldn't load Recently Deleted.") }

    /** Deletes one trashed trip for good, or every one when [id] is null. */
    suspend fun purge(id: Int?): String? = try {
        api.delete(id?.let { "/trash/trips/$it" } ?: "/trash/trips"); null
    } catch (e: Exception) { e.message ?: "Couldn't delete." }

    fun dismissUndo(token: Long) { if (_undo.value?.token == token) _undo.value = null }

    fun clear() { _undo.value = null }

    private suspend fun afterChange() { trips.refresh(); journeys.loadSuggestions() }

    companion object {
        fun undoText(deleted: List<Int>, titles: Map<Int, String?>): String =
            if (deleted.size == 1) "Deleted ${titles[deleted[0]] ?: "trip"}" else "Deleted ${deleted.size} trips"
    }
}
