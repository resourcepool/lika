package li.lika.common.models

data class AccountCreator(var name: String = "", var global: Boolean = false, var domainIds: Set<String> = emptySet())

data class AccountUpdate(var name: String = "")

data class AccountDomainsUpdate(var domainIds: Set<String> = emptySet())

enum class TokenKind {
    @com.fasterxml.jackson.annotation.JsonProperty("read_write") READ_WRITE,
    @com.fasterxml.jackson.annotation.JsonProperty("read") READ
}

data class TokenRegeneration(var token: TokenKind = TokenKind.READ_WRITE)
