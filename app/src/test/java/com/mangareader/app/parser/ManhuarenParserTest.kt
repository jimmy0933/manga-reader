package com.mangareader.app.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManhuarenParserTest {

    @Test
    fun parseEmptyHtml_returnsEmptyChapters() {
        val emptyHtml = "<html><body></body></html>"
        val parsedDetail = ManhuarenParser.parseDetail("https://www.manhuaren.com/manhua-test/", emptyHtml)

        assertEquals("manhua-test", parsedDetail.id)
        assertTrue(parsedDetail.chapters.isEmpty())
        assertEquals("未知漫畫", parsedDetail.title)
    }

    @Test
    fun parseMangaId_worksCorrectly() {
        assertEquals("manhua-test", ManhuarenParser.extractMangaId("https://www.manhuaren.com/manhua-test/"))
        assertEquals("manhua-test", ManhuarenParser.extractMangaId("https://www.manhuaren.com/manhua-test"))
    }
}
