package li.lika.common.models

import com.fasterxml.jackson.annotation.JsonIgnore

data class Domain(var id: String? = null,
                  var standard: Boolean = false,
                  /** Owning account of Lika < 0.7.0, only read by the migration to API keys */
                  @get:JsonIgnore
                  var ownerId: String? = null,
                  var host: String = "",
                  var apiBaseUrl: String = "",
                  var landingBaseUrl: String? = null) {
}
