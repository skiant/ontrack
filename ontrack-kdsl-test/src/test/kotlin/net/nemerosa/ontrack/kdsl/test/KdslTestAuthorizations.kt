package net.nemerosa.ontrack.kdsl.test

import net.nemerosa.ontrack.kdsl.model.branch
import org.junit.Test
import kotlin.test.assertNull

class KdslTestAuthorizations : AbstractKdslTest() {

    @Test
    fun `Branch not found before not authorised`() {
        project {
            branch {
                // Removing any "Grant view to all"
                withNotGrantProjectViewToAll {
                    // Switch to anonymous client
                    val ontrack = ontrack.asAnonymous()
                    // Branch cannot be found
                    val branch = ontrack.branch(this.project.name, this.name)
                    assertNull(branch, "Branch cannot be found")
                }
            }
        }
    }

}