package net.nemerosa.ontrack.acceptance

import net.nemerosa.ontrack.acceptance.browser.pages.APIPage
import net.nemerosa.ontrack.acceptance.browser.pages.BuildPage
import net.nemerosa.ontrack.acceptance.browser.pages.HomePage
import net.nemerosa.ontrack.acceptance.browser.pages.ProjectPage
import net.nemerosa.ontrack.acceptance.support.AcceptanceTest
import net.nemerosa.ontrack.acceptance.support.AcceptanceTestSuite
import org.junit.Test

import static net.nemerosa.ontrack.acceptance.steps.BasicSteps.loginAsAdmin
import static net.nemerosa.ontrack.test.TestUtils.uid
import static org.junit.Assert.assertEquals

/**
 * Basic GUI tests
 */
@AcceptanceTestSuite
@AcceptanceTest([AcceptanceTestContext.PRODUCTION, AcceptanceTestContext.SMOKE, AcceptanceTestContext.BROWSER_TEST])
class ACCBrowserBasic extends AcceptanceTestClient {

    @Test
    void 'Project API page must be accessible'() {
        browser { browser ->
            withProject { id, name ->
                // Goes to the home page and logs in browser ->
                HomePage home = loginAsAdmin(browser)
                // Goes to the project
                ProjectPage projectPage = home.goToProject(name)
                // Goes to the API page
                APIPage apiPage = projectPage.goToAPI()
                // Gets the link of the page
                def link = apiPage.apiLink
                // Checks the link
                assert link == "/structure/projects/${id}"
            }
        }
    }

    @Test
    void 'Build page'() {
        def projectName = uid("P")
        ontrack.project(projectName) {
            branch("master") {
                def build = build("1")

                browser { browser ->
                    // Logs in
                    loginAsAdmin(browser)
                    // Goes to the build page which must contains the link
                    BuildPage buildPage = goTo(BuildPage, [id: build.id])
                    // Checks the title
                    assertEquals(
                            "Build 1",
                            buildPage.viewTitle
                    )
                }
            }
        }
    }

}
