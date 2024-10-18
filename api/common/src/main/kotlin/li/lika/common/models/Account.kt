package li.lika.common.models

data class Account(val id: String?,
                   val name: String = "",
                   val global: Boolean = false,
                   val defaultAccessToken: String,
                   val defaultPublicReadAccessToken: String) {
}
