package net.nemerosa.ontrack.kdsl.test.support

import net.nemerosa.ontrack.dsl.Branch
import net.nemerosa.ontrack.dsl.build
import net.nemerosa.ontrack.dsl.promotionLevel
import net.nemerosa.ontrack.dsl.validationStamp

fun AbstractKdslTest.withTestBranch(code: (Branch) -> Unit) {
    branch {
        // Promotions
        promotionLevel("COPPER", "Copper promotion")
        promotionLevel("BRONZE", "Bronze promotion")
        promotionLevel("GOLD", "Gold promotion")
        // Validation stamps
        validationStamp("SMOKE")
        validationStamp("REGRESSION")
        // Builds and their promotions
        build("1") {
            promote("GOLD")
        }
        build("2") {
            promote("BRONZE")
        }
        build("3") {
            promote("COPPER")
        }
        // Runs the code for this branch
        code(this)
    }
}