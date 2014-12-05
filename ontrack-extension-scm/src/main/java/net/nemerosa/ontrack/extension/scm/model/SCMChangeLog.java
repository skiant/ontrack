package net.nemerosa.ontrack.extension.scm.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.nemerosa.ontrack.model.buildfilter.BuildDiff;
import net.nemerosa.ontrack.model.structure.BuildView;
import net.nemerosa.ontrack.model.structure.Project;

import java.util.UUID;

/**
 * @param <T> Type of SCM data associated with a build
 */
@EqualsAndHashCode(callSuper = false)
@Data
public class SCMChangeLog<T> extends BuildDiff {

    private final String uuid;
    private final SCMBuildView<T> scmBuildFrom;
    private final SCMBuildView<T> scmBuildTo;

    protected SCMChangeLog(String uuid, Project project, SCMBuildView<T> scmBuildFrom, SCMBuildView<T> scmBuildTo) {
        super(project);
        this.uuid = uuid;
        this.scmBuildFrom = scmBuildFrom;
        this.scmBuildTo = scmBuildTo;
    }

    @Override
    public BuildView getFrom() {
        return scmBuildFrom.getBuildView();
    }

    @Override
    public BuildView getTo() {
        return scmBuildTo.getBuildView();
    }

    @JsonIgnore
    public boolean isSameBranch() {
        return getFrom().getBuild().getBranch().id() == getTo().getBuild().getBranch().id();
    }

    public static <S, T> SCMChangeLog<T> of(Project project, SCMBuildView<T> from, SCMBuildView<T> to) {
        return new SCMChangeLog<>(
                UUID.randomUUID().toString(),
                project,
                from,
                to
        );
    }

}
