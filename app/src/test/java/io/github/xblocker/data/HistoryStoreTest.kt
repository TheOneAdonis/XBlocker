package io.github.xblocker.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class HistoryStoreTest {
    private fun event(id: Int) = JSONObject().put("id", "$id").put("rule", "rule-$id")

    @Test fun retainsOneThousandNewestUniqueEventsAndCountsOnlyNewEvents() {
        var records = JSONArray()
        var added = 0
        repeat(11) { batch ->
            val incoming = JSONArray((batch * 100 until (batch + 1) * 100).map(::event))
            val result = HistoryStore.append(records, incoming, batch.toLong())
            records = result.records
            added += result.added
        }
        assertEquals(1100, added)
        assertEquals(1000, records.length())
        assertEquals("100", records.getJSONObject(0).getString("id"))
        assertEquals("1099", records.getJSONObject(999).getString("id"))
        val duplicate = HistoryStore.append(records, JSONArray().put(event(1099)), 99)
        assertEquals(0, duplicate.added)
        assertEquals(records.toString(), duplicate.records.toString())
    }

    @Test fun existingTwoHundredRecordsSurviveWithoutMigrationOrInputMutation() {
        val original = JSONArray((0 until 200).map { event(it).put("time", 10L) })
        val snapshot = original.toString()
        assertEquals(snapshot, HistoryStore.append(original, null, 99).records.toString())
        val result = HistoryStore.append(original, JSONArray().put(event(200)), 99)
        assertEquals(201, result.records.length())
        assertEquals(10L, result.records.getJSONObject(0).getLong("time"))
        assertEquals(snapshot, original.toString())
    }

    @Test fun batchLimitAndPrivateFieldExclusionRemainInEffect() {
        val incoming = JSONArray((0..150).map { event(it).put("text", "private-body") })
        val result = HistoryStore.append(JSONArray(), incoming, 123)
        assertEquals(100, result.added)
        assertEquals(100, result.records.length())
        assertFalse(result.records.toString().contains("private-body"))
        assertEquals(123L, result.records.getJSONObject(0).getLong("time"))
    }
}
