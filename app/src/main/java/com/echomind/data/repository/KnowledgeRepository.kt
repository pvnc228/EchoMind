package com.echomind.data.repository

import androidx.room.withTransaction
import com.echomind.data.local.AppDatabase
import com.echomind.data.local.dao.KnowledgeDao
import com.echomind.data.local.entity.EvidenceLinkEntity
import com.echomind.data.local.entity.ThemeEntity
import com.echomind.data.local.entity.ThemeLinkEntity
import com.echomind.domain.model.RelatedRecord
import com.echomind.domain.model.Relationship
import com.echomind.domain.model.Theme
import com.echomind.domain.model.ThemeConclusion
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KnowledgeRepository @Inject constructor(
    private val database: AppDatabase,
    private val knowledgeDao: KnowledgeDao
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
        if (knowledgeDao.getConfirmedThemeLink(themeId, revisionId) != null) return
        knowledgeDao.insertThemeLink(
            ThemeLinkEntity(
                themeId = themeId,
                conclusionRevisionId = revisionId,
                confirmed = true,
                createdAt = System.currentTimeMillis()
            )
        )
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
                revisionVersion = revision.version
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
        if (knowledgeDao.getEvidenceLinkForRevisionAndSource(revisionId, sourceRecordId) != null) return
        knowledgeDao.insertEvidenceLink(
            EvidenceLinkEntity(
                conclusionRevisionId = revisionId,
                sourceRawRecordId = sourceRecordId,
                relationship = relationship,
                status = "confirmed"
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
            if (link.sourceRawRecordId == ownRawRecordId) return@mapNotNull null
            val raw = knowledgeDao.getRawRecordById(link.sourceRawRecordId) ?: return@mapNotNull null
            RelatedRecord(
                rawRecordId = raw.id,
                relationship = link.relationship,
                sourceText = raw.originalText,
                recordedAt = raw.createdAt
            )
        }
    }

    suspend fun getLinkCandidates(excludeRawRecordId: Long): List<RelatedRecord> {
        val rawIds = buildSet {
            for (conclusion in knowledgeDao.getAllConclusions()) {
                if (conclusion.rawRecordId != excludeRawRecordId) add(conclusion.rawRecordId)
            }
        }
        return rawIds.mapNotNull { id ->
            val raw = knowledgeDao.getRawRecordById(id) ?: return@mapNotNull null
            RelatedRecord(
                rawRecordId = raw.id,
                relationship = "",
                sourceText = raw.originalText,
                recordedAt = raw.createdAt
            )
        }
    }
}
