package io.github.xblocker.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.Test
import kotlin.test.*

class KeywordUrlEntitiesTest {
    private fun post(id: String = "1") = JSONObject().put("rest_id", id)
        .put("legacy", JSONObject()).put("details", JSONObject().put("full_text", "Join https://t.co/short"))

    private fun link(key: String = "expanded_url", value: Any = "https://t.me/example") = JSONObject().put(key, value)

    private fun search(post: JSONObject): String {
        fun entry(tweet: JSONObject) = JSONObject().put("entry_id", "tweet-${tweet.getString("rest_id")}")
            .put("content", JSONObject().put("content", JSONObject().put("tweet_results", JSONObject().put("result", tweet))))
        val entries = JSONArray().put(entry(post)).put(entry(post("2")))
            .put(JSONObject().put("entry_id", "cursor-bottom").put("content", JSONObject().put("cursor_type", "Bottom")))
        return JSONObject().put("data", JSONObject().put("search_by_raw_query", JSONObject().put("search_timeline",
            JSONObject().put("timeline", JSONObject().put("instructions", JSONArray().put(JSONObject().put("entries", entries))))))).toString()
    }

    private fun filter(input: String, rule: String = "t.me", enabled: Boolean = true) = TimelineFilter(RuleEngine(
        FilterSettings(cloudEnabled = false, customRules = rule, blockKeywordTweets = enabled), ""
    )).filter(input, "SearchTimeline")

    @Test fun `search matches literal and regex against root URL entities and keeps clean entries`() {
        val input = search(post().put("url_entities", JSONArray().put(link())))
        for (rule in listOf("t.me", "/t\\.me/i", "/t\\.me\\/([A-Za-z0-9_]+)/i")) {
            val output = filter(input, rule)
            assertTrue(output.recognized)
            assertEquals(listOf("1"), output.events.map { it.id }, rule)
            val expected = JSONObject(input)
            expected.getJSONObject("data").getJSONObject("search_by_raw_query").getJSONObject("search_timeline")
                .getJSONObject("timeline").getJSONArray("instructions").getJSONObject(0).getJSONArray("entries").remove(0)
            assertEquals(expected.toString(), output.json)
            assertEquals(input, filter(input, rule, enabled = false).json)
        }
    }

    @Test fun `independent URL containers do not mask each other`() {
        for (bodyName in listOf("root", "details", "legacy", "note")) {
            val tweet = post()
            val body = when (bodyName) {
                "root" -> tweet
                "note" -> JSONObject().put("text", "Join https://t.co/short").also {
                    tweet.put("note_tweet", JSONObject().put("note_tweet_results", JSONObject().put("result", it)))
                }
                else -> tweet.getJSONObject(bodyName)
            }
            body.put("entities", JSONObject())
            body.put("url_entities", JSONArray().put(link()))
            assertEquals(1, filter(search(tweet)).events.size, bodyName)
            body.remove("url_entities")
            body.put("entity_set", JSONObject().put("urls", JSONArray().put(link())))
            assertEquals(1, filter(search(tweet)).events.size, bodyName)
        }
    }

    @Test fun `display and unwound URLs match independently`() {
        for (field in listOf("expanded_url", "display_url", "unwound_url")) {
            val input = search(post().put("url_entities", JSONArray().put(link(field, "https://T.ME/example"))))
            assertEquals(1, filter(input, "/t\\.me/i").events.size, field)
        }
    }

    @Test fun `invalid URL containers and values are ignored without dropping clean tweets`() {
        for (invalid in listOf(JSONObject.NULL, "t.me", JSONObject().put("expanded_url", "t.me"), 123)) {
            val input = search(post().put("url_entities", invalid))
            assertEquals(input, filter(input).json)
        }
        val mixed = JSONArray().put(JSONObject.NULL).put(123).put("t.me")
            .put(link(value = JSONObject().put("value", "t.me")))
            .put(link(value = JSONArray().put("t.me")))
        val tweet = post().put("url_entities", mixed)
        val input = search(tweet)
        assertEquals(input, filter(input).json)
        mixed.put(link())
        assertEquals(1, filter(search(tweet)).events.size)
    }

    @Test fun `URL entities in quotes profiles cards and reposts do not leak into the outer tweet`() {
        val nested = post("3").put("url_entities", JSONArray().put(link()))
        for (key in listOf("quoted_status_result", "quoted_tweet_results", "retweeted_status_result", "card", "core")) {
            val outer = post().put(key, JSONObject().put("result", nested))
            val input = search(outer)
            assertEquals(input, filter(input).json, key)
        }
    }

    @Test fun `URL entity count and field length remain bounded`() {
        val links = JSONArray()
        repeat(32) { links.put(link(value = "https://example.org/$it")) }
        links.put(link())
        val input = search(post().put("url_entities", links))
        assertEquals(input, filter(input).json)
        val longUrl = search(post().put("url_entities", JSONArray().put(link(value = "x".repeat(2048) + "t.me"))))
        assertEquals(longUrl, filter(longUrl).json)
    }
}
