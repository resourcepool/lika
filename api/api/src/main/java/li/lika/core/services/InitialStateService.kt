package li.lika.core.services

import jakarta.annotation.PostConstruct
import li.lika.core.services.auth.AccessMigrationService
import li.lika.core.services.auth.SessionService
import org.springframework.stereotype.Service

@Service
class InitialStateService(
    val domainService: DomainService,
    val deeplinkService: DeeplinkService,
    val migration: AccessMigrationService,
    val sessions: SessionService
) {
    @PostConstruct
    fun init() {
        domainService.init()
        migration.migrate()
        deeplinkService.init()
        sessions.announceSetup()
    }
}
