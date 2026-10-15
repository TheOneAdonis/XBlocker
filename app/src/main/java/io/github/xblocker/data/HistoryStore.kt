package io.github.xblocker.data

import org.json.JSONArray
import org.json.JSONObject

internal object HistoryStore {
    const val MAX_RECORDS = 1000
    private const val MAX_BATCH = 100
    private val fields = listOf("id", "handle", "reason", "rule", "category")

    data class Result(val records: JSONArray, val added: Int)

    /** Deduplicate first, then retain the newest records with one linear copy. */
    fun append(existing: JSONArray, incoming: JSONArray?, now: Long): Result {
        val records = ArrayList<JSONObject>(existing.length() + MAX_BATCH)
        val known = mutableSetOf<String>()
        for (i in 0 until existing.length()) {
            val record = existing.optJSONObject(i) ?: continue
            records.add(record)
            known.add(record.optString("id"))
        }
        var added = 0
        if (incoming != null) for (i in 0 until minOf(incoming.length(), MAX_BATCH)) {
            val event = incoming.optJSONObject(i) ?: continue
            val id = event.optString("id").take(1000)
            if (id.isBlank() || !known.add(id)) continue
            val safe = JSONObject()
            for (key in fields) safe.put(key, event.optString(key).take(1000))
            safe.put("time", now)
            records.add(safe)
            added++
        }
        val retained = JSONArray()
        for (i in maxOf(0, records.size - MAX_RECORDS) until records.size) retained.put(records[i])
        return Result(retained, added)
    }
}
