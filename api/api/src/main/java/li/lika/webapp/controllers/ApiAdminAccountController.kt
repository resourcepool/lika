package li.lika.webapp.controllers

import li.lika.common.models.*
import li.lika.core.services.AccountService
import org.springframework.web.bind.annotation.*

@RequestMapping("/api/admin/accounts")
@RestController
class ApiAdminAccountController(private val accountService: AccountService) {

    @GetMapping("/{id}")
    fun findOne(@PathVariable id: String): Account {
        return accountService.findOne(id)
    }

    @GetMapping
    fun findAll(): List<Account> {
        return accountService.findAll()
    }

    @PostMapping
    fun create(@RequestBody req: AccountCreator): Account {
        return accountService.create(req)
    }

    @PatchMapping("/{id}")
    fun rename(@PathVariable id: String, @RequestBody req: AccountUpdate): Account {
        return accountService.rename(id, req.name)
    }

    /**
     * Replaces one of the account's tokens. The previous token stops working immediately.
     */
    @PostMapping("/{id}/regenerate")
    fun regenerate(@PathVariable id: String, @RequestBody req: TokenRegeneration): Account {
        return accountService.regenerateToken(id, req.token)
    }

    /**
     * Replaces the set of domains granted to the account.
     */
    @PutMapping("/{id}/domains")
    fun updateDomains(@PathVariable id: String, @RequestBody req: AccountDomainsUpdate): Account {
        return accountService.updateDomains(id, req.domainIds)
    }

    /**
     * Fails with 409 when deleting the last global account.
     */
    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: String) {
        accountService.deleteOne(id)
    }
}
