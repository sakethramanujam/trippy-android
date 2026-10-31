package samalabs.trippy.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class TrashTest {
    private fun preview(json: String) = TrippyJson.decodeFromString(DeletePreview.serializer(), json)

    private val lisbon = preview(
        """{"trip_id":1,"title":"Lisbon","is_owner":true,"action":"delete","counts":{"flights":2,"stays":1,"rentals":0,"expenses":3,"travelers":2},
           "unsettled":true,"unsettled_people":2,"unsettled_cents":4250,"journey":{"id":9,"title":"Iberia","trips_left":0,"journey_deleted":true},"keep_days":30,"extra":1}""",
    )
    private val porto = preview("""{"trip_id":2,"title":"Porto","is_owner":true,"counts":{"flights":1,"stays":1},"journey":{"id":9,"title":"Iberia","trips_left":1}}""")
    private val shared = preview("""{"trip_id":3,"title":"Ski week","is_owner":false,"action":"leave","unsettled":true}""")

    @Test fun countsSummary() {
        assertEquals("2 flights, 1 stay, 3 expenses", lisbon.counts.summary)
        assertEquals("No bookings or expenses", TripCounts().summary)
        assertEquals(TripCounts(flights = 3, stays = 2, expenses = 3, travelers = 2), lisbon.counts + porto.counts)
    }

    @Test fun singleOwnedDelete() {
        val p = DeletePlan(listOf(lisbon), 1)
        assertFalse(p.isLeave)
        assertEquals("Delete “Lisbon”?", p.title)
        assertEquals("Delete trip", p.button)
        assertEquals("“Iberia” has no other trips, so the journey goes too.", p.journeyNote(p.journeys.single()))
        assertTrue(p.unsettledNote()!!.contains("$42.50"))
        assertNull(p.skippedNote)
    }

    @Test fun bulkSkipsSharedTripsAndMergesJourneys() {
        val p = DeletePlan(listOf(lisbon, porto, shared), 3)
        assertFalse(p.isLeave)
        assertEquals("Delete 2 trips?", p.title)
        assertEquals("Delete 2 trips", p.button)
        assertEquals(1, p.journeys.size)
        assertEquals("1 selected trip belongs to someone else and will be skipped.", p.skippedNote)
        assertEquals(listOf(1), p.unsettled.map { it.tripId })
    }

    @Test fun nonOwnerLeaves() {
        val p = DeletePlan(listOf(shared), 1)
        assertTrue(p.isLeave)
        assertEquals("Leave “Ski week”?", p.title)
        assertEquals("Leave trip", p.button)
        assertTrue(p.unsettledNote()!!.startsWith("You still have"))
    }

    @Test fun trashListDecodesAndCountsDown() {
        val list = TrippyJson.decodeFromString(
            TrashList.serializer(),
            """{"keep_days":30,"trips":[{"id":4,"title":"Austin","start_date":"2026-03-02","end_date":"2026-03-05",
               "deleted_at":"2026-10-01T10:00:00","purge_at":"2026-10-31T10:00:00","counts":{"flights":2}}]}""",
        )
        val t = list.trips.single()
        assertEquals(1, t.daysLeft(LocalDateTime.of(2026, 10, 30, 12, 0)))
        assertEquals(0, t.daysLeft(LocalDateTime.of(2026, 11, 2, 0, 0)))
        assertEquals(30, t.daysLeft(LocalDateTime.of(2026, 10, 1, 10, 0)))
        assertEquals("Mar 2, 2026 · 2 flights", t.subtitle)
    }

    @Test fun bulkResultAndUndoText() {
        val r = TrippyJson.decodeFromString(
            BulkDeleteResult.serializer(),
            """{"deleted":[{"id":1,"journey":{"id":9},"permanent":false}],"skipped":[{"id":3,"reason":"not_owner"}],"keep_days":30}""",
        )
        assertEquals(listOf(1), r.deleted.map { it.id })
        assertEquals("not_owner", r.skipped.single().reason)
        assertEquals("Deleted Lisbon", TrashRepository.undoText(listOf(1), mapOf(1 to "Lisbon")))
        assertEquals("Deleted 2 trips", TrashRepository.undoText(listOf(1, 2), emptyMap()))
    }
}
