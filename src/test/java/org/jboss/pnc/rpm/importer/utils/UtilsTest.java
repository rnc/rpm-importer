package org.jboss.pnc.rpm.importer.utils;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.LogRecord;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.test.LogCollectingTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.ResourceArg;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@QuarkusTestResource(
        value = LogCollectingTestResource.class,
        restrictToAnnotatedClass = true,
        initArgs = @ResourceArg(name = LogCollectingTestResource.LEVEL, value = "FINE"))
class UtilsTest {

    @BeforeEach
    public void clearLogs() {
        LogCollectingTestResource.current().clear();
    }

    @Test
    void testClone() {
        Path result = Utils.cloneRepository("https://github.com/project-ncl/rpm-importer.git", "main");

        List<LogRecord> logRecords = LogCollectingTestResource.current().getRecords();

        assertTrue(result.toFile().exists());
        assertTrue(new File(result.toString(), "README.md").exists());
        assertTrue(logRecords.stream().anyMatch(r -> LogCollectingTestResource.format(r).contains("Clone summary")));
    }
}