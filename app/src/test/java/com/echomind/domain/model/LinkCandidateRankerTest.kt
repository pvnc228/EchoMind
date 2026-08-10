package com.echomind.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureNanoTime

class LinkCandidateRankerTest {

    @Test
    fun `unicode golden fixtures rank by score then recorded time then id`() {
        val candidates = listOf(
            LinkCandidateInput(
                rawRecordId = 11L,
                text = "Этот проект пока остановлен",
                recordedAt = 300L
            ),
            LinkCandidateInput(
                rawRecordId = 10L,
                text = "КАРЬЕРНЫЙ, проект развивается",
                recordedAt = 100L
            ),
            LinkCandidateInput(
                rawRecordId = 12L,
                text = "Сегодня хорошая погода",
                recordedAt = 999L
            )
        )

        val ranked = LinkCandidateRanker.rank(
            currentText = "Карьерный проект требует решения",
            themeText = "Работа",
            candidates = candidates,
            currentRawRecordId = null,
            linkedRawRecordIds = emptySet()
        )

        assertEquals(listOf(10L, 11L), ranked.map { it.rawRecordId })
        assertEquals(2, ranked.first().score)
        assertEquals(1, ranked[1].score)
        assertTrue(ranked.none { it.rawRecordId == 12L })
    }

    @Test
    fun `case punctuation NFKC and input order do not change ranked ids`() {
        val candidates = listOf(
            LinkCandidateInput(20L, "ПРОЕКТ и карьерный план", 50L),
            LinkCandidateInput(19L, "проект и карьерный план", 50L)
        )

        val first = LinkCandidateRanker.rank(
            currentText = "Карьерный проект",
            themeText = "Работа",
            candidates = candidates,
            currentRawRecordId = null,
            linkedRawRecordIds = emptySet()
        )
        val second = LinkCandidateRanker.rank(
            currentText = "КАРЬЕРНЫЙ,\u00A0проект",
            themeText = "Работа!!!",
            candidates = candidates.reversed(),
            currentRawRecordId = null,
            linkedRawRecordIds = emptySet()
        )

        assertEquals(listOf(19L, 20L), first.map { it.rawRecordId })
        assertEquals(first.map { it.rawRecordId }, second.map { it.rawRecordId })
        assertEquals(first.map { it.score }, second.map { it.score })
    }

    @Test
    fun `current and already linked raw records are excluded`() {
        val ranked = LinkCandidateRanker.rank(
            currentText = "Project decision",
            themeText = "Work",
            candidates = listOf(
                LinkCandidateInput(1L, "Project decision", 1L),
                LinkCandidateInput(2L, "Project support", 2L),
                LinkCandidateInput(3L, "Project contradiction", 3L)
            ),
            currentRawRecordId = 1L,
            linkedRawRecordIds = setOf(2L)
        )

        assertEquals(listOf(3L), ranked.map { it.rawRecordId })
    }

    @Test
    fun `local ranking benchmark remains bounded at one and ten thousand candidates`() {
        val currentText = "career project evidence decision"
        val themeText = "work planning"
        val oneThousand = rankingCorpus(1_000)
        val tenThousand = rankingCorpus(10_000)

        repeat(2) {
            LinkCandidateRanker.rank(currentText, themeText, oneThousand, null, emptySet())
            LinkCandidateRanker.rank(currentText, themeText, tenThousand, null, emptySet())
        }

        fun benchmark(candidates: List<LinkCandidateInput>): Long {
            val samples = List(4) {
                measureNanoTime {
                    LinkCandidateRanker.rank(
                        currentText = currentText,
                        themeText = themeText,
                        candidates = candidates,
                        currentRawRecordId = null,
                        linkedRawRecordIds = emptySet()
                    )
                }
            }
            return samples.drop(1).minOrNull() ?: error("No benchmark samples")
        }

        val oneThousandNanos = benchmark(oneThousand)
        val tenThousandNanos = benchmark(tenThousand)
        println(
            "RANKING_BENCHMARK nanos: 1k=$oneThousandNanos 10k=$tenThousandNanos " +
                "growth=${tenThousandNanos.toDouble() / oneThousandNanos.coerceAtLeast(1)}"
        )

        assertTrue(
            "10k local ranking exceeded the 5 second CPU budget: ${tenThousandNanos / 1_000_000}ms",
            tenThousandNanos < 5_000_000_000L
        )
        assertTrue(
            "10k ranking grew beyond the expected bounded 40x envelope",
            tenThousandNanos < oneThousandNanos * 40L + 50_000_000L
        )
        assertTrue(
            "Ranker must keep the suggestion result bounded",
            LinkCandidateRanker.rank(
                currentText,
                themeText,
                tenThousand,
                null,
                emptySet()
            ).size <= 5
        )
    }

    private fun rankingCorpus(size: Int): List<LinkCandidateInput> =
        (1..size).map { id ->
            LinkCandidateInput(
                rawRecordId = id.toLong(),
                text = "career project evidence decision archive $id",
                recordedAt = id.toLong()
            )
        }
}
