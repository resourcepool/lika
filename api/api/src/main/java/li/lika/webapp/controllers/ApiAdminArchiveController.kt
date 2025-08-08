package li.lika.webapp.controllers

import li.lika.common.models.DatabaseArchive
import li.lika.core.services.ArchiveService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RequestMapping("/api/admin/archive")
@RestController
class ApiAdminArchiveController(private val archiveService: ArchiveService) {

    @PostMapping("/export")
    fun exportArchive(): DatabaseArchive {
        return archiveService.exportArchive()
    }

    @PostMapping("/import")
    fun importArchive(@RequestBody archive: DatabaseArchive) {
        archiveService.importArchive(archive)
    }

}
