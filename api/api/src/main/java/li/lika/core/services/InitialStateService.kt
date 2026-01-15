package li.lika.core.services

import jakarta.annotation.PostConstruct
import li.lika.security.models.AccessTokenAuthentication
import li.lika.security.props.SecurityProperties
import org.springframework.stereotype.Service

@Service
class InitialStateService(val accountService: AccountService, val domainService: DomainService, val deeplinkService: DeeplinkService, val securityProperties: SecurityProperties) {
    @PostConstruct
    fun init() {
        accountService.init()
        domainService.init()
        val adminAccount = accountService.findByAccessToken(securityProperties.defaultGlobalAccessToken!!)!!
        deeplinkService.init(AccessTokenAuthentication.ofReadWriteAccessToken(adminAccount, adminAccount.defaultAccessToken))
    }
}
