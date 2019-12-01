package net.nemerosa.ontrack.ui.graphql.core

import graphql.schema.idl.TypeRuntimeWiring
import net.nemerosa.ontrack.graphql.support.GraphqlUtils
import net.nemerosa.ontrack.model.structure.PromotionLevel
import net.nemerosa.ontrack.model.structure.PromotionRun
import net.nemerosa.ontrack.model.structure.StructureService
import net.nemerosa.ontrack.ui.graphql.support.TypeFieldContributor
import org.springframework.stereotype.Component

@Component
class TypePromotionLevel(
        typeFieldContributors: List<TypeFieldContributor>,
        private val structureService: StructureService
) : AbstractTypeProjectEntity<PromotionLevel>(PromotionLevel::class, typeFieldContributors) {
    override fun dataFetchers(builder: TypeRuntimeWiring.Builder) {
        super.dataFetchers(builder)

        builder.dataFetcher("promotionRuns") { environment ->
            val promotionLevel: PromotionLevel = environment.getSource()
            // Gets all the promotion runs
            val promotionRuns: List<PromotionRun> = structureService.getPromotionRunsForPromotionLevel(promotionLevel.id)
            // Filters according to the arguments
            GraphqlUtils.stdListArgumentsFilter(promotionRuns, environment)
        }
    }
}
