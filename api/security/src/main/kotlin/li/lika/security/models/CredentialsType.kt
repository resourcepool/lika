package li.lika.security.models

enum class CredentialsType(val value: String) {
    AUTH_HEADER("auth-header"), API_KEY_HEADER("api-key-header"), QUERY_PARAM("query-param");
}
