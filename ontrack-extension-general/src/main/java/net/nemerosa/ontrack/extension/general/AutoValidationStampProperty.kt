package net.nemerosa.ontrack.extension.general

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty


data class AutoValidationStampProperty
@JsonCreator
constructor(
        @JsonProperty("autoCreate")
        val isAutoCreate: Boolean,
        @JsonProperty("autoCreateIfNotPredefined")
        val isAutoCreateIfNotPredefined: Boolean
) {
    constructor(isAutoCreate: Boolean) : this(isAutoCreate, false)
}