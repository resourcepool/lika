package li.lika.common.models

import com.fasterxml.jackson.annotation.JsonProperty

enum class LinkBatchAction {
    /** Deletes the links */
    @JsonProperty("delete") DELETE,
    /** Adds or replaces the entries of [LinkBatchRequest.metadata], keeping the others */
    @JsonProperty("set_metadata") SET_METADATA,
    /** Removes the entries named by [LinkBatchRequest.keys] */
    @JsonProperty("remove_metadata") REMOVE_METADATA,
    /** Sets [LinkBatchRequest.expiryDate], or removes the expiry when it is null */
    @JsonProperty("set_expiry") SET_EXPIRY
}

/**
 * One operation on several links. All of them change, or none: every link must exist and be editable by the caller.
 */
data class LinkBatchRequest(
    var ids: List<String> = emptyList(),
    var action: LinkBatchAction? = null,
    var metadata: Map<String, String>? = null,
    var keys: List<String>? = null,
    /** ISO date, e.g. 2026-12-31 */
    var expiryDate: String? = null
) {
    companion object {
        const val MAX_LINKS = 500
    }
}

/** @param links the links after the operation; empty for a deletion */
data class LinkBatchResult(val count: Int, val links: List<Deeplink>)

/** Values in use for a metadata key, for filters and suggestions */
data class MetadataKeyValues(val key: String, val values: List<String>, val linkCount: Int)
