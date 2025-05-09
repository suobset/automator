package com.skushagra.selfselect

import com.fasterxml.jackson.annotation.JsonProperty

// This file defines all valid YAML options and what they do.
data class ActorAction(
    @JsonProperty("action") val action: String
)
