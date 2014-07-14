package net.nemerosa.ontrack.service.security;

import net.nemerosa.ontrack.model.security.*;
import net.nemerosa.ontrack.model.support.StartupService;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Management of the roles and functions.
 */
@Service
public class RolesServiceImpl implements RolesService, StartupService {

    /**
     * Index of global roles
     */
    private final Map<String, GlobalRole> globalRoles = new LinkedHashMap<>();

    /**
     * Index of project roles
     */
    private final Map<String, ProjectRole> projectRoles = new LinkedHashMap<>();

    @Override
    public List<GlobalRole> getGlobalRoles() {
        return new ArrayList<>(globalRoles.values());
    }

    @Override
    public Optional<GlobalRole> getGlobalRole(String id) {
        return Optional.ofNullable(globalRoles.get(id));
    }

    @Override
    public List<ProjectRole> getProjectRoles() {
        return new ArrayList<>(projectRoles.values());
    }

    @Override
    public Optional<ProjectRole> getProjectRole(String id) {
        return Optional.ofNullable(projectRoles.get(id));
    }

    @Override
    public List<Class<? extends GlobalFunction>> getGlobalFunctions() {
        return defaultGlobalFunctions;
    }

    @Override
    public List<Class<? extends ProjectFunction>> getProjectFunctions() {
        return defaultProjectFunctions;
    }

    @Override
    public String getName() {
        return "Roles";
    }

    @Override
    public int startupOrder() {
        return 50;
    }

    @Override
    public void start() {
        // Global roles
        initGlobalRoles();
        // Project roles
        initProjectRoles();
    }

    private void initProjectRoles() {

        // Owner
        register("OWNER", "Project owner",
                "The project owner is allowed to all functions in a project, but for its deletion.",
                getProjectFunctions().stream().filter(t -> !ProjectDelete.class.isAssignableFrom(t)).collect(Collectors.toList())
        );

        // TODO Validation manager

        // TODO Promoter

    }

    private void register(String id, String name, String description, List<Class<? extends ProjectFunction>> projectFunctions) {
        register(new ProjectRole(
                id,
                name,
                description,
                new LinkedHashSet<>(projectFunctions)
        ));
    }

    private void register(ProjectRole projectRole) {
        projectRoles.put(projectRole.getId(), projectRole);

    }

    private void initGlobalRoles() {

        // Administrator
        register("ADMINISTRATOR", "Administrator",
                "An administrator is allowed to do everything in the application.",
                getGlobalFunctions(),
                getProjectFunctions());

        // Controller
        register("CONTROLLER", "Controller",
                "A controller, is allowed to create builds, promotion runs and validation runs. This role is " +
                        "typically granted to continuous integration tools.",
                Arrays.asList(
                        // No global function
                ),
                Arrays.asList(
                        BuildCreate.class,
                        PromotionRunCreate.class,
                        ValidationRunCreate.class
                )
        );

    }

    private void register(String id, String name, String description, List<Class<? extends GlobalFunction>> globalFunctions, List<Class<? extends ProjectFunction>> projectFunctions) {
        register(new GlobalRole(
                id,
                name,
                description,
                new LinkedHashSet<>(globalFunctions),
                new LinkedHashSet<>(projectFunctions)
        ));
    }

    private void register(GlobalRole role) {
        globalRoles.put(role.getId(), role);
    }
}
