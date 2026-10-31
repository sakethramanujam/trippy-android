package samalabs.trippy.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import samalabs.trippy.data.ApiExpense
import samalabs.trippy.data.ApiParticipant
import samalabs.trippy.data.ApiSplit
import samalabs.trippy.data.ApiTrip
import samalabs.trippy.data.ExpenseBody
import samalabs.trippy.data.Fixtures
import samalabs.trippy.data.SplitValueBody
import samalabs.trippy.data.TripLedger
import samalabs.trippy.data.TrippyJson

class MoneyTest {
    @Test fun parsesDollarText() {
        assertEquals(123450L, Cents.parse("$1,234.50"))
        assertEquals(1235L, Cents.parse("12.345"))
        assertEquals(1250L, Cents.parse("12,5"))
        assertEquals(700L, Cents.parse(" 7 "))
        assertNull(Cents.parse("abc"))
        assertNull(Cents.parse(""))
        assertEquals("12.30", Cents.plain(1230))
    }

    @Test fun allocateGivesOddCentsToEarlierEntries() {
        assertEquals(mapOf(1 to 334L, 2 to 333L, 3 to 333L), Cents.allocate(1000, listOf(1 to 1.0, 2 to 1.0, 3 to 1.0)))
        assertEquals(mapOf(1 to 25L, 2 to 75L), Cents.allocate(100, listOf(1 to 1.0, 2 to 3.0)))
        val parts = Cents.allocate(9999, listOf(1 to 33.3, 2 to 33.3, 3 to 33.4))
        assertEquals(9999L, parts.values.sum())
        assertTrue(Cents.allocate(100, listOf(1 to 0.0)).isEmpty())
    }

    @Test fun draftStatusPerMode() {
        val ids = SplitDraft.payerFirst(listOf(1, 2, 3), payer = 2)
        assertEquals(listOf(2, 1, 3), ids)
        val equal = SplitDraft(1000, ids, SplitMode.Equal, emptyMap())
        assertEquals(334L, equal.preview[2])
        assertTrue(equal.status!!.second)
        assertNull(equal.splitValues())

        val exact = SplitDraft(1000, ids, SplitMode.Exact, mapOf(2 to "5", 1 to "3"))
        assertEquals("$2.00 left to assign", exact.status!!.first)
        assertFalse(exact.status!!.second)
        val exactOk = exact.copy(values = mapOf(2 to "5", 1 to "3", 3 to "2.00"))
        assertTrue(exactOk.status!!.second)
        assertEquals(listOf(2 to 5.0, 1 to 3.0, 3 to 2.0), exactOk.splitValues())

        val pct = SplitDraft(1000, ids, SplitMode.Percent, mapOf(2 to "50", 1 to "25"))
        assertEquals("25% left", pct.status!!.first)
        assertTrue(pct.copy(values = mapOf(2 to "50", 1 to "25", 3 to "25")).status!!.second)

        val shares = SplitDraft(1000, ids, SplitMode.Shares, mapOf(2 to "2", 1 to "1", 3 to "1"))
        assertEquals(500L, shares.preview[2])
        assertTrue(shares.status!!.second)
        assertFalse(SplitDraft(1000, emptyList(), SplitMode.Equal, emptyMap()).status!!.second)
        assertNull(SplitDraft(null, ids, SplitMode.Equal, emptyMap()).status)
    }

    @Test fun prefillsEditValues() {
        val e = ApiExpense(id = 1, amount = 10.0, splitMode = "exact", splits = listOf(ApiSplit(1, 600), ApiSplit(2, 400)))
        assertEquals(mapOf(1 to "6.00", 2 to "4.00"), e.initialSplitValues())
        val s = ApiExpense(id = 1, amount = 10.0, splitMode = "shares", splits = listOf(ApiSplit(1, 600, share = 2.0), ApiSplit(2, 400, share = 1.5)))
        assertEquals(mapOf(1 to "2", 2 to "1.5"), s.initialSplitValues())
        assertEquals(1000L, s.cents)
    }

    @Test fun decodesLedgerAndNamesPeople() {
        val ledger = TrippyJson.decodeFromString(TripLedger.serializer(), Fixtures.read("ledger.json"))
        assertEquals(12000L, ledger.totalCents)
        assertEquals(2, ledger.simplified.size)
        assertEquals(-4000L, ledger.people[1].netCents)
        assertEquals(5, ledger.settlements.single().id)
        val trip = ApiTrip(id = 7, ownerId = 1, participants = listOf(ApiParticipant(id = 2, displayName = "Sam Lee")))
        val people = moneyPeople(trip, ledger, null)
        assertEquals(listOf(1, 2, 3), people.map { it.id })
        assertEquals("Alex Rivera", people[0].name)
        assertEquals("AR", people[0].initials)
        assertEquals(listOf(1, 2), splitMemberIds(trip))
    }

    @Test fun encodesSplitBody() {
        val equal = TrippyJson.encodeToString(ExpenseBody.serializer(), ExpenseBody(12.5, "Gas", 1, "equal", listOf(1, 2)))
        val o = Json.parseToJsonElement(equal).jsonObject
        assertEquals("[1,2]", o["split_among"].toString())
        assertFalse("splits" in o)
        val pct = TrippyJson.encodeToString(ExpenseBody.serializer(), ExpenseBody(10.0, "x", 1, "percent", listOf(1, 2), listOf(SplitValueBody(1, 60.0), SplitValueBody(2, 40.0))))
        assertTrue(pct.contains("\"splits\":[{\"user_id\":1,\"value\":60.0}"))
    }
}
