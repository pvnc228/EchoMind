package com.echomind.data.repository

import androidx.room.withTransaction
import com.echomind.data.local.AppDatabase
import com.echomind.data.local.dao.KnowledgeDao
import com.echomind.data.local.entity.EvidenceLinkEntity
import com.echomind.data.local.entity.HomeCardDispositionEntity
import com.echomind.data.local.entity.ThemeEntity
import com.echomind.data.local.entity.ThemeLinkEntity
import com.echomind.data.settings.SettingsStore
import com.echomind.domain.model.HomeRelevance
import com.echomind.domain.model.HomeRelevanceBuilder
import com.echomind.domain.model.HomeCard
import com.echomind.domain.model.HomeNavigationTarget
import com.echomind.domain.model.LinkCandidateInput
import com.echomind.domain.model.LinkCandidateRanker
import com.echomind.domain.model.KnowledgeSearchResult
import com.echomind.domain.model.RelatedRecord
import com.echomind.domain.model.Relationship
import com.echomind.domain.model.Theme
import com.echomind.domain.model.ThemeCandidate
import com.echomind.domain.model.ThemeConclusion
import com.echomind.domain.model.PendingThemeLink
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KnowledgeRepository @Inject constructor(
    private val database: AppDatabase,
    private val knowledgeDao: KnowledgeDao,
    private val settingsStore: SettingsStore
) {
    suspend fun getThemes(): List<Theme> =
        knowledgeDao.getActiveThemes().map { theme ->
            Theme(
                id = theme.id,
                name = theme.name,
                createdAt = theme.createdAt,
                archivedAt = theme.archivedAt,
                conclusionCount = knowledgeDao.getConfirmedLinksForTheme(theme.id).size
            )
        }

    suspend fun createTheme(name: String): Long {
        require(name.isNotBlank()) { "A theme needs a name." }
        return knowledgeDao.insertTheme(
            ThemeEntity(name = name.trim(), createdAt = System.currentTimeMillis())
        )
    }

    suspend fun renameTheme(id: Long, name: String) {
        require(name.isNotBlank()) { "A theme needs a name." }
        check(knowledgeDao.renameTheme(id, name.trim()) == 1) { "Theme $id does not exist." }
    }

    suspend fun archiveTheme(id: Long) {
        check(
            knowledgeDao.archiveTheme(id, System.currentTimeMillis()) == 1
        ) { "Theme $id does not exist." }
    }

    suspend fun deleteTheme(id: Long) {
        database.withTransaction {
            knowledgeDao.deleteLinksForTheme(id)
            check(knowledgeDao.deleteThemeById(id) == 1) { "Theme $id does not exist." }
        }
    }

    suspend fun linkConclusionToTheme(themeId: Long, revisionId: Long) {
        requireNotNull(knowledgeDao.getThemeById(themeId)) { "Theme $themeId does not exist." }
        requireNotNull(knowledgeDao.getRevisionById(revisionId)) { "Revision $revisionId does not exist." }
        check(
            knowledgeDao.insertThemeLink(
            ThemeLinkEntity(
                themeId = themeId,
                conclusionRevisionId = revisionId,
                confirmed = true,
                createdAt = System.currentTimeMillis()
            )
            ) != -1L
        ) { "Theme link already exists and requires explicit review." }
    }

    suspend fun unlinkConclusionFromTheme(themeId: Long, revisionId: Long) {
        knowledgeDao.deleteThemeLink(themeId, revisionId)
    }

    suspend fun getThemeConclusions(themeId: Long): List<ThemeConclusion> {
        requireNotNull(knowledgeDao.getThemeById(themeId)) { "Theme $themeId does not exist." }
        return knowledgeDao.getConfirmedLinksForTheme(themeId).map { link ->
            val revision = requireNotNull(knowledgeDao.getRevisionById(link.conclusionRevisionId))
            ThemeConclusion(
                themeId = themeId,
                conclusionText = revision.text,
                revisionVersion = revision.version,
                revisionId = revision.id
            )
        }
    }

    suspend fun getThemeName(themeId: Long): String =
        requireNotNull(knowledgeDao.getThemeById(themeId)) { "Theme $themeId does not exist." }.name

    suspend fun getConclusionsForRevision(revisionId: Long): List<Theme> =
        knowledgeDao.getConfirmedLinksForRevision(revisionId).map { link ->
            val theme = requireNotNull(knowledgeDao.getThemeById(link.themeId))
            Theme(
                id = theme.id,
                name = theme.name,
                createdAt = theme.createdAt,
                archivedAt = theme.archivedAt,
                conclusionCount = knowledgeDao.getConfirmedLinksForTheme(theme.id).size
            )
        }

    suspend fun getPendingThemesForRevision(revisionId: Long): List<PendingThemeLink> =
        knowledgeDao.getPendingThemeLinksForRevision(revisionId).mapNotNull { link ->
            val theme = knowledgeDao.getThemeById(link.themeId) ?: return@mapNotNull null
            PendingThemeLink(
                linkId = link.id,
                themeId = theme.id,
                themeName = theme.name,
                revisionId = revisionId
            )
        }

    suspend fun reviewPendingThemeLink(linkId: Long, accept: Boolean) {
        database.withTransaction {
            if (accept) {
                check(knowledgeDao.confirmThemeLink(linkId) == 1) {
                    "Pending theme link $linkId is no longer reviewable."
                }
            } else {
                check(knowledgeDao.rejectThemeLink(linkId) == 1) {
                    "Pending theme link $linkId is no longer reviewable."
                }
            }
        }
    }

    suspend fun linkRelatedRecord(
        revisionId: Long,
        sourceRecordId: Long,
        relationship: String
    ) {
        require(relationship == Relationship.SUPPORTS || relationship == Relationship.CONTRADICTS) {
            "Relationship must be supports or contradicts."
        }
        requireNotNull(knowledgeDao.getRevisionById(revisionId)) { "Revision $revisionId missing." }
        requireNotNull(knowledgeDao.getRawRecordById(sourceRecordId)) { "Record $sourceRecordId missing." }
        knowledgeDao.insertEvidenceLink(
            EvidenceLinkEntity(
                conclusionRevisionId = revisionId,
                sourceRawRecordId = sourceRecordId,
                relationship = relationship,
                status = "confirmed",
                origin = "user_confirmed",
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun unlinkRelatedRecord(revisionId: Long, sourceRecordId: Long) {
        knowledgeDao.deleteEvidenceLink(revisionId, sourceRecordId)
    }

    suspend fun getRelatedRecords(revisionId: Long): List<RelatedRecord> {
        val revision = knowledgeDao.getRevisionById(revisionId) ?: return emptyList()
        val ownRawRecordId = knowledgeDao.getConclusionById(revision.conclusionId)
            ?.rawRecordId
        return knowledgeDao.getEvidenceLinksForRevision(revisionId).mapNotNull { link ->
            if (link.status != "confirmed" || link.sourceRawRecordId == ownRawRecordId) {
                return@mapNotNull null
            }
            val raw = knowledgeDao.getRawRecordById(link.sourceRawRecordId) ?: return@mapNotNull null
            RelatedRecord(
                rawRecordId = raw.id,
                relationship = link.relationship,
                sourceText = raw.originalText,
                recordedAt = raw.createdAt,
                linkId = link.id,
                status = link.status
            )
        }
    }

    suspend fun getPendingRelatedRecords(revisionId: Long): List<RelatedRecord> {
        val links = knowledgeDao.getPendingEvidenceLinksForRevision(revisionId)
        return links.mapNotNull { link ->
            val raw = knowledgeDao.getRawRecordById(link.sourceRawRecordId) ?: return@mapNotNull null
            RelatedRecord(
                rawRecordId = raw.id,
                relationship = link.relationship,
                sourceText = raw.originalText,
                recordedAt = raw.createdAt,
                linkId = link.id,
                status = link.status
            )
        }
    }

    suspend fun reviewPendingRelatedRecord(linkId: Long, accept: Boolean) {
        database.withTransaction {
            val link = knowledgeDao.getEvidenceLinkById(linkId)
                ?: error("Pending evidence link $linkId is missing.")
            check(link.status != "confirmed") { "Evidence link $linkId is already confirmed." }
            if (accept) {
                check(knowledgeDao.confirmEvidenceLink(linkId) == 1) {
                    "Pending evidence link $linkId is no longer reviewable."
                }
            } else {
                check(knowledgeDao.deleteEvidenceLinkById(linkId) == 1) {
                    "Pending evidence link $linkId is no longer reviewable."
                }
            }
        }
    }

    suspend fun getLinkCandidates(currentRevisionId: Long, limit: Int = 5): List<RelatedRecord> {
        val currentRevision = knowledgeDao.getRevisionById(currentRevisionId) ?: return emptyList()
        val currentConclusion = currentRevision.text
        val currentRaw = knowledgeDao.getConclusionById(currentRevision.conclusionId)
            ?.rawRecordId
        val currentThemeNames = knowledgeDao.getConfirmedLinksForRevision(currentRevisionId)
            .mapNotNull { link -> knowledgeDao.getThemeById(link.themeId)?.name.orEmpty() }
        val linkedSourceIds = knowledgeDao.getEvidenceLinksForRevision(currentRevisionId)
            .map { it.sourceRawRecordId }
            .toSet()
        return LinkCandidateRanker.rank(
            currentText = currentConclusion,
            themeText = currentThemeNames.joinToString(" "),
            candidates = knowledgeDao.getAllRawRecords().map { raw ->
                LinkCandidateInput(
                    rawRecordId = raw.id,
                    text = raw.originalText,
                    recordedAt = raw.createdAt
                )
            },
            currentRawRecordId = currentRaw,
            linkedRawRecordIds = linkedSourceIds,
            limit = limit
        )
    }

    suspend fun getManualLinkCandidates(
        currentRevisionId: Long,
        query: String = ""
    ): List<RelatedRecord> {
        val currentRevision = requireNotNull(knowledgeDao.getRevisionById(currentRevisionId)) {
            "Revision $currentRevisionId missing."
        }
        val currentRaw = knowledgeDao.getConclusionById(currentRevision.conclusionId)?.rawRecordId
        val linkedSourceIds = knowledgeDao.getEvidenceLinksForRevision(currentRevisionId)
            .map { it.sourceRawRecordId }
        val excludedIds = buildList {
            currentRaw?.let(::add)
            addAll(linkedSourceIds)
        }.distinct()
        val normalizedQuery = query.trim()
        return knowledgeDao.getRawRecordsExcluding(excludedIds)
            .asSequence()
            .filter { normalizedQuery.isBlank() || it.originalText.contains(normalizedQuery, ignoreCase = true) }
            .map { raw ->
                RelatedRecord(
                    rawRecordId = raw.id,
                    relationship = "",
                    sourceText = raw.originalText,
                    recordedAt = raw.createdAt
                )
            }
            .toList()
    }

    suspend fun getHomeRelevance(): HomeRelevance {
        val now = System.currentTimeMillis()
        val legacySuppressionReset = settingsStore.resetLegacySuppressionsIfNeeded()
        val themes = knowledgeDao.getActiveThemes()
        val themeLinks = knowledgeDao.getConfirmedThemeLinksForCurrentRevisions()
        val conclusions = knowledgeDao.getAllConclusions()
        val conclusionsById = conclusions.associateBy { it.id }
        val rawRecords = knowledgeDao.getRawRecordsForCurrentConclusions().associateBy { it.id }
        val revisions = knowledgeDao.getCurrentRevisions().associateBy { it.id }
        val ownRawRecordByRevision = revisions.mapValues { (_, revision) ->
            conclusionsById[revision.conclusionId]?.rawRecordId
        }
        val evidenceByRevision = knowledgeDao.getEvidenceLinksForCurrentRevisions()
            .filter { it.status == "confirmed" }
            .groupBy { it.conclusionRevisionId }
        val decisions = knowledgeDao.getDecisionsForCurrentRevisions()
        val outcomesByDecision = knowledgeDao.getOutcomesForCurrentRevisionDecisions()
            .groupBy { it.decisionId }
        val decisionsByRevision = decisions
            .filter { it.sourceRevisionId != null }
            .groupBy { it.sourceRevisionId }
        val currentRevisionByConclusion = conclusions.mapNotNull { conclusion ->
            conclusion.currentRevisionId?.let { conclusion.id to it }
        }.toMap()
        val currentRevisionIds = currentRevisionByConclusion.values.toSet()
        val themedCurrentRevisionIds = themeLinks
            .map { it.conclusionRevisionId }
            .filter { it in currentRevisionIds }
            .toSet()

        fun candidate(
            themeId: Long,
            name: String,
            scopeType: com.echomind.domain.model.CoverageScopeType,
            scopeId: Long,
            revisionIds: List<Long>,
            themeLinkSubset: List<com.echomind.data.local.entity.ThemeLinkEntity>,
            unfinishedSince: Long? = null,
            navigationTarget: HomeNavigationTarget? = null
        ): ThemeCandidate {
            val currentEvidence = revisionIds.flatMap { evidenceByRevision[it].orEmpty() }
            val externalEvidence = currentEvidence.filter { evidence ->
                val ownRaw = ownRawRecordByRevision[evidence.conclusionRevisionId]
                evidence.sourceRawRecordId != ownRaw
            }
            val contradictions = externalEvidence.count { it.relationship == Relationship.CONTRADICTS }
            val supports = externalEvidence.size
            val sourceRawRecordIds = (
                revisionIds.mapNotNull(ownRawRecordByRevision::get) +
                    externalEvidence.map { it.sourceRawRecordId }
            ).distinct().sorted()
            val outcomeIds = revisionIds.flatMap { revisionId ->
                decisionsByRevision[revisionId].orEmpty().flatMap { decision ->
                    if (!decision.choice.isNullOrBlank()) outcomesByDecision[decision.id].orEmpty().map { it.id }
                    else emptyList()
                }
            }
            val revisionTimes = revisionIds.mapNotNull { revisions[it]?.createdAt }
            val graphTimes = revisionTimes +
                currentEvidence.map { it.createdAt } +
                themeLinkSubset.map { it.createdAt } +
                revisionIds.flatMap { revisionId ->
                    decisionsByRevision[revisionId].orEmpty().flatMap { decision ->
                        outcomesByDecision[decision.id].orEmpty().map { it.createdAt }
                    }
                }
            val state = when {
                revisionIds.isEmpty() && scopeType == com.echomind.domain.model.CoverageScopeType.THEME ->
                    com.echomind.domain.model.EvidenceState.EMPTY_THEME
                contradictions > 0 -> com.echomind.domain.model.EvidenceState.CONTRADICTED
                supports > 0 -> com.echomind.domain.model.EvidenceState.SUPPORTED
                else -> com.echomind.domain.model.EvidenceState.NO_EXTERNAL_EVIDENCE
            }
            return ThemeCandidate(
                themeId = themeId,
                name = name,
                conclusionCount = revisionIds.size,
                evidenceCount = supports,
                contradictionCount = contradictions,
                scopeType = scopeType,
                scopeId = scopeId,
                currentRevisionIds = revisionIds.sorted(),
                evidenceState = state,
                hasOutcome = outcomeIds.isNotEmpty(),
                lastGraphChangeAt = graphTimes.maxOrNull() ?: now,
                relevantLinkIds = (currentEvidence.map { it.id } + themeLinkSubset.map { it.id }).distinct(),
                relevantSourceRevisionKeys = externalEvidence.map { evidence ->
                    val version = revisions[evidence.conclusionRevisionId]?.version ?: 0
                    "${evidence.sourceRawRecordId}:${evidence.relationship}:$version"
                },
                sourceRawRecordIds = sourceRawRecordIds,
                relevantOutcomeIds = outcomeIds.sorted(),
                unfinishedSince = unfinishedSince,
                navigationTarget = navigationTarget
            )
        }

        val themeLinksByTheme = themeLinks.groupBy { it.themeId }
        val themeCandidates = themes.map { theme ->
            val links = themeLinksByTheme[theme.id].orEmpty()
            val revisionIds = links.map { it.conclusionRevisionId }
                .filter { it in currentRevisionIds }
                .distinct()
            candidate(
                themeId = theme.id,
                name = theme.name,
                scopeType = com.echomind.domain.model.CoverageScopeType.THEME,
                scopeId = theme.id,
                revisionIds = revisionIds,
                themeLinkSubset = links.filter { it.conclusionRevisionId in revisionIds }
            )
        }
        val unthemedCandidates = conclusions.mapNotNull { conclusion ->
            val revisionId = conclusion.currentRevisionId ?: return@mapNotNull null
            if (revisionId in themedCurrentRevisionIds) return@mapNotNull null
            val entryId = rawRecords[conclusion.rawRecordId]?.legacyEntryId ?: return@mapNotNull null
            candidate(
                themeId = 0L,
                name = revisions[revisionId]?.text?.trim()?.take(80)
                    .takeUnless { it.isNullOrBlank() }
                    ?: "Unthemed reflection",
                scopeType = com.echomind.domain.model.CoverageScopeType.UNTHEMED,
                scopeId = conclusion.id,
                revisionIds = listOf(revisionId),
                themeLinkSubset = emptyList(),
                navigationTarget = HomeNavigationTarget.Entry(entryId)
            )
        }
        val unfinishedCandidates = knowledgeDao.getProposedHypotheses()
            .map { hypothesis ->
                candidate(
                    themeId = 0L,
                    name = "",
                    scopeType = com.echomind.domain.model.CoverageScopeType.UNTHEMED,
                    scopeId = -hypothesis.id,
                    revisionIds = emptyList(),
                    themeLinkSubset = emptyList(),
                    unfinishedSince = hypothesis.createdAt,
                    navigationTarget = HomeNavigationTarget.ReflectionProposal(hypothesis.id)
                ).copy(relevantSourceRevisionKeys = listOf("h:${hypothesis.id}"))
            }
        val dispositions = knowledgeDao.getAllHomeCardDispositions()
        val suppressedKeys = dispositions.filter { disposition ->
            disposition.dismissedAt != null || disposition.postponedUntil?.let { it > now } == true
        }.map { it.cardKey }.toSet()
        return HomeRelevanceBuilder.build(
            themeCandidates + unthemedCandidates + unfinishedCandidates,
            now = now,
            suppressedCardKeys = suppressedKeys
        ).copy(legacySuppressionReset = legacySuppressionReset)
    }

    suspend fun dismissCard(card: HomeCard) {
        knowledgeDao.upsertHomeCardDisposition(card.toDisposition(dismissedAt = System.currentTimeMillis()))
    }

    suspend fun postponeCard(card: HomeCard, until: Long) {
        knowledgeDao.upsertHomeCardDisposition(card.toDisposition(postponedUntil = until))
    }

    suspend fun restoreCard(cardKey: String) {
        knowledgeDao.deleteHomeCardDisposition(cardKey)
    }

    suspend fun getCardDispositions(): List<HomeCardDispositionEntity> =
        knowledgeDao.getAllHomeCardDispositions()

    private fun HomeCard.toDisposition(
        dismissedAt: Long? = null,
        postponedUntil: Long? = null
    ) = HomeCardDispositionEntity(
        cardKey = cardKey,
        cardType = type.name,
        scopeType = scopeType.name,
        scopeId = scopeId,
        dismissedAt = dismissedAt,
        postponedUntil = postponedUntil,
        createdAt = System.currentTimeMillis()
    )

    suspend fun search(query: String): List<KnowledgeSearchResult> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return emptyList()
        val escaped = escapeLikeQuery(trimmed)

        val rawResults = knowledgeDao.searchRawRecords(escaped).map { raw ->
            KnowledgeSearchResult.RawRecord(
                rawRecordId = raw.id,
                entryId = raw.legacyEntryId,
                text = raw.originalText,
                createdAt = raw.createdAt
            )
        }
        val conclusionResults = knowledgeDao.searchRevisions(escaped).mapNotNull { revision ->
            val conclusion = knowledgeDao.getConclusionById(revision.conclusionId) ?: return@mapNotNull null
            val raw = knowledgeDao.getRawRecordById(conclusion.rawRecordId)
            KnowledgeSearchResult.Conclusion(
                conclusionId = conclusion.id,
                revisionId = revision.id,
                entryId = raw?.legacyEntryId,
                text = revision.text,
                revisionVersion = revision.version,
                createdAt = revision.createdAt,
                isCurrent = conclusion.currentRevisionId == revision.id
            )
        }
        val themeResults = knowledgeDao.searchThemes(escaped).map { theme ->
            KnowledgeSearchResult.Theme(
                themeId = theme.id,
                text = theme.name,
                conclusionCount = knowledgeDao.getConfirmedLinksForTheme(theme.id).size
            )
        }
        return rawResults + conclusionResults + themeResults
    }

    private fun escapeLikeQuery(value: String): String = value
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")
}
