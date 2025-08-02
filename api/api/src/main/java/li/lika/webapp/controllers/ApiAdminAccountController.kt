package li.lika.webapp.controllers

import li.lika.common.models.Account
import li.lika.common.models.AccountCreator
import li.lika.core.services.AccountService
import org.springframework.web.bind.annotation.*

@RequestMapping("/api/admin/accounts")
@RestController
class ApiAdminAccountController(private val accountService: AccountService) {

    @GetMapping("/{id}")
    fun findOne(@PathVariable id: String): Account? {
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

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: String) {
        accountService.deleteOne(id)
    }
}
