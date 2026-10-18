import io.github.xblocker.core.FilterSettings
import io.github.xblocker.core.RuleEngine
import io.github.xblocker.core.TimelineFilter
import org.json.JSONObject
import java.io.File

/** Replay the same sanitized fixture against either the baseline or the patched core. */
fun main(args: Array<String>) {
    require(args.size == 2 && args[1] in setOf("baseline", "patched")) {
        "Usage: ReplayRtbFixtureKt <fixture.json> <baseline|patched>"
    }
    val input = File(args[0]).readText()
    val filter = TimelineFilter(RuleEngine(FilterSettings(), ""))
    val result = filter.filter(input)
    val output = JSONObject(result.json)
    val entries = output.getJSONObject("data").getJSONObject("timelineResponse")
        .getJSONArray("instructions").getJSONObject(0).getJSONArray("entries")
    check(result.recognized)
    check(entries.length() == 1) { "The outer module must survive" }
    val items = entries.getJSONObject(0).getJSONObject("content").getJSONArray("items")
    val retainedIds = (0 until items.length()).map { items.getJSONObject(it).getString("entry_id") }
    val expected = JSONObject(input)
    val patched = args[1] == "patched"
    if (patched) {
        expected.getJSONObject("data").getJSONObject("timelineResponse")
            .getJSONArray("instructions").getJSONObject(0).getJSONArray("entries")
            .getJSONObject(0).getJSONObject("content").getJSONArray("items").remove(0)
    }
    check(expected.similar(output)) { "Unexpected changes outside the RTB item" }
    check(result.events.size == if (patched) 1 else 0)
    if (patched) check(result.events.single().rule == "TimelineRtbImageAd")
    println("recognized=${result.recognized}")
    println("events=${result.events.size}")
    result.events.forEach { println("event id=${it.id} reason=${it.reason} rule=${it.rule}") }
    println("stats=${filter.stats.toMap().toSortedMap()}")
    println("timelineEntries=${entries.length()}")
    println("moduleItems=${items.length()}")
    println("remainingEntryIds=${retainedIds.joinToString(",")}")
    println("ordinaryTweetPreserved=true")
    println("outputMatchesExpected=true")
}
