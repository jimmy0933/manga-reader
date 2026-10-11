package com.mangareader.app.parser

import org.jsoup.Jsoup
import java.net.URI
import java.net.URLEncoder

data class MangaParsedDetail(
    val id: String,
    val title: String,
    val author: String,
    val coverUrl: String,
    val intro: String,
    val sourceUrl: String,
    val latestChapterTitle: String,
    val chapters: List<MangaParsedChapter>
)

data class MangaParsedChapter(
    val chapterIndex: Int,
    val chapterId: String,
    val title: String,
    val url: String
)

data class MangaSearchResult(
    val id: String,
    val title: String,
    val author: String,
    val coverUrl: String,
    val intro: String,
    val url: String
)

object ManhuarenParser {

    const val BASE_URL = "https://www.manhuaren.com"

    fun extractMangaId(url: String): String {
        val clean = url.trim().removeSuffix("/")
        val lastSegment = clean.substringAfterLast("/")
        return if (lastSegment.startsWith("manhua-")) {
            lastSegment
        } else {
            "manga_" + Math.abs(url.hashCode())
        }
    }

    fun parseDetail(sourceUrl: String, html: String): MangaParsedDetail {
        val doc = Jsoup.parse(html, BASE_URL)

        // Title
        var title = doc.selectFirst("p.detail-main-info-title")?.text()?.trim() ?: ""
        if (title.isBlank()) {
            title = doc.selectFirst("title")?.text()?.substringBefore("_")?.substringBefore("-")?.trim() ?: "未知漫畫"
        }

        // Cover
        var coverUrl = doc.selectFirst("img.detail-main-bg")?.attr("abs:src") ?: ""
        if (coverUrl.isBlank()) {
            coverUrl = doc.selectFirst(".detail-main-cover img")?.attr("abs:src") ?: ""
        }

        // Author
        var author = doc.selectFirst("p.detail-main-info-author a")?.text()?.trim() ?: ""
        if (author.isBlank()) {
            author = doc.selectFirst("p.detail-main-info-author")?.text()?.replace("作者：", "")?.trim() ?: "未知作者"
        }

        // Intro
        var intro = doc.selectFirst("p.detail-desc")?.text()?.trim() ?: ""
        if (intro.isBlank()) {
            intro = doc.selectFirst("meta[name=Description]")?.attr("content")?.trim() ?: ""
        }

        // Chapters
        val seenUrls = HashSet<String>()
        val rawList = mutableListOf<Pair<String, String>>()
        var latestTitle = ""

        // 1. 最新章節標題通常在 header 的 a.detail-list-title-2 (例如「第889话」)
        val headerLatest = doc.selectFirst("a.detail-list-title-2, .detail-list-title a[href~=^/m\\d+/]")
        if (headerLatest != null) {
            val href = headerLatest.attr("href").trim()
            val text = headerLatest.text().trim()
            if (href.matches(Regex("""^/m\d+/?$""")) && text.isNotBlank()) {
                val fullUrl = if (href.startsWith("http")) href else BASE_URL + href
                seenUrls.add(fullUrl)
                rawList.add(Pair(fullUrl, text))
                latestTitle = text
            }
        }

        // 2. 嚴格鎖定章節列表容器 (ul.detail-list-select 或 ul[id^=detail-list-select])，避免抓到網頁底部的推薦漫畫連結
        val chapterUls = doc.select("ul[id^=detail-list-select], ul.detail-list-select, ul.detail-list-1, div.detail-list")
        val chapterElements = if (chapterUls.isNotEmpty()) {
            chapterUls.select("a[href~=^/m\\d+/]")
        } else {
            doc.select("a[href~=^/m\\d+/]")
        }

        for (el in chapterElements) {
            val href = el.attr("href").trim()
            val text = el.text().trim()
            if (href.matches(Regex("""^/m\d+/?$""")) && text.isNotBlank()) {
                val fullUrl = if (href.startsWith("http")) href else BASE_URL + href
                if (!seenUrls.contains(fullUrl)) {
                    seenUrls.add(fullUrl)
                    rawList.add(Pair(fullUrl, text))
                    if (latestTitle.isBlank()) latestTitle = text
                }
            }
        }

        // 漫畫人網站原生為倒序排列 (從最新話 889 到 第 1 卷)，反轉為升序 (index 0 為第 1 話/卷，最後一個 index 為最新話)
        rawList.reverse()

        val parsedChapters = mutableListOf<MangaParsedChapter>()
        rawList.forEachIndexed { idx, pair ->
            val chUrl = pair.first
            val chTitle = pair.second
            val chId = chUrl.removeSuffix("/").substringAfterLast("/").removePrefix("m")
            parsedChapters.add(
                MangaParsedChapter(
                    chapterIndex = idx,
                    chapterId = chId,
                    title = chTitle,
                    url = chUrl
                )
            )
        }

        val id = extractMangaId(sourceUrl)
        return MangaParsedDetail(
            id = id,
            title = title,
            author = author,
            coverUrl = sanitizeUrl(coverUrl),
            intro = intro,
            sourceUrl = sourceUrl,
            latestChapterTitle = if (latestTitle.isNotBlank()) latestTitle else (parsedChapters.lastOrNull()?.title ?: ""),
            chapters = parsedChapters
        )
    }

    fun parseSearchResults(html: String): List<MangaSearchResult> {
        val doc = Jsoup.parse(html, BASE_URL)
        val results = mutableListOf<MangaSearchResult>()
        val seen = HashSet<String>()

        // Search items: each card has <img class="book-list-cover-img"> and <p class="book-list-info-title">
        val links = doc.select("a[href*=/manhua-]")
        for (link in links) {
            val href = link.attr("abs:href")
            if (seen.contains(href)) continue

            val titleEl = link.selectFirst("p.book-list-info-title")
            val coverEl = link.selectFirst("img.book-list-cover-img")
            val descEl = link.selectFirst("p.book-list-info-desc")
            val authorEl = link.selectFirst("p.book-list-info-bottom")

            if (titleEl != null) {
                seen.add(href)
                val title = titleEl.text().trim()
                val cover = coverEl?.attr("abs:src") ?: ""
                val desc = descEl?.text()?.trim() ?: ""
                val author = authorEl?.text()?.trim() ?: ""

                results.add(
                    MangaSearchResult(
                        id = extractMangaId(href),
                        title = title,
                        author = author,
                        coverUrl = sanitizeUrl(cover),
                        intro = desc,
                        url = href
                    )
                )
            }
        }

        return results
    }

    fun unpackChapterImages(chapterHtml: String): List<String> {
        // Look for Dean Edwards packer: eval(function(p,a,c,k,e,d){...}('...', radix, count, '...'.split('|'), 0, {}))
        val pattern = Regex("""\}\s*\(\s*'([\s\S]*?)'\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*'([\s\S]*?)'\.split\('\|'\)""")
        val match = pattern.find(chapterHtml)

        if (match != null) {
            val payload = match.groupValues[1]
            val radix = match.groupValues[2].toIntOrNull() ?: 62
            val count = match.groupValues[3].toIntOrNull() ?: 0
            val keys = match.groupValues[4].split("|")

            val unpacked = unpack(payload, radix, count, keys)

            // Extract var newImgs = ['...', '...'];
            val imgArrayMatch = Regex("""var\s+newImgs\s*=\s*\[([\s\S]*?)\];""").find(unpacked)
            if (imgArrayMatch != null) {
                val rawItems = imgArrayMatch.groupValues[1]
                val itemRegex = Regex("""['"]([^'"]+)['"]""")
                val urls = itemRegex.findAll(rawItems).map { sanitizeUrl(it.groupValues[1]) }.toList()
                if (urls.isNotEmpty()) {
                    return urls
                }
            }
        }

        // Fallback: check if images are directly in script or img tags
        val directArrayMatch = Regex("""var\s+newImgs\s*=\s*\[([\s\S]*?)\];""").find(chapterHtml)
        if (directArrayMatch != null) {
            val rawItems = directArrayMatch.groupValues[1]
            val itemRegex = Regex("""['"]([^'"]+)['"]""")
            return itemRegex.findAll(rawItems).map { sanitizeUrl(it.groupValues[1]) }.toList()
        }

        return emptyList()
    }

    private fun unpack(payload: String, radix: Int, count: Int, keys: List<String>): String {
        fun encode(c: Int): String {
            val prefix = if (c < radix) "" else encode(c / radix)
            val rem = c % radix
            val charStr = if (rem > 35) (rem + 29).toChar().toString() else rem.toString(36)
            return prefix + charStr
        }

        val dict = HashMap<String, String>(count)
        for (i in 0 until count) {
            val k = if (i < keys.size && keys[i].isNotEmpty()) keys[i] else encode(i)
            dict[encode(i)] = k
        }

        val regex = Regex("""\b\w+\b""")
        return regex.replace(payload) { mr ->
            dict[mr.value] ?: mr.value
        }
    }

    fun sanitizeUrl(rawUrl: String): String {
        if (rawUrl.isBlank()) return ""
        val url = rawUrl.replace("\\/", "/").trim()
        return try {
            val uri = URI(url.replace(" ", "%20"))
            uri.toASCIIString()
        } catch (e: Exception) {
            val parts = url.split("?", limit = 2)
            val pathSegments = parts[0].split("/").map { segment ->
                if (segment.contains(":") || segment.isEmpty()) segment
                else URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
            }
            val safePath = pathSegments.joinToString("/")
            if (parts.size > 1) "$safePath?${parts[1]}" else safePath
        }
    }
}
