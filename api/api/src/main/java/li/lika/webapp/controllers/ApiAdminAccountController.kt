package li.lika.webapp.controllers

import li.lika.common.models.Account
import li.lika.common.models.AccountCreator
import li.lika.core.services.AccountService
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

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
