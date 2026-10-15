package io.github.xblocker

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.xblocker.core.ConfigCodec
import io.github.xblocker.core.FilterSettings
import io.github.xblocker.core.RuleEngine
import io.github.xblocker.core.TimelineFilter
import io.github.xblocker.core.Tweet
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidRuntimeTest {
    @Test fun historyRetainsOneThousandRecordsWithAndroidJson() {
        var records = org.json.JSONArray()
        repeat(11) { batch ->
            val incoming = org.json.JSONArray()
            repeat(100) { incoming.put(JSONObject().put("id", "${batch * 100 + it}")) }
            records = io.github.xblocker.data.HistoryStore.append(records, incoming, batch.toLong()).records
        }
        assertEquals(1000, records.length())
        assertEquals("100", records.getJSONObject(0).getString("id"))
        assertEquals("1099", records.getJSONObject(999).getString("id"))
    }
    @Test fun keywordTweetScopePersistsAndMatchesExpandedLinksOnAndroid() {
        val repository = io.github.xblocker.data.Repository(InstrumentationRegistry.getInstrumentation().targetContext)
        val original = repository.settings()
        try {
            assertFalse(ConfigCodec.decode(JSONObject()).blockKeywordTweets)
            val settings = FilterSettings(cloudEnabled = false, customRules = "/example\\.org/i", blockKeywordTweets = true)
            repository.save(settings)
            assertEquals(settings, repository.settings())
            val input = """{"entries":[{"entryId":"tweet-1","content":{"itemContent":{"tweet_results":{"result":{"rest_id":"1","legacy":{"full_text":"https://t.co/short","entities":{"urls":[{"expanded_url":"https://EXAMPLE.org/post"}]}}}}}}}]}"""
            assertEquals(1, TimelineFilter(RuleEngine(repository.settings(), "")).filter(input).events.size)
            repository.save(settings.copy(blockKeywordTweets = false))
            assertEquals(input, TimelineFilter(RuleEngine(repository.settings(), "")).filter(input).json)
        } finally { repository.save(original) }
    }
    @Test fun cloudSnapshotCompilesOnAndroid() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val rules = context.assets.open("keywords.txt").bufferedReader().use { it.readText() }
        val engine = RuleEngine(FilterSettings(), rules)
        assertTrue(engine.count > 500)
        assertNotNull(RuleEngine(FilterSettings(customRules = "xblocker-test"), "").match(Tweet("1", "xblocker-test", isReply = true)))
    }
    @Test fun timelineFilteringUsesAndroidJsonCorrectly() {
        val input = """{"entries":[{"entryId":"tweet-1","content":{"itemContent":{"tweet_results":{"result":{"rest_id":"1","legacy":{"full_text":"xblocker-test","in_reply_to_status_id_str":"2"}}}}}},{"entryId":"cursor-bottom-1","content":{"cursorType":"Bottom","value":"keep"}}]}"""
        val result = TimelineFilter(RuleEngine(FilterSettings(customRules = "xblocker-test"), "")).filter(input)
        assertEquals(1, result.events.size)
        assertEquals("cursor-bottom-1", JSONObject(result.json).getJSONArray("entries").getJSONObject(0).getString("entryId"))
    }
    @Test fun codecPreservesEmptyCategoryOverrides() {
        val settings = FilterSettings(disabledCategories = emptySet(), whitelist = setOf("alice"))
        assertEquals(settings, ConfigCodec.decode(ConfigCodec.encode(settings)))
    }

    @Test fun autoUpdatePreferenceDefaultsOnAndCanBeDisabled() {
        val repository = io.github.xblocker.data.Repository(InstrumentationRegistry.getInstrumentation().targetContext)
        val original = repository.autoUpdate()
        try {
            repository.setAutoUpdate(true)
            assertTrue(repository.autoUpdate())
            repository.setAutoUpdate(false)
            assertFalse(repository.autoUpdate())
        } finally { repository.setAutoUpdate(original) }
    }
    @Test fun x1216DetailsAndReplyReferencesUseAndroidJsonCorrectly() {
        val input = """{"entries":[
          {"entry_id":"tweet-1","content":{"content":{"tweet_results":{"result":{
            "rest_id":"1","legacy":{},"details":{"full_text":"xblocker-test"},"reply_to_results":{"rest_id":null}
          }}}}},
          {"entry_id":"tweet-2","content":{"content":{"tweet_results":{"result":{
            "rest_id":"2","legacy":{},"details":{"full_text":"xblocker-test"},"reply_to_results":{"rest_id":"1"}
          }}}}}
        ]}"""
        val output = TimelineFilter(RuleEngine(FilterSettings(customRules = "xblocker-test"), "")).filter(input)
        assertTrue(output.recognized)
        assertEquals("2", output.events.single().id)
        assertEquals("tweet-1", JSONObject(output.json).getJSONArray("entries").getJSONObject(0).getString("entry_id"))
    }
}
