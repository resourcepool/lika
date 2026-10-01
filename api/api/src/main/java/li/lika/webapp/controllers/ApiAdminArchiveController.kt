package li.lika.webapp.controllers

import li.lika.common.models.DatabaseArchive
import li.lika.common.models.ImportConflictMode
import li.lika.common.models.ImportReport
import li.lika.core.services.ArchiveService
import org.springframework.web.bind.annotation.*

@RequestMapping("/api/admin/archive")
@RestController
class ApiAdminArchiveController(private val archiveService: ArchiveService) {

    @PostMapping("/export")
    fun exportArchive(): DatabaseArchive {
        return archiveService.exportArchive()
    }

    /**
     * Reads v1 and v2 archives.
     *
     * @param dryRun only reports what would be created, updated or skipped
     * @param conflict what to do with accounts, domains and links which already exist
     */
    @PostMapping("/import")
    fun importArchive(
        @RequestBody archive: DatabaseArchive,
        @RequestParam(defaultValue = "false") dryRun: Boolean,
        @RequestParam(defaultValue = "skip") conflict: String
    ): ImportReport {
        val mode = when (conflict.lowercase()) {
            "skip" -> ImportConflictMode.SKIP
            "overwrite" -> ImportConflictMode.OVERWRITE
            else -> throw IllegalArgumentException("conflict must be skip or overwrite")
        }
        return archiveService.importArchive(archive, mode, dryRun)
    }

}
