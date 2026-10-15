package io.github.xblocker.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.Test
import kotlin.test.*

class KeywordTweetsTest {
    private val settings = FilterSettings(cloudEnabled = false, customRules = "t.me", blockKeywordTweets = true)
    private fun engine(settings: FilterSettings = this.settings) = RuleEngine(settings, "cloud-word")
    private fun post(id: String = "1", text: String = "https://t.co/example", reply: Boolean = false) = JSONObject()
        .put("rest_id", id).put("legacy", JSONObject().put("full_text", text).apply {
            if (reply) put("in_reply_to_status_id_str", "100")
        })
    private fun entry(post: JSONObject) = JSONObject().put("entryId", "tweet-${post.getString("rest_id")}")
        .put("content", JSONObject().put("itemContent", JSONObject().put("tweet_results", JSONObject().put("result", post))))
    private fun envelope(vararg posts: JSONObject) = JSONObject().put("entries", JSONArray(posts.map(::entry))).toString()
    private fun urls(body: JSONObject, field: String = "entities", expanded: String = "https://t.me/example") {
        body.put(field, JSONObject().put("urls", JSONArray().put(JSONObject()
            .put("url", "https://t.co/example").put("expanded_url", expanded).put("display_url", "t.me/example"))))
    }

    @Test fun `new setting defaults off for old configs and roundtrips both states`() {
        assertFalse(FilterSettings().blockKeywordTweets)
        assertFalse(ConfigCodec.decode(JSONObject("""{"onlyReplies":true}""")).blockKeywordTweets)
        for (enabled in listOf(false, true)) {
            val s = settings.copy(blockKeywordTweets = enabled)
            assertEquals(s, ConfigCodec.decode(ConfigCodec.encode(s)))
        }
    }

    @Test fun `body scope switch overrides reply-only but leaves reply behavior intact`() {
        val regular = Tweet("1", "join t.me/example")
        for (onlyReplies in listOf(false, true)) for (block in listOf(false, true)) {
            val e = engine(settings.copy(onlyReplies = onlyReplies, blockKeywordTweets = block))
            assertEquals(!onlyReplies || block, e.match(regular) != null)
            assertNotNull(e.match(regular.copy(isReply = true)))
        }
        assertEquals("正文", engine().match(regular)?.reason)
        assertNull(engine().match(regular.copy(text = "clean")))
        assertNull(engine(settings.copy(enabled = false)).match(regular))
        assertNull(engine(settings.copy(whitelist = setOf("@ALICE"))).match(regular.copy(handle = "alice")))
    }

    @Test fun `extra body scope does not expand author name filtering`() {
        val named = Tweet("1", "clean", name = "t.me", handle = "t.me")
        assertNull(engine().match(named))
        assertEquals("昵称", engine().match(named.copy(isReply = true))?.reason)
        assertEquals("昵称", engine(settings.copy(onlyReplies = false)).match(named)?.reason)
        assertNull(engine(settings.copy(checkNames = false)).match(named.copy(isReply = true)))
    }

    @Test fun `extended scope uses existing cloud and regex settings`() {
        assertNull(engine().match(Tweet("1", "cloud-word")))
        assertNotNull(engine(settings.copy(cloudEnabled = true)).match(Tweet("1", "cloud-word")))
        val cloud = RuleEngine(settings.copy(cloudEnabled = true, disabledCategories = setOf("links")), "# [links]\ncloud-word")
        assertNull(cloud.match(Tweet("1", "cloud-word")))
        assertNotNull(engine(settings.copy(customRules = "/T\\.ME/i")).match(Tweet("1", "t.me/example")))
        assertNull(engine(settings.copy(customRules = "")).match(Tweet("1", "t.me/example")))
    }

    @Test fun `expanded links work for legacy details and long notes without modifying clean entries`() {
        for (shape in listOf("legacy", "details", "note")) {
            val target = post()
            when (shape) {
                "legacy" -> urls(target.getJSONObject("legacy"))
                "details" -> {
                    val details = target.getJSONObject("legacy")
                    urls(details)
                    target.put("details", details).put("legacy", JSONObject())
                }
                else -> {
                    val note = JSONObject().put("text", "long post https://t.co/example")
                    urls(note, "entity_set")
                    target.put("note_tweet", JSONObject().put("note_tweet_results", JSONObject().put("result", note)))
                }
            }
            val clean = post("2", "clean")
            val input = envelope(target, clean)
            val output = TimelineFilter(engine()).filter(input)
            assertEquals("1", output.events.single().id, shape)
            assertEquals(entry(clean).toString(), JSONObject(output.json).getJSONArray("entries").getJSONObject(0).toString())
            assertEquals(input, TimelineFilter(engine(settings.copy(blockKeywordTweets = false))).filter(input).json)
        }
    }

    @Test fun `quoted links and profile links never remove a clean outer post`() {
        val outer = post(text = "clean")
        val quote = post("2")
        urls(quote.getJSONObject("legacy"))
        outer.put("quoted_status_result", JSONObject().put("result", quote))
        val user = JSONObject()
        urls(user)
        outer.put("core", JSONObject().put("user_results", JSONObject().put("result", JSONObject().put("legacy", user))))
        val input = envelope(outer)
        assertEquals(input, TimelineFilter(engine()).filter(input).json)
    }

    @Test fun `new scope filters module items and preserves cursors`() {
        val cursor = JSONObject().put("entryId", "cursor-bottom").put("content", JSONObject().put("cursorType", "Bottom").put("value", "opaque"))
        val input = JSONObject().put("moduleItems", JSONArray().put(entry(post(text = "t.me/example"))).put(cursor)).toString()
        val output = TimelineFilter(engine()).filter(input)
        assertEquals(1, output.events.size)
        assertEquals(cursor.toString(), JSONObject(output.json).getJSONArray("moduleItems").getJSONObject(0).toString())
    }
}
