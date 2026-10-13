package io.github.xblocker.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.Test
import kotlin.test.*

class RtbPromotionFilterTest {
    private fun envelope(vararg entries: JSONObject) = JSONObject().put("entries", JSONArray(entries.toList())).toString()
    private fun filter(settings: FilterSettings = FilterSettings()) = TimelineFilter(RuleEngine(settings, "spam"))
    private fun rtb(metadataKey: String? = null) = JSONObject().put("entry_id", "rtb-1").put("content",
        JSONObject().put("content", JSONObject().put("__typename", "TimelineRtbImageAd").apply {
            if (metadataKey != null) put(metadataKey, JSONObject().put("impression_id", "synthetic"))
        }))
    private fun tweet(id: String, text: String) = JSONObject().put("entry_id", "tweet-$id").put("content",
        JSONObject().put("content", JSONObject().put("tweet_results", JSONObject().put("result",
            JSONObject().put("rest_id", id).put("legacy", JSONObject().put("full_text", text)
                .put("in_reply_to_status_id_str", "root"))))))

    @Test fun `nested RTB typename works with raw aliased or absent promotion metadata`() {
        for (key in listOf("rtb_promoted_metadata", "promoted_metadata", null)) {
            val input = envelope(rtb(key), tweet("2", "hello"))
            val out = filter().filter(input)
            assertEquals(listOf("推广广告"), out.events.map { it.reason }, "metadata=$key")
            assertEquals("tweet-2", JSONObject(out.json).getJSONArray("entries").getJSONObject(0).getString("entry_id"))
            assertEquals(input, filter(FilterSettings(blockPromoted = false)).filter(input).json)
            assertEquals(input, filter(FilterSettings(enabled = false)).filter(input).json)
        }
    }

    @Test fun `RTB wrapper never hides the tweet from keyword matching`() {
        val wrapped = tweet("1", "spam")
        wrapped.getJSONObject("content").put("rtb_promoted_metadata", JSONObject())
        val f = filter(FilterSettings(blockPromoted = false))
        val out = f.filter(envelope(wrapped, tweet("2", "hello")))
        assertEquals(listOf("正文"), out.events.map { it.reason })
        assertEquals(2, f.stats.tweets.get())
        assertEquals(2, f.stats.replies.get())
    }

    @Test fun `quoted RTB marker cannot promote a clean outer tweet`() {
        val clean = tweet("1", "hello")
        clean.getJSONObject("content").getJSONObject("content").getJSONObject("tweet_results").getJSONObject("result")
            .put("quoted_status_result", rtb("rtb_promoted_metadata"))
        val input = envelope(clean)
        assertEquals(input, filter().filter(input).json)
    }

    @Test fun `RTB module items and spam are removed while clean siblings and cursors survive`() {
        val module = JSONObject().put("entry_id", "module-1").put("content", JSONObject().put("items",
            JSONArray().put(rtb("promoted_metadata")).put(tweet("2", "spam")).put(tweet("3", "hello"))))
        val cursor = JSONObject().put("entry_id", "cursor-bottom").put("content", JSONObject().put("cursor_type", "Bottom"))
        val f = filter()
        val out = f.filter(envelope(module, cursor))
        assertEquals(setOf("推广广告", "正文"), out.events.map { it.reason }.toSet())
        val entries = JSONObject(out.json).getJSONArray("entries")
        assertEquals(2, entries.length())
        assertEquals("tweet-3", entries.getJSONObject(0).getJSONObject("content").getJSONArray("items")
            .getJSONObject(0).getString("entry_id"))
        assertEquals("cursor-bottom", entries.getJSONObject(1).getString("entry_id"))
    }
}
