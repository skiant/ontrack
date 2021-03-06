package net.nemerosa.ontrack.extension.svn.service;

import net.nemerosa.ontrack.common.Time;
import net.nemerosa.ontrack.extension.api.model.BuildDiffRequest;
import net.nemerosa.ontrack.extension.issues.export.ExportFormat;
import net.nemerosa.ontrack.extension.issues.model.ConfiguredIssueService;
import net.nemerosa.ontrack.extension.issues.model.Issue;
import net.nemerosa.ontrack.extension.issues.model.IssueServiceConfigurationRepresentation;
import net.nemerosa.ontrack.extension.scm.model.SCMBuildView;
import net.nemerosa.ontrack.extension.scm.service.AbstractSCMChangeLogService;
import net.nemerosa.ontrack.extension.svn.client.SVNClient;
import net.nemerosa.ontrack.extension.svn.db.SVNIssueRevisionDao;
import net.nemerosa.ontrack.extension.svn.db.SVNRepository;
import net.nemerosa.ontrack.extension.svn.model.*;
import net.nemerosa.ontrack.extension.svn.property.SVNBranchConfigurationProperty;
import net.nemerosa.ontrack.extension.svn.property.SVNBranchConfigurationPropertyType;
import net.nemerosa.ontrack.extension.svn.property.SVNProjectConfigurationProperty;
import net.nemerosa.ontrack.extension.svn.property.SVNProjectConfigurationPropertyType;
import net.nemerosa.ontrack.extension.svn.support.ConfiguredBuildSvnRevisionLink;
import net.nemerosa.ontrack.extension.svn.support.SVNLogEntryCollector;
import net.nemerosa.ontrack.extension.svn.support.SVNUtils;
import net.nemerosa.ontrack.model.structure.*;
import net.nemerosa.ontrack.tx.Transaction;
import net.nemerosa.ontrack.tx.TransactionService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.wc.SVNRevision;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class SVNChangeLogServiceImpl extends AbstractSCMChangeLogService implements SVNChangeLogService {

    private final BuildSvnRevisionLinkService buildSvnRevisionLinkService;
    private final SVNIssueRevisionDao issueRevisionDao;
    private final SVNService svnService;
    private final SVNClient svnClient;
    private final TransactionService transactionService;

    @Autowired
    public SVNChangeLogServiceImpl(
            StructureService structureService,
            PropertyService propertyService,
            BuildSvnRevisionLinkService buildSvnRevisionLinkService,
            SVNIssueRevisionDao issueRevisionDao,
            SVNService svnService,
            SVNClient svnClient,
            TransactionService transactionService) {
        super(structureService, propertyService);
        this.buildSvnRevisionLinkService = buildSvnRevisionLinkService;
        this.issueRevisionDao = issueRevisionDao;
        this.svnService = svnService;
        this.svnClient = svnClient;
        this.transactionService = transactionService;
    }

    @Override
    @Transactional
    public SVNChangeLog changeLog(BuildDiffRequest request) {
        try (Transaction ignored = transactionService.start()) {
            // Gets the two builds
            Build buildFrom = structureService.getBuild(request.getFrom());
            Build buildTo = structureService.getBuild(request.getTo());
            // Ordering of builds
            if (buildFrom.id() > buildTo.id()) {
                Build t = buildFrom;
                buildFrom = buildTo;
                buildTo = t;
            }
            // Gets the two branches, for each build
            Branch branchFrom = buildFrom.getBranch();
            Branch branchTo = buildTo.getBranch();
            // Checks the branch is the same
            if (branchFrom.id() != branchTo.id()) {
                throw new SVNChangeLogDifferentBranchException();
            }
            SVNRepository svnRepository = getSVNRepository(branchFrom);
            return new SVNChangeLog(
                    UUID.randomUUID().toString(),
                    branchFrom.getProject(),
                    svnRepository,
                    getSCMBuildView(svnRepository, buildFrom.getId()),
                    getSCMBuildView(svnRepository, buildTo.getId())
            );
        }
    }

    @Override
    @Transactional
    public SVNChangeLogRevisions getChangeLogRevisions(SVNChangeLog changeLog) {

        // Reference
        Collection<SVNChangeLogReference> references = changeLog.getChangeLogReferences();

        // No difference?
        if (references.isEmpty()) {
            return SVNChangeLogRevisions.none();
        }

        // SVN transaction
        try (Transaction ignored = transactionService.start()) {
            List<SVNChangeLogRevision> revisions = new ArrayList<>();
            for (SVNChangeLogReference reference : references) {
                if (!reference.isNone()) {
                    SVNRepository repository = changeLog.getRepository();
                    // List of log entries
                    SVNLogEntryCollector logEntryCollector = new SVNLogEntryCollector();
                    // SVN change log
                    svnClient.log(
                            repository,
                            SVNUtils.toURL(repository.getUrl(reference.getPath())),
                            SVNRevision.create(reference.getEnd()),
                            SVNRevision.create(reference.getStart() + 1),
                            SVNRevision.create(reference.getEnd()),
                            true, // Stops on copy
                            false, // No path discovering (yet)
                            0L, // no limit
                            true, // Includes merged revisions
                            logEntryCollector
                    );
                    // Loops through all SVN log entries, taking the merged revisions into account
                    int level = 0;
                    for (SVNLogEntry svnEntry : logEntryCollector.getEntries()) {
                        long revision = svnEntry.getRevision();
                        if (SVNRevision.isValidRevisionNumber(revision)) {
                            // Conversion
                            SVNChangeLogRevision entry = createChangeLogRevision(repository, reference.getPath(), level, svnEntry);
                            // Adds it to the list
                            revisions.add(entry);
                            // New parent?
                            if (svnEntry.hasChildren()) {
                                level++;
                            }
                        } else {
                            level--;
                        }
                    }
                }
            }
            // Sorting the revisions
            Collections.sort(
                    revisions,
                    // From the newest revision to the oldest
                    (o1, o2) -> Long.compare(o2.getRevision(), o1.getRevision())
            );
            // OK
            return new SVNChangeLogRevisions(revisions);
        }
    }

    @Override
    @Transactional
    public SVNChangeLogIssues getChangeLogIssues(SVNChangeLog changeLog) {
        // Revisions must have been loaded first
        if (changeLog.getRevisions() == null) {
            changeLog.withRevisions(getChangeLogRevisions(changeLog));
        }
        // In a transaction
        try (Transaction ignored = transactionService.start()) {
            // Repository
            SVNRepository repository = changeLog.getRepository();
            // Index of issues, sorted by keys
            Map<String, SVNChangeLogIssue> issues = new TreeMap<>();
            // For all revisions in this revision log
            for (SVNChangeLogRevision changeLogRevision : changeLog.getRevisions().getList()) {
                long revision = changeLogRevision.getRevision();
                collectIssuesForRevision(repository, issues, revision);
            }
            // List of issues
            List<SVNChangeLogIssue> issuesList = new ArrayList<>(issues.values());
            // Validations
            validateIssues(issuesList, changeLog);
            // Issues link
            IssueServiceConfigurationRepresentation issueServiceConfiguration = null;
            String allIssuesLink = "";
            ConfiguredIssueService configuredIssueService = repository.getConfiguredIssueService();
            if (configuredIssueService != null) {
                issueServiceConfiguration = configuredIssueService.getIssueServiceConfigurationRepresentation();
                allIssuesLink = configuredIssueService.getLinkForAllIssues(
                        issuesList.stream().map(SVNChangeLogIssue::getIssue).collect(Collectors.toList())
                );
            }
            // OK
            return new SVNChangeLogIssues(allIssuesLink, issueServiceConfiguration, issuesList);

        }
    }

    @Override
    @Transactional
    public SVNChangeLogFiles getChangeLogFiles(SVNChangeLog changeLog) {
        // Revisions must have been loaded first
        if (changeLog.getRevisions() == null) {
            changeLog.withRevisions(getChangeLogRevisions(changeLog));
        }
        // In a transaction
        try (Transaction ignored = transactionService.start()) {
            // Index of files, indexed by path
            Map<String, SVNChangeLogFile> files = new TreeMap<>();
            // For each revision
            // Takes into account only the unmerged revisions
            changeLog.getRevisions().getList().stream()
                    .filter(changeLogRevision -> changeLogRevision.getLevel() == 0)
                    .forEach(changeLogRevision -> {
                        long revision = changeLogRevision.getRevision();
                        collectFilesForRevision(changeLog.getRepository(), files, revision);
                    });
            // List of files
            return new SVNChangeLogFiles(new ArrayList<>(files.values()));
        }
    }

    private void collectFilesForRevision(SVNRepository repository, Map<String, SVNChangeLogFile> files, long revision) {
        SVNRevisionPaths revisionPaths = svnService.getRevisionPaths(repository, revision);
        for (SVNRevisionPath revisionPath : revisionPaths.getPaths()) {
            String path = revisionPath.getPath();
            // Existing file entry?
            SVNChangeLogFile changeLogFile = files.get(path);
            if (changeLogFile == null) {
                changeLogFile = new SVNChangeLogFile(path, repository.getPathBrowsingURL(path));
                files.put(path, changeLogFile);
            }
            // Adds the revision and the type
            SVNChangeLogFileChange change = new SVNChangeLogFileChange(
                    revisionPaths.getInfo(),
                    revisionPath.getChangeType(),
                    repository.getFileChangeBrowsingURL(path, revisionPaths.getInfo().getRevision())
            );
            changeLogFile.addChange(change);
        }
    }

    private void collectIssuesForRevision(SVNRepository repository, Map<String, SVNChangeLogIssue> issues, long revision) {
        // Gets all issues attached to this revision
        List<String> issueKeys = issueRevisionDao.findIssuesByRevision(repository.getId(), revision);
        // For each issue
        for (String issueKey : issueKeys) {
            // Gets its details if not indexed yet
            SVNChangeLogIssue changeLogIssue = issues.get(issueKey);
            if (changeLogIssue == null) {
                changeLogIssue = getChangeLogIssue(repository, issueKey);
            }
            // Existing issue?
            if (changeLogIssue != null) {
                // Attaches the revision to this issue
                SVNRevisionInfo issueRevision = svnService.getRevisionInfo(repository, revision);
                changeLogIssue = changeLogIssue.addRevision(issueRevision);
                // Puts back into the cache
                issues.put(issueKey, changeLogIssue);
            }
        }
    }

    private SVNChangeLogIssue getChangeLogIssue(SVNRepository repository, String issueKey) {
        // Issue service
        ConfiguredIssueService configuredIssueService = repository.getConfiguredIssueService();
        // Gets the details about the issue
        if (configuredIssueService != null) {
            Issue issue = configuredIssueService.getIssue(issueKey);
            if (issue == null || StringUtils.isBlank(issue.getKey())) {
                return null;
            }
            // Creates the issue details for the change logs
            return new SVNChangeLogIssue(issue);
        } else {
            return null;
        }
    }

    private SVNChangeLogRevision createChangeLogRevision(SVNRepository repository, String path, int level, SVNLogEntry svnEntry) {
        return SVNServiceUtils.createChangeLogRevision(
                repository,
                path,
                level,
                svnEntry.getRevision(),
                svnEntry.getMessage(),
                svnEntry.getAuthor(),
                Time.from(svnEntry.getDate(), null)
        );
    }

    protected SCMBuildView<SVNHistory> getSCMBuildView(SVNRepository svnRepository, ID buildId) {
        // Gets the build view
        BuildView buildView = getBuildView(buildId);
        // Gets the history for the build
        SVNHistory history = getBuildSVNHistory(svnRepository, buildView.getBuild());
        // OK
        return new SCMBuildView<>(buildView, history);
    }

    @Override
    public OptionalLong getBuildRevision(Build build) {
        // Gets the branch SVN information
        Property<SVNBranchConfigurationProperty> branchConfigurationProperty = propertyService.getProperty(build.getBranch(), SVNBranchConfigurationPropertyType.class);
        Property<SVNProjectConfigurationProperty> projectConfigurationProperty = propertyService.getProperty(build.getBranch().getProject(), SVNProjectConfigurationPropertyType.class);
        if (branchConfigurationProperty.isEmpty() || projectConfigurationProperty.isEmpty()) {
            return OptionalLong.empty();
        } else {
            // Gets the branch revision link
            ConfiguredBuildSvnRevisionLink<Object> revisionLink = buildSvnRevisionLinkService.getConfiguredBuildSvnRevisionLink(
                    branchConfigurationProperty.getValue().getBuildRevisionLink()
            );
            // Returns revision information
            return revisionLink.getRevision(build, branchConfigurationProperty.getValue());
        }
    }

    @Override
    public String getDiff(SVNRepository repository, SVNChangeLogFile changeLogFile) {
        try (Transaction ignored = transactionService.start()) {
            return svnClient.getDiff(
                    repository,
                    changeLogFile.getPath(),
                    changeLogFile.getChanges().stream().map(change -> change.getRevisionInfo().getRevision()).collect(Collectors.toList())
            );
        }
    }

    @Override
    public SVNHistory getBuildSVNHistory(SVNRepository svnRepository, Build build) {
        // Gets the build path for the branch
        String svnBuildPath = getSVNBuildPath(build);
        // Gets the history from the SVN client
        return svnClient.getHistory(svnRepository, svnBuildPath);
    }

    @Override
    public Collection<ExportFormat> changeLogExportFormats(ID branchId) {
        // Gets the branch
        Branch branch = structureService.getBranch(branchId);
        // Gets the SVN repository
        SVNRepository svnRepository = getSVNRepository(branch);
        ConfiguredIssueService configuredIssueService = svnRepository.getConfiguredIssueService();
        if (configuredIssueService != null) {
            return configuredIssueService.getIssueServiceExtension().exportFormats(
                    configuredIssueService.getIssueServiceConfiguration()
            );
        } else {
            return Collections.emptyList();
        }
    }

    protected String getSVNBuildPath(Build build) {
        // Gets the build path property value
        Property<SVNBranchConfigurationProperty> branchConfiguration = propertyService.getProperty(
                build.getBranch(),
                SVNBranchConfigurationPropertyType.class
        );
        if (branchConfiguration.isEmpty()) {
            throw new MissingSVNBranchConfigurationException(build.getBranch().getName());
        } else {
            // Gets the build revision link
            ConfiguredBuildSvnRevisionLink<Object> revisionLink = buildSvnRevisionLinkService.getConfiguredBuildSvnRevisionLink(
                    branchConfiguration.getValue().getBuildRevisionLink()
            );
            // Gets the path to the build
            return revisionLink.getBuildPath(build, branchConfiguration.getValue());
        }
    }

    public SVNRepository getSVNRepository(Branch branch) {
        return svnService.getRequiredSVNRepository(branch);
    }

}
