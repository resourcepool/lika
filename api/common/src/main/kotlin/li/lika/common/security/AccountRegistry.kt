package li.lika.common.security

import li.lika.common.models.Account

interface AccountRegistry {
    fun findByAccessToken(accessToken: String): Account?
}
