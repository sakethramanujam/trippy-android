package samalabs.trippy.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import samalabs.trippy.data.Fixtures
import samalabs.trippy.data.ImportCommitRequest
import samalabs.trippy.data.ImportPreview
import samalabs.trippy.data.ImportPreviewRequest
import samalabs.trippy.data.TrippyJson
import java.util.Base64

class ImportingTest {
    private fun decode(name: String) = TrippyJson.decodeFromString(ImportPreview.serializer(), Fixtures.read(name))

    @Test fun decodesPreviewAndPreselectsNewItems() {
        val p = decode("import_preview.json")
        assertFalse(p.needsMapping)
        assertEquals(3, p.items.size)
        assertEquals(setOf(0), Importing.initialSelection(p.items))
        assertEquals("In Trash already", Importing.duplicateNote(p.items[1]))
        assertEquals("Repeated in this file", Importing.duplicateNote(p.items[2]))
        assertNull(Importing.duplicateNote(p.items[0]))
        assertEquals("low", p.items[1].confidence)
    }

    @Test fun commitSendsOnlySelectedFieldsUntouched() {
        val p = decode("import_preview.json")
        val req = Importing.commitRequest(p.items, setOf(0), tripId = null)
        assertEquals(1, req.items.size)
        val json = Json.parseToJsonElement(TrippyJson.encodeToString(ImportCommitRequest.serializer(), req)).jsonObject
        assertEquals(JsonPrimitive(true), json["auto_trip"])
        val fields = (json["items"] as JsonArray)[0].jsonObject["fields"]!!.jsonObject
        assertEquals(JsonPrimitive("UA123"), fields["flight_number"])
        assertTrue("cost" in fields)
    }

    @Test fun decodesMappingStage() {
        val p = decode("import_mapping.json")
        assertTrue(p.needsMapping)
        assertEquals("DL5", p.sample("Flight"))
        assertEquals("SFO", p.sample("From"))
        val mapping = p.suggestedMapping.filterValues { !it.isNullOrBlank() }.mapValues { it.value!! }
        assertEquals(listOf("departure_time"), Importing.missingRequired(p.targets.getValue("flight").required, mapping))
        val arrays = p.copy(sampleRows = listOf(Json.parseToJsonElement("""["UA1","SFO","JFK","2026"]""")))
        assertEquals("JFK", arrays.sample("To"))
    }

    @Test fun buildsPreviewRequests() {
        assertNull(Importing.previewRequest(ImportSource.Flighty, "pasted", "", null))
        assertEquals(ImportPreviewRequest("ics_url", url = "webcal://x/feed.ics"), Importing.previewRequest(ImportSource.IcsUrl, "", " webcal://x/feed.ics ", null))
        val email = Importing.previewRequest(ImportSource.Email, "Your booking", "", null)!!
        assertEquals("Your booking", email.text)
        val file = PickedFile("a.csv", "a,b\n1,2".toByteArray())
        val csv = Importing.previewRequest(ImportSource.Csv, "", "", file, CsvKind.Rental, mapOf("rental_company" to "a", "seat" to ""))!!
        assertEquals("rental", csv.csvKind)
        assertEquals(mapOf("rental_company" to "a"), csv.mapping)
        assertEquals("a,b\n1,2", String(Base64.getDecoder().decode(csv.dataBase64)))
        assertEquals("Departure airport", Importing.fieldLabel("departure_airport"))
    }
}
