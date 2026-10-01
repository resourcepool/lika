package li.lika.common.models

data class DomainCreator(var host: String = "", var apiBaseUrl: String = "", var landingBaseUrl: String = "")

data class DomainUpdate(var apiBaseUrl: String? = null, var landingBaseUrl: String? = null)
