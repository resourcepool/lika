package li.lika.common.models

data class Domain(var id: String? = null,
                  var standard: Boolean = false,
                  var ownerId: String? = null,
                  var host: String = "",
                  var apiBaseUrl: String = "",
                  var landingBaseUrl: String? = null) {
}
