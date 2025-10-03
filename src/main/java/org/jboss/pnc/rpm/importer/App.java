/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.rpm.importer;

import static org.maveniverse.domtrip.maven.MavenPomElements.Elements.ARTIFACT_ID;
import static org.maveniverse.domtrip.maven.MavenPomElements.Elements.PROPERTIES;
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
import java.util.Optional;

import org.apache.commons.lang.StringUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.transport.sshd.SshdSessionFactory;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.pnc.api.reqour.dto.TranslateRequest;
import org.jboss.pnc.api.reqour.dto.TranslateResponse;
import org.jboss.pnc.bacon.auth.client.PncClientHelper;
import org.jboss.pnc.bacon.common.Constant;
import org.jboss.pnc.bacon.config.Config;
import org.jboss.pnc.bacon.config.PncConfig;
import org.jboss.pnc.bacon.config.ReqourConfig;
import org.jboss.pnc.client.Configuration;
import org.jboss.pnc.dto.SCMRepository;
import org.jboss.pnc.dto.requests.CreateAndSyncSCMRequest;
import org.jboss.pnc.dto.response.RepositoryCreationResponse;
import org.jboss.pnc.rpm.importer.clients.OrchService;
import org.jboss.pnc.rpm.importer.clients.ReqourService;
import org.jboss.pnc.rpm.importer.utils.Utils;
import org.maveniverse.domtrip.maven.PomEditor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.maveniverse.domtrip.Document;
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

    @RestClient
    ReqourService reqourService;

    @RestClient
    OrchService orchService;

    /**
     * Set the verbosity of logback if the verbosity flag is set
     */
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
    SshdSessionFactory sshSessionFactory;

    @Override
    public void run() {
        // This is not ideal - while there should be native java transports to use
        // the ssh agent I couldn't get them to work. According to
        // https://gerrit.googlesource.com/jgit/+/refs/heads/servlet-4/org.eclipse.jgit.ssh.apache/README.md
        // setting GIT_SSH means it will use native git to communicate.
        // Entered https://github.com/eclipse-jgit/jgit/issues/216 and https://github.com/eclipse-jgit/jgit/issues/215
        if (System.getenv("GIT_SSH") == null) {
            log.error("Define GIT_SSH=/bin/ssh in the environment.");
            return;
        }

        if (verbose) {
            // TODO: NYI
            throw new RuntimeException("NYI - use -Dquarkus.log.level=DEBUG for now");
            //            log.warn("### slf4j " + log.getClass().getName());
            //            ObjectHelper.setRootLoggingLevel(Level.DEBUG);
            //
            //            // Add more loggers that you want to switch to DEBUG here
            //            ObjectHelper.setLoggingLevel("org.jboss.pnc.client", Level.DEBUG);
            //
            //            log.debug("Log level set to DEBUG");
        }

        if (configPath != null) {
            setConfigLocation(configPath, "flag");
        } else if (System.getenv(Constant.CONFIG_ENV) != null) {
            setConfigLocation(System.getenv(Constant.CONFIG_ENV), "environment variable");
        } else {
            setConfigLocation(Constant.DEFAULT_CONFIG_FOLDER, "constant");
        }

        ReqourConfig reqourConfig = Config.instance().getActiveProfile().getReqour();
        TranslateResponse translateResponse = reqourService.external_to_internal(
                reqourConfig.getUrl(),
                TranslateRequest.builder().externalUrl(url).build());

        RepositoryCreationResponse repositoryCreationResponse;
        String internalUrl = translateResponse.getInternalUrl();

        log.info("For external URL {} retrieved internal {}", url, translateResponse.getInternalUrl());

        PncConfig pncConfig = Config.instance().getActiveProfile().getPnc();
        Configuration pncConfiguration = PncClientHelper.getPncConfiguration();

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
            throw new RuntimeException("Internal repository does not exist");
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
            String version = parseVersionReleaseSerial(repository);
            log.info("Found version: {}", version);
            String artifactId = parseMeadPkgName(repository) + "-" + branch;
            log.info("Setting artifactId to comprise of mead-pkg-name and branch name: {}", artifactId);

            String source;
            try (InputStream x = App.class.getClassLoader().getResourceAsStream("pom-template.xml")) {
                assert x != null;
                source = new String(x.readAllBytes(), StandardCharsets.UTF_8);
            }
            File target = new File(repository.toFile(), "pom.xml");

            if (target.exists()) {
                log.error("pom.xml already exists and not overwriting");
                return;
            }
            Files.writeString(target.toPath(), source);

            // Using https://github.com/maveniverse/domtrip as Maven MavenXpp3Reader/Writer
            // does not preserve comments. Another alternative would be PME POMIO but that
            // brings in quite a lot.
            Document document = Document.of(target.toPath());
            PomEditor pomEditor = new PomEditor(document);
            pomEditor.findChildElement(pomEditor.root(), ARTIFACT_ID).textContent(artifactId);
            pomEditor.findChildElement(pomEditor.root(), VERSION).textContent(version);
            pomEditor.findChildElement(pomEditor.findChildElement(pomEditor.root(), PROPERTIES), "wrappedBuild")
                    .textContent(version);

            File target3 = new File(repository.toFile(), "pom-domtrip.xml");
            Files.writeString(target3.toPath(), pomEditor.toXml());

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    Path cloneRepository(String url, String branch) {
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

    /**
     * The format of the file is
     *
     * <pre>
     * {@code <meadversion> <namedversion> <meadalpha> <meadrel> <serial> <namedversionrel>}
     * </pre>
     *
     * We only want the meadversion.
     *
     * @param path the directory where the ETT files are
     * @return a parsed String version
     */
    private String parseVersionReleaseSerial(Path path) throws IOException {
        String found = Files.readString(Paths.get(path.toString(), "version-release-serial")).trim();
        return found.split(" ")[0];
    }

    /**
     * The format of the file is
     *
     * <pre>
     * {@code <pkg> <optionalTag>}
     * </pre>
     *
     * We only want the meadversion.
     *
     * @param path the directory where the ETT files are
     * @return a parsed String version
     */
    private String parseMeadPkgName(Path path) throws IOException {
        String found = Files.readString(Paths.get(path.toString(), "mead-pkg-name")).trim();
        return found.split(" ")[0];
    }

    private void setConfigLocation(String configLocation, String source) {
        Config.configure(configLocation, Constant.CONFIG_FILE_NAME, profile);
        log.debug("Config file set from {} with profile {} to {}", source, profile, Config.getConfigFilePath());
    }
}
