package com.echomind.data.analysis

import com.echomind.domain.model.ReflectionDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReflectionDraftParserTest {

    private val parser = ReflectionDraftParser()

    @Test
    fun parsesValidStructuredJsonSuccessfully() {
        val json = """
            {
              "tentativeThesis": "Короткий ответ — повод проверить контекст.",
              "observations": ["Сообщение было коротким", "Ответ пришел без смайлов"],
              "interpretations": ["Возможно, коллега занят"],
              "assumptions": ["Длина ответа показывает отношение"],
              "openQuestions": ["Были ли подобные ответы раньше?"],
              "counterargument": "Краткость не означает недовольство."
            }
        """.trimIndent()

        val result = parser.parse(json)
        assertNotNull(result)
        assertEquals("Короткий ответ — повод проверить контекст.", result!!.draft.tentativeThesis)
        assertEquals(listOf("Сообщение было коротким", "Ответ пришел без смайлов"), result.draft.observations)
        assertEquals(listOf("Возможно, коллега занят"), result.draft.interpretations)
        assertEquals(listOf("Длина ответа показывает отношение"), result.draft.assumptions)
        assertEquals(listOf("Были ли подобные ответы раньше?"), result.draft.openQuestions)
        assertEquals("Краткость не означает недовольство.", result.counterargument)
    }

    @Test
    fun extractsJsonFromMarkdownCodeFences() {
        val markdown = """
            Here is the analysis:
            ```json
            {
              "tentativeThesis": "Thesis from fenced block",
              "observations": ["Obs 1"],
              "interpretations": ["Interp 1"],
              "assumptions": [],
              "openQuestions": ["Q 1"],
              "counterargument": "Counter 1"
            }
            ```
        """.trimIndent()

        val result = parser.parse(markdown)
        assertNotNull(result)
        assertEquals("Thesis from fenced block", result!!.draft.tentativeThesis)
        assertEquals("Counter 1", result.counterargument)
    }

    @Test
    fun handlesPartialAndNullFieldsGracefully() {
        val partialJson = """
            {
              "tentativeThesis": "",
              "interpretations": ["Interpretation as thesis fallback"],
              "observations": null
            }
        """.trimIndent()

        val result = parser.parse(partialJson)
        assertNotNull(result)
        assertEquals("Interpretation as thesis fallback", result!!.draft.suggestedConclusion())
        assertTrue(result.draft.observations.isEmpty())
        assertTrue(result.draft.assumptions.isEmpty())
        assertEquals("", result.counterargument)
    }

    @Test
    fun returnsNullOnInvalidJson() {
        val badText = "This is not JSON at all."
        val result = parser.parse(badText)
        assertNull(result)
    }
}
