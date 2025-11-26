package org.jboss.pnc.rpm.importer;

import static eu.maveniverse.domtrip.maven.MavenPomElements.Elements.BUILD;
import static eu.maveniverse.domtrip.maven.MavenPomElements.Elements.PLUGINS;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import org.jboss.pnc.rpm.importer.model.brew.Extra;
import org.jboss.pnc.rpm.importer.model.brew.TagInfo;
import org.jboss.pnc.rpm.importer.utils.Utils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import eu.maveniverse.domtrip.Document;
import eu.maveniverse.domtrip.Element;
import eu.maveniverse.domtrip.maven.PomEditor;

@SuppressWarnings("ResultOfMethodCallIgnored")
class AppTest {
    @Test
    void testUpdateSpecName(@TempDir Path tempDir) throws IOException {
        File spec = new File(tempDir.toString(), "example.spec");
        spec.createNewFile();
        String source = Utils.readTemplate();

        App app = new App();
        app.repository = tempDir;
        String result = app.updateSpecName(source);

        assertTrue(result.contains("def spec = new File(\"${project.build.directory}/spec/example.spec"));
    }

    @Test
    void testAddMacros(@TempDir Path tempDir) throws IOException {
        String source = Utils.readTemplate();
        Document document = Document.of(source);
        PomEditor pomEditor = new PomEditor(document);
        Element plugins = pomEditor.findChildElement(pomEditor.findChildElement(pomEditor.root(), BUILD), PLUGINS);
        TagInfo tagInfo = new TagInfo();
        Extra extra = new Extra();
        tagInfo.setExtra(extra);
        extra.setRpmMacroDist("MY-CUSTOM-MACRO");

        App app = new App();
        app.repository = tempDir;
        app.updateMacros(pomEditor, plugins, tagInfo);

        String result = pomEditor.toXml();
        assertTrue(result.contains("""
                          <macros>
                            <dist>MY-CUSTOM-MACRO</dist>
                          </macros>
                """));
    }
}
