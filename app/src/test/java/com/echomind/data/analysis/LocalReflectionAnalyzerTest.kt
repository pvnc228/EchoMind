package com.echomind.data.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalReflectionAnalyzerTest {

    private val analyzer = LocalReflectionAnalyzer()

    @Test
    fun `one rejection is separated into an event inference and global rule`() {
        val proposal = analyzer.analyze(M1CFixtures.oneRejection)

        assertTargetQuality(proposal, "тестового задания", "обратная связь")
        assertEquals(listOf("Вчера мне отказали после тестового задания."), proposal.draft.observations)
        assertTrue(proposal.draft.assumptions.single().contains("никогда не справлюсь"))
    }

    @Test
    fun `short reply does not prove dissatisfaction`() {
        val proposal = analyzer.analyze(M1CFixtures.shortReply)

        assertTargetQuality(proposal, "Краткость", "других сообщениях")
        assertTrue(proposal.counterargument.contains("Краткость"))
        assertTrue(proposal.draft.assumptions.single().contains("длина надёжно показывает"))
        assertTrue(proposal.draft.observations.none { it.contains("думать") })
    }

    @Test
    fun `urgency does not override interest and missing information`() {
        val proposal = analyzer.analyze(M1CFixtures.urgentOffer)

        assertTargetQuality(proposal, "Срочность", "недостающая информация")
        assertTrue(proposal.draft.assumptions.single().contains("обязан решить быстро"))
    }

    @Test
    fun `planning correlation includes the competing urgent-task explanation`() {
        val proposal = analyzer.analyze(M1CFixtures.planningCorrelation)

        assertTargetQuality(proposal, "срочных задач", "срочных задач")
        assertTrue(proposal.counterargument.contains("срочных задач"))
    }

    @Test
    fun `praise remains one signal rather than a specialization decision`() {
        val proposal = analyzer.analyze(M1CFixtures.backendPraise)

        assertTargetQuality(proposal, "отдельную работу", "без внешней похвалы")
        assertTrue(proposal.draft.assumptions.single().contains("не нужно рассматривать другие варианты"))
    }

    @Test
    fun `factual interview report stays observational`() {
        val proposal = analyzer.analyze(M1CFixtures.factualInterview)

        assertEquals(4, proposal.draft.observations.size)
        assertTrue(proposal.draft.interpretations.isEmpty())
        assertTrue(proposal.draft.assumptions.isEmpty())
        assertTrue(proposal.counterargument.isBlank())
        assertTrue(proposal.draft.tentativeThesis.contains("фактическая запись"))
        assertTrue(proposal.draft.openQuestions.isEmpty())
    }

    @Test
    fun `already cautious specialization hypothesis preserves calibration`() {
        val proposal = analyzer.analyze(M1CFixtures.cautiousSpecialization)

        assertTrue(proposal.draft.assumptions.isEmpty())
        assertTrue(proposal.counterargument.isBlank())
        assertTrue(proposal.draft.tentativeThesis.contains("осторожно"))
        assertEquals(listOf("Что могло бы это различить?"), proposal.draft.openQuestions)
    }

    @Test
    fun `already falsifiable procrastination hypothesis remains falsifiable`() {
        val proposal = analyzer.analyze(M1CFixtures.falsifiableProcrastination)

        assertTrue(proposal.draft.assumptions.isEmpty())
        assertTrue(proposal.counterargument.isBlank())
        assertTrue(proposal.draft.tentativeThesis.contains("проверяемой"))
        assertEquals(listOf("Что могло бы опровергнуть эту гипотезу?"), proposal.draft.openQuestions)
    }

    @Test
    fun `rephrased Russian causal claim keeps competing evidence relation`() {
        val proposal = analyzer.analyze(
            "Когда я перестал разбивать работу на шаги, сроки двух задач сдвинулись. " +
                "Я считаю, что причиной стало отсутствие плана. " +
                "Поэтому мне нужно планировать каждый час. " +
                "Однако в тот период появились внеплановые срочные запросы."
        )

        assertTrue(proposal.draft.tentativeThesis.contains("одну причину единственной"))
        assertTrue(proposal.counterargument.contains("внеплановые срочные запросы"))
        assertTrue(proposal.draft.openQuestions.single().contains("внеплановые срочные запросы"))
    }

    @Test
    fun `general English fallback remains language matched`() {
        val proposal = analyzer.analyze(
            "Yesterday I received a short reply. I believe it proves I made a serious mistake."
        )

        assertTrue(proposal.draft.tentativeThesis.contains("short reply"))
        assertTrue(proposal.counterargument.contains("brief message"))
        assertTrue(proposal.draft.openQuestions.single().contains("other messages"))
    }

    @Test
    fun `one Cyrillic character does not switch an English reflection to Russian`() {
        val proposal = analyzer.analyze(
            "Yesterday I received a short reply about project Я. I believe it proves I made a mistake."
        )

        assertTrue(proposal.draft.tentativeThesis.contains("short reply"))
        assertFalse(proposal.draft.tentativeThesis.any(::isCyrillic))
    }

    @Test
    fun `an unrelated message does not manufacture a short reply rule`() {
        val proposal = analyzer.analyze(
            "Сегодня коллега ответил на сообщение. Я думаю, что мне стоит сменить специальность."
        )

        assertFalse(proposal.draft.tentativeThesis.contains("Короткий ответ"))
        assertTrue(proposal.draft.assumptions.isEmpty())
        assertFalse(proposal.counterargument.contains("Краткость"))
    }

    @Test
    fun `long reflections keep structured sections bounded`() {
        val proposal = analyzer.analyze(
            (1..8).joinToString(" ") { "Observed detail $it." }
        )

        assertEquals(4, proposal.draft.observations.size)
    }

    @Test
    fun `does not invent an observation when none is explicit`() {
        val proposal = analyzer.analyze("I should always get it right.")

        assertTrue(proposal.draft.observations.isEmpty())
        assertEquals(1, proposal.draft.assumptions.size)
        assertTrue(proposal.counterargument.contains("another tentative explanation"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects blank reflections`() {
        analyzer.analyze("   ")
    }

    private fun assertTargetQuality(
        proposal: LocalReflectionProposal,
        expectedAlternativeDetail: String,
        expectedQuestionDetail: String
    ) {
        val draft = proposal.draft
        val visibleSections = draft.observations + draft.interpretations + draft.assumptions + draft.openQuestions

        assertTrue(draft.tentativeThesis.any(::isCyrillic))
        assertTrue(proposal.counterargument.any(::isCyrillic))
        assertTrue(proposal.counterargument.contains(expectedAlternativeDetail))
        assertTrue(draft.openQuestions.single().contains(expectedQuestionDetail))
        assertEquals(visibleSections.size, visibleSections.distinct().size)
        assertFalse(draft.tentativeThesis in draft.interpretations)
        assertTrue(draft.observations.isNotEmpty())
        assertTrue(draft.interpretations.isNotEmpty())
        assertTrue(draft.assumptions.isNotEmpty())
    }

    private fun isCyrillic(character: Char): Boolean =
        character in 'А'..'я' || character == 'Ё' || character == 'ё'

    private object M1CFixtures {
        val oneRejection = "Вчера мне отказали после тестового задания. Я думаю, что это значит, что я не способен стать хорошим архитектором. Один отказ кажется мне доказательством, что я никогда не справлюсь. Какие ещё объяснения этого результата возможны?"
        val shortReply = "Сегодня коллега ответил на моё сообщение одним словом и больше ничего не написал. Мне кажется, он недоволен моей работой, потому что обычно отвечает подробнее. Я начал думать, что допустил серьёзную ошибку, хотя пока не проверял это напрямую."
        val urgentOffer = "Я думаю, что должен согласиться на первое предложение о работе. Хорошие возможности нельзя упускать, и я обязан решить быстро. При этом сама работа мне не очень интересна, а других собеседований я ещё не дождался."
        val planningCorrelation = "После того как я перестал вести список задач, два дедлайна сорвались. Я считаю, что это произошло из-за отсутствия системы. Поэтому мне нужно снова подробно планировать каждый день. Но в те же две недели у меня появилось несколько срочных задач"
        val backendPraise = "Вчера руководитель похвалил мой backend-код. Я думаю, что это значит, что backend — моя идеальная специализация. Это первое направление за последнее время, где я получил заметное одобрение, поэтому, возможно, мне больше не нужно рассматривать другие варианты."
        val factualInterview = "Сегодня собеседование длилось сорок минут. Мне задали пять технических вопросов, на четыре я ответил полностью. На пятом вопросе интервьюер дал подсказку. Ответ обещали прислать через три дня."
        val cautiousSpecialization = "Сегодня мне понравилось самостоятельно разбираться со сложной ошибкой. Вчера похожая работа полностью меня вымотала. Я думаю, что мне подходит техническая специализация, но пока не понимаю, нравится ли мне сама работа или только момент успешного решения. Что могло бы это различить?"
        val falsifiableProcrastination = "Я заметил, что трижды откладывал задачи, когда не понимал первый конкретный шаг. Я думаю, что неопределённость может быть одной из причин моей прокрастинации. Но трёх случаев недостаточно, чтобы считать это общим правилом. Что могло бы опровергнуть эту гипотезу?"
    }
}
