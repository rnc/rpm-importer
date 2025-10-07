package org.jboss.pnc.rpm.importer;

import static org.maveniverse.domtrip.maven.MavenPomElements.Elements.ARTIFACT_ID;
import static org.maveniverse.domtrip.maven.MavenPomElements.Elements.BUILD;
import static org.maveniverse.domtrip.maven.MavenPomElements.Elements.CLASSIFIER;
import static org.maveniverse.domtrip.maven.MavenPomElements.Elements.DEPENDENCIES;
import static org.maveniverse.domtrip.maven.MavenPomElements.Elements.DEPENDENCY_MANAGEMENT;
import static org.maveniverse.domtrip.maven.MavenPomElements.Elements.GROUP_ID;
import static org.maveniverse.domtrip.maven.MavenPomElements.Elements.NAME;
import static org.maveniverse.domtrip.maven.MavenPomElements.Elements.PLUGINS;
import static org.maveniverse.domtrip.maven.MavenPomElements.Elements.PROPERTIES;
import static org.maveniverse.domtrip.maven.MavenPomElements.Elements.TYPE;
import static org.maveniverse.domtrip.maven.MavenPomElements.Elements.VERSION;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang.StringUtils;
import org.commonjava.atlas.maven.ident.ref.SimpleArtifactRef;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logmanager.Level;
import org.jboss.pnc.api.reqour.dto.TranslateRequest;
import org.jboss.pnc.api.reqour.dto.TranslateResponse;
import org.jboss.pnc.bacon.auth.client.PncClientHelper;
import org.jboss.pnc.bacon.common.Constant;
import org.jboss.pnc.bacon.config.Config;
import org.jboss.pnc.bacon.config.PncConfig;
import org.jboss.pnc.bacon.config.ReqourConfig;
import org.jboss.pnc.client.Configuration;
import org.jboss.pnc.dto.Artifact;
import org.jboss.pnc.dto.SCMRepository;
import org.jboss.pnc.dto.requests.CreateAndSyncSCMRequest;
import org.jboss.pnc.dto.response.Page;
import org.jboss.pnc.dto.response.RepositoryCreationResponse;
import org.jboss.pnc.rpm.importer.clients.OrchService;
import org.jboss.pnc.rpm.importer.clients.ReqourService;
import org.jboss.pnc.rpm.importer.model.brew.BuildInfo;
import org.jboss.pnc.rpm.importer.utils.Brew;
import org.jboss.pnc.rpm.importer.utils.Utils;
import org.maveniverse.domtrip.maven.PomEditor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import eu.maveniverse.domtrip.Document;
import eu.maveniverse.domtrip.Element;
import io.quarkus.logging.Log;
import io.quarkus.picocli.runtime.annotations.TopCommand;
import picocli.CommandLine;
import picocli.CommandLine.Option;

/**
 * The entrypoint of the RPM importer.
 */
@TopCommand
@CommandLine.Command(
        name = "rpm-importer",
        description = "",
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider.class)
public class App implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(App.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @RestClient
    ReqourService reqourService;

    @RestClient
    OrchService orchService;

    @Option(names = { "-v", "--verbose" }, description = "Verbose output")
    boolean verbose;

    @Option(names = "--profile", description = "PNC Configuration profile")
    private String profile = "default";

    /**
     * Set the path to config file if the configPath flag or environment variable is set
     */
    @Option(names = { "-p", "--configPath" }, description = "Path to PNC configuration folder")
    private String configPath = null;

    @Option(names = "--url", description = "External URL to distgit repository", required = true)
    private String url;

    @Option(names = "--branch", description = "Branch in distgit repository")
    private String branch;

    @Option(
            names = "--skip-sync",
            description = "Skips any syncing and only clones the repository and performs the patching")
    private Boolean skip;

    @Option(
            names = "--repository",
            description = "Skips cloning and uses existing repository")
    private Path repository;

    @Option(
            names = "--overwrite",
            description = "Overwrites existing pom. Dangerous!")
    private Boolean overwrite = false;

    @Override
    public void run() {
        // This is not ideal - while there should be native java transports to use
        // the ssh agent I couldn't get them to work. According to
        // https://gerrit.googlesource.com/jgit/+/refs/heads/servlet-4/org.eclipse.jgit.ssh.apache/README.md
        // setting GIT_SSH means it will use native git to communicate.
        // Entered https://github.com/eclipse-jgit/jgit/issues/216 and https://github.com/eclipse-jgit/jgit/issues/215
        if (System.getenv("GIT_SSH") == null) {
            log.warn(
                    "You may need to define GIT_SSH=/bin/ssh in the environment or ensure the ssh agent is configured with the key and     'IdentitiesOnly yes' for authentication");
        }

        if (verbose) {
            java.util.logging.Logger.getLogger("org.jboss.pnc.rpm").setLevel(Level.FINE);
            log.debug("Log level set to DEBUG");
        }

        if (configPath != null) {
            setConfigLocation(configPath, "flag");
        } else if (System.getenv(Constant.CONFIG_ENV) != null) {
            setConfigLocation(System.getenv(Constant.CONFIG_ENV), "environment variable");
        } else {
            setConfigLocation(Constant.DEFAULT_CONFIG_FOLDER, "constant");
        }
        PncConfig pncConfig = Config.instance().getActiveProfile().getPnc();
        Configuration pncConfiguration = PncClientHelper.getPncConfiguration();

        ReqourConfig reqourConfig = Config.instance().getActiveProfile().getReqour();
        TranslateResponse translateResponse = reqourService.external_to_internal(
                reqourConfig.getUrl(),
                TranslateRequest.builder().externalUrl(url).build());

        RepositoryCreationResponse repositoryCreationResponse;
        String internalUrl = translateResponse.getInternalUrl();

        log.info("For external URL {} retrieved internal {}", url, translateResponse.getInternalUrl());

        // We search using the internal URL in case the scm repository hasn't been setup to
        // sync and doesn't have the external URL listed.
        var response = orchService.getAll(
                pncConfig.getUrl(),
                pncConfiguration.getBearerTokenSupplier().get(),
                internalUrl);
        Optional<SCMRepository> internalUrlOpt = response.getContent().stream().findFirst();

        // If present, the repository is already synced to internal.
        if (!skip && internalUrlOpt.isEmpty()) {
            CreateAndSyncSCMRequest createAndSyncSCMRequest = CreateAndSyncSCMRequest.builder().scmUrl(url).build();
            log.warn("### Invoking clone service with {}", createAndSyncSCMRequest);
            repositoryCreationResponse = orchService
                    .createNew(
                            pncConfig.getUrl(),
                            "Bearer " + pncConfiguration.getBearerTokenSupplier().get(),
                            createAndSyncSCMRequest);
            log.warn("### clone service {}", repositoryCreationResponse);
        } else if (skip && internalUrlOpt.isEmpty()) {
            log.error("Skipping repository creation but {} is not available internally", internalUrl);
            //TODO: Should this be an error.
            //throw new RuntimeException("Internal repository does not exist");
        }
        log.info("Found internalUrl {}", internalUrl);

        if (StringUtils.isEmpty(branch)) {
            log.warn("No branch specified; unable to proceed");
            return;
        }
        if (repository == null) {
            repository = cloneRepository(internalUrl, branch);
        } else {
            log.info("Using existing repository {}", repository);
            try (var jGit = Git.init().setDirectory(repository.toFile()).call()) {
                jGit.checkout().setName(branch).call();
            } catch (GitAPIException e) {
                throw new RuntimeException(e);
            }
        }

        try {
            String lastMeadBuildFile = Files.readString(Paths.get(repository.toString(), "last-mead-build")).trim();
            BuildInfo lastMeadBuild = MAPPER.readValue(
                    Brew.getBuildInfo(lastMeadBuildFile),
                    BuildInfo.class);
            log.info(
                    "Found last-mead-build {} with GAV {}:{}:{}",
                    lastMeadBuildFile,
                    lastMeadBuild.getExtra().getTypeinfo().getMaven().getGroupId(),
                    lastMeadBuild.getExtra().getTypeinfo().getMaven().getArtifactId(),
                    lastMeadBuild.getExtra().getTypeinfo().getMaven().getVersion());
            String version = Utils.parseVersionReleaseSerial(repository);
            log.info("Found version: {}", version);
            // We want to ensure the artifact names are completely unique. Unlike in brew if
            // we build multiple branches they still need to be differentiated.
            // TODO: Decide on the best way to differentiate. One format ends up as
            //           org.jboss.pnc.rpm : org.apache.sshd-sshd-jb-eap-7.4-rhel-7
            //       It might be better to also use the groupId e.g.
            //           org.jboss.pnc.rpm.org.apache.sshd : sshd-jb-eap-7.4-rhel-7
            //       Latter needs NVR -> GAV conversion
            String groupId = "org.jboss.pnc.rpm." + lastMeadBuild.getExtra().getTypeinfo().getMaven().getGroupId();
            String artifactId = lastMeadBuild.getExtra().getTypeinfo().getMaven().getArtifactId() + "-" + branch;
            log.info(
                    "Setting groupId : artifactId to comprise of scoped groupId and branch name: {}:{}",
                    groupId,
                    artifactId);

            String source;
            try (InputStream x = App.class.getClassLoader().getResourceAsStream("pom-template.xml")) {
                assert x != null;
                source = new String(x.readAllBytes(), StandardCharsets.UTF_8);
            }
            File target = new File(repository.toFile(), "pom.xml");

            if (target.exists() && !overwrite) {
                log.error("pom.xml already exists and not overwriting");
                return;
            }
            Files.writeString(target.toPath(), source);

            // Using https://github.com/maveniverse/domtrip as Maven MavenXpp3Reader/Writer
            // does not preserve comments. Another alternative would be PME POMIO but that
            // brings in quite a lot.
            Document document = Document.of(target.toPath());
            PomEditor pomEditor = new PomEditor(document);
            pomEditor.findChildElement(pomEditor.root(), NAME).textContent(Utils.parseMeadPkgName(repository));
            pomEditor.findChildElement(pomEditor.root(), GROUP_ID).textContent(groupId);
            pomEditor.findChildElement(pomEditor.root(), ARTIFACT_ID).textContent(artifactId);
            pomEditor.findChildElement(pomEditor.root(), VERSION).textContent(version);
            pomEditor.findChildElement(pomEditor.findChildElement(pomEditor.root(), PROPERTIES), "wrappedBuild")
                    .textContent(version);
            Element depMgmt = pomEditor.findChildElement(pomEditor.root(), DEPENDENCY_MANAGEMENT);
            Element deps = pomEditor.findChildElement(depMgmt, DEPENDENCIES);
            pomEditor.addDependency(
                    deps,
                    lastMeadBuild.getExtra().getTypeinfo().getMaven().getGroupId(),
                    lastMeadBuild.getExtra().getTypeinfo().getMaven().getArtifactId(),
                    "${wrappedBuild}");

            List<SimpleArtifactRef> dependencies = getDependencies(pncConfig, pncConfiguration, lastMeadBuild);
            Element plugins = pomEditor.findChildElement(pomEditor.findChildElement(pomEditor.root(), BUILD), PLUGINS);
            // findFirst as the template only has one plugin with this artifactId
            var plugin = plugins.children()
                    .filter(
                            element -> pomEditor.findChildElement(element, "artifactId")
                                    .textContent()
                                    .equals("maven-dependency-plugin"))
                    .findFirst();
            // We know the template is a specific format so don't need to use isPresent.
            @SuppressWarnings("OptionalGetWithoutIsPresent")
            Element artifactItems = plugin.get()
                    .child("executions")
                    .get()
                    .child("execution")
                    .get()
                    .child("configuration")
                    .get()
                    .child("artifactItems")
                    .get();
            log.warn("### items are {}", artifactItems);

            dependencies.forEach(artifactRef -> {
                Element artifactItem = pomEditor.insertMavenElement(artifactItems, "artifactItem");
                pomEditor.insertMavenElement(artifactItem, GROUP_ID, artifactRef.getGroupId());
                pomEditor.insertMavenElement(artifactItem, ARTIFACT_ID, artifactRef.getArtifactId());
                pomEditor.insertMavenElement(artifactItem, VERSION, "${wrappedBuild}");
                if (StringUtils.isNotEmpty(artifactRef.getClassifier())) {
                    pomEditor.insertMavenElement(artifactItem, CLASSIFIER, artifactRef.getClassifier());
                }
                if (StringUtils.isNotEmpty(artifactRef.getType()) && !artifactRef.getType().equals("jar")) {
                    pomEditor.insertMavenElement(artifactItem, TYPE, artifactRef.getType());
                }
            });

            Files.writeString(target.toPath(), pomEditor.toXml());

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    List<SimpleArtifactRef> getDependencies(
            PncConfig pncConfig,
            Configuration pncConfiguration,
            BuildInfo lastMeadBuild) {
        // Unfortunately, this is somewhat heavyweight. We need all the artifacts produced by this
        // build. I think this is currently only possible by retrieving the artifactId for the GAV,
        // then the artifact for that Id and finally using the buildId from the previous, retrieve all
        // built artifacts.
        var allArtifacts = orchService.getArtifactsFiltered(
                pncConfig.getUrl(),
                pncConfiguration.getBearerTokenSupplier().get(),
                String.format(
                        "%s:%s:%s:%s",
                        lastMeadBuild.getExtra().getTypeinfo().getMaven().getGroupId(),
                        lastMeadBuild.getExtra().getTypeinfo().getMaven().getArtifactId(),
                        "pom",
                        lastMeadBuild.getExtra().getTypeinfo().getMaven().getVersion()));

        log.warn("### {}", allArtifacts);
        String artifactId = null;
        var found = allArtifacts.getContent().stream().findFirst();
        if (found.isPresent()) {
            artifactId = found.get().getId();
            Artifact artifact = orchService.getSpecific(
                    pncConfig.getUrl(),
                    pncConfiguration.getBearerTokenSupplier().get(),
                    artifactId);

            String buildId = artifact.getBuild().getId();
            log.info(
                    "For artifact {} found artifactId {} with buildId {}",
                    lastMeadBuild.getExtra().getTypeinfo().getMaven(),
                    artifactId,
                    buildId);

            Page<Artifact> artifacts = orchService.getBuiltArtifacts(
                    pncConfig.getUrl(),
                    pncConfiguration.getBearerTokenSupplier().get(),
                    buildId);
            var deps = artifacts.getContent()
                    .stream()
                    .map(c -> SimpleArtifactRef.parse(c.getIdentifier()))
                    .sorted()
                    .toList();
            log.info("Found dependencies {}", deps);
            return deps;
        } else {
            log.error("Unable to find an artifact from GAV {}", lastMeadBuild.getExtra().getTypeinfo().getMaven());
        }
        return Collections.emptyList();
    }

    private Path cloneRepository(String url, String branch) {
        Path path = Utils.createTempDirForCloning();
        log.info("Using {} for repository", path);
        StringWriter writer = new StringWriter();
        TextProgressMonitor monitor = new TextProgressMonitor(writer) {
            // Don't want percent updates, just final summaries.
            protected void onUpdate(String taskName, int workCurr, Duration duration) {
            }

            protected void onUpdate(String taskName, int cmp, int totalWork, int pcnt, Duration duration) {
            }
        };
        monitor.showDuration(true);

        var repoClone = Git.cloneRepository()
                .setURI(url)
                .setProgressMonitor(monitor)
                .setBranch(branch)
                .setDirectory(path.toFile());
        try (var ignored = repoClone.call()) {
            Log.infof("Clone summary:\n%s", writer.toString().replaceAll("(?m)^\\s+", ""));
        } catch (GitAPIException e) {
            throw new RuntimeException(e);
        }
        return path;
    }

    private void setConfigLocation(String configLocation, String source) {
        Config.configure(configLocation, Constant.CONFIG_FILE_NAME, profile);
        log.debug("Config file set from {} with profile {} to {}", source, profile, Config.getConfigFilePath());
    }
}
