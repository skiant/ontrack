package net.nemerosa.ontrack.kdsl.model

import com.fasterxml.jackson.databind.JsonNode
import net.nemerosa.ontrack.dsl.Ontrack
import net.nemerosa.ontrack.dsl.Resource
import net.nemerosa.ontrack.kdsl.client.OntrackConnector
import net.nemerosa.ontrack.kdsl.core.Connector

abstract class KDSLResource(
        protected val json: JsonNode,
        ontrackConnector: OntrackConnector
) : Connector(ontrackConnector), Resource {

    protected val ontrack: Ontrack by lazy {
        KDSLOntrack(ontrackConnector)
    }

}
