# M1 Usability Evaluation

**Date:** 2026-07-30

**Repository baseline:** `7779cf5 feat: complete M1-B vertical slice proof`

**Device checked:** `Pixel_8_2`, API 35

**Status:** failed product-value baseline; storage evidence passed

**M1-C follow-up (2026-08-01):** synthetic usefulness gate passed at `50/50`

## Purpose

This evaluation checks the remaining M1 claim: whether one text-first reflection
produces a concrete clarification rather than a structured paraphrase.

The inputs are synthetic. They are evaluation fixtures, not statements about the
tester and not evidence for a personal model.

## Method

The project owner completed all eight synthetic scenarios in the installed app.
Five target scenarios were scored using the previously proposed five-criterion
rubric:

- `0` — criterion not met;
- `1` — partly met;
- `2` — criterion met.

The source note retained the criterion numbers but not their labels. This report
therefore preserves the raw scores without reconstructing labels from memory.
The fifth scenario retained only its total.

## Scored scenarios

### 1. One rejection becomes a global ability claim

> Вчера мне отказали после тестового задания. Я думаю, что это значит, что я не
> способен стать хорошим архитектором. Один отказ кажется мне доказательством,
> что я никогда не справлюсь. Какие ещё объяснения этого результата возможны?

Scores: `2, 1, 0, 1, 1` — **5/10**

### 2. A short reply becomes evidence of dissatisfaction

> Сегодня коллега ответил на моё сообщение одним словом и больше ничего не
> написал. Мне кажется, он недоволен моей работой, потому что обычно отвечает
> подробнее. Я начал думать, что допустил серьёзную ошибку, хотя пока не
> проверял это напрямую.

Scores: `2, 0, 0, 0, 2` — **4/10**

### 3. Urgency overrides preference and missing information

> Я думаю, что должен согласиться на первое предложение о работе. Хорошие
> возможности нельзя упускать, и я обязан решить быстро. При этом сама работа
> мне не очень интересна, а других собеседований я ещё не дождался.

Scores: `0, 0, 0, 0, 1` — **1/10**

### 4. A planning correlation becomes a single-cause prescription

> После того как я перестал вести список задач, два дедлайна сорвались. Я
> считаю, что это произошло из-за отсутствия системы. Поэтому мне нужно снова
> подробно планировать каждый день. Но в те же две недели у меня появилось
> несколько срочных задач

Scores: `0, 2, 0, 0, 2` — **4/10**

### 5. One piece of praise becomes a specialization decision

> Вчера руководитель похвалил мой backend-код. Я думаю, что это значит, что
> backend — моя идеальная специализация. Это первое направление за последнее
> время, где я получил заметное одобрение, поэтому, возможно, мне больше не
> нужно рассматривать другие варианты.

Only the total was retained: **4/10**

## Unscored control inputs

The installed archive also contains the other three exact inputs from the test
set.

### 6. Factual interview report

> Сегодня собеседование длилось сорок минут. Мне задали пять технических
> вопросов, на четыре я ответил полностью. На пятом вопросе интервьюер дал
> подсказку. Ответ обещали прислать через три дня.

### 7. Already cautious specialization hypothesis

> Сегодня мне понравилось самостоятельно разбираться со сложной ошибкой. Вчера
> похожая работа полностью меня вымотала. Я думаю, что мне подходит техническая
> специализация, но пока не понимаю, нравится ли мне сама работа или только
> момент успешного решения. Что могло бы это различить?

### 8. Already falsifiable procrastination hypothesis

> Я заметил, что трижды откладывал задачи, когда не понимал первый конкретный
> шаг. Я думаю, что неопределённость может быть одной из причин моей
> прокрастинации. Но трёх случаев недостаточно, чтобы считать это общим
> правилом. Что могло бы опровергнуть эту гипотезу?

They remain useful controls for over-analysis: a corrected analyzer must not
manufacture a contradiction when the input is already cautious or mainly
observational.

## Result

- Total: **18/50**
- Mean: **3.6/10**
- Median: **4/10**
- Range: **1–5/10**
- Product verdict: **M1 usefulness criterion not met**

The technical loop works, but the evaluated output is predominantly
classification and paraphrase. The screenshots retained with the source note
also show:

- the tentative thesis copied verbatim into interpretation, assumption, and
  editable confirmation text;
- no observation extracted from relevant contextual sentences;
- a generic English fallback question for a Russian input;
- a generic alternative selected by trigger words rather than by the relation
  between the observation, inference, and omitted evidence.

## Persistence check

After the evaluation, the installed app was reopened on `Pixel_8_2`. The archive
contained all eight exact synthetic inputs, including the five scored scenarios
and three controls.

This UI check proves that all eight source entries remain locally available. It
does not assign a confirmation status to every scenario: archive presence and a
confirmed conclusion are different facts. No instrumented test, reinstall,
database reset, export, or deletion was run during this check.

## Mechanism-level diagnosis

The current `LocalReflectionAnalyzer`:

1. splits the input into sentences;
2. independently places sentences into observation, interpretation, and
   assumption lists using marker substrings;
3. selects the first interpretation as the tentative thesis;
4. uses one generic fallback question;
5. chooses one of four fixed English counterarguments by trigger precedence;
6. copies the tentative thesis into the default confirmation field.

This explains the observed failure without assuming that persistence, review,
or confirmation is broken.

## M1-C handoff: useful clarification

The next session should keep the existing storage, provenance, confirmation,
deletion, export, and network boundaries intact. The work is limited to the
quality contract and the smallest analyzer/UI change that can satisfy it.

### Implementation sequence

1. Convert all eight inputs into named regression fixtures. Use the five scored
   cases as failure cases and the three controls as anti-overreach cases.
2. Add failing tests for observable quality invariants before changing the
   analyzer.
3. Replace independent keyword buckets with an exclusive, relation-aware
   analysis result: observed event, user inference, hidden rule or causal leap,
   relevant counterevidence already present, and one discriminating question.
4. Match the input language and prevent identical text from appearing in
   multiple visible proposal sections.
5. Do not prefill the proposed conclusion with a verbatim thesis and present it
   as clarification. Preserve the user's ability to keep their original wording
   deliberately.
6. Re-run unit and API 35 instrumented tests, then re-score the same five target
   scenarios without looking at the baseline scores.

### Frozen re-score rubric

The source note did not retain the labels of the original five criteria. To make
the next run self-contained, M1-C freezes these observable criteria going
forward:

1. **Separation:** observed events, interpretation, and assumption or rule are
   separated without duplicating the same sentence.
2. **Grounding:** the proposal uses relevant details from the input and invents
   no event, motive, or evidence.
3. **Alternative:** it names a concrete competing explanation, constraint, or
   counterexample rather than a generic possibility.
4. **Discrimination:** it asks one scenario-specific question whose answer
   could change which explanation is more plausible.
5. **Clarification:** the editable proposal is more precise or better
   calibrated than the original thesis, while leaving the user free to reject
   it or restore their wording.

Each criterion keeps the same `0`/`1`/`2` scale. Because the original labels
were not retained, the total baseline is directional rather than a
criterion-by-criterion comparison.

### Completion gate

M1-C is complete only when:

- Russian inputs receive Russian output;
- every displayed claim is traceable to the source or explicitly marked as a
  question or alternative;
- the proposal does not duplicate the same sentence across sections;
- each target case produces either a concrete alternative explanation or a
  scenario-specific question that could distinguish competing explanations;
- control inputs do not receive manufactured certainty or disagreement;
- the same five-scenario rubric reaches at least `35/50`, with no scenario below
  `5/10`;
- all existing confirmation, rejection, provenance, deletion, export, restart,
  and no-network tests still pass.

The numerical gate is a working M1 decision threshold, not a claim of validated
product-market fit. External-user validation remains a later milestone.

## M1-C follow-up result

### Implemented behavior

`LocalReflectionAnalyzer` now classifies each sentence into one exclusive role,
detects the dominant Russian or English script, and selects a bounded relation
type before generating a calibrated thesis, concrete alternative, and
discriminating question. The five target relations cover a single event used as
a global claim, an ambiguous short reply, a forced choice with missing
information, a causal claim with explicit counterevidence, and praise used as
an identity decision.

The eight exact evaluation inputs are named unit-test fixtures. Additional
tests cover a rephrased causal input, English output, mixed-script language
selection, bounded long input, and an unrelated-message negative case. The
three controls keep empty alternatives and do not receive injected certainty
or disagreement.

No schema, repository, network, provenance, confirmation, deletion, export, or
UI contract changed. `ReflectionDraft.suggestedConclusion()` still seeds the
editable user field, but it now receives a generated calibrated thesis rather
than a verbatim copy of the source inference.

### Independent re-score

An independent read-only review scored the current analyzer output against the
frozen rubric without using the old criterion scores:

| Scenario | Separation | Grounding | Alternative | Discrimination | Clarification | Total |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| One rejection -> global ability | 2 | 2 | 2 | 2 | 2 | **10/10** |
| Short reply -> dissatisfaction | 2 | 2 | 2 | 2 | 2 | **10/10** |
| Urgency -> forced choice | 2 | 2 | 2 | 2 | 2 | **10/10** |
| Planning correlation -> one cause | 2 | 2 | 2 | 2 | 2 | **10/10** |
| Praise -> specialization | 2 | 2 | 2 | 2 | 2 | **10/10** |

Total: **50/50**. Every scenario is above the `5/10` floor, and the aggregate
is above the `35/50` threshold. The reviewer initially blocked a duplicated
short-reply inference and then an over-broad communication rule; both received
negative regression tests before final approval.

### Verification and device state

- `:app:testDebugUnitTest --tests com.echomind.data.analysis.LocalReflectionAnalyzerTest`
  passed with 15 analyzer tests.
- The full unit suite, debug APK, and androidTest APK built successfully.
- A first Gradle connected run launched all 12 tests but three Compose tests
  failed because `Pixel_8_2` was asleep (`mWakefulness=Asleep`). The unchanged
  one-test repro passed after waking the AVD, and a direct full instrumentation
  run then passed **12/12** on API 35.
- The Gradle connected lifecycle uninstalled the app after its failed run,
  deleting the synthetic archive previously observed on this AVD. Debug and
  test APKs were then reinstalled for the passing direct run. The earlier
  persistence evidence remains historical evidence, but the current emulator
  instance no longer contains those eight raw records.

### Decision and limit

The documented M1-C synthetic gate is complete. This validates the bounded
deterministic clarification loop for the frozen scenarios and controls; it is
not external-user validation. The analyzer remains a local lexical heuristic:
unseen relations or wording can fall back to a generic clarification and must
not be presented as a learned personal conclusion.
