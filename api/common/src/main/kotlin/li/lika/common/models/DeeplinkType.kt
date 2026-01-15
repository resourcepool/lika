package li.lika.common.models

enum class DeeplinkType(val value: String) {
    SIMPLE("simple"),
    WITH_LANDING("landing");

    companion object {
        fun read(type: String?): DeeplinkType {
            return DeeplinkType.entries.find { it.value == type }?: SIMPLE
        }
    }
}
