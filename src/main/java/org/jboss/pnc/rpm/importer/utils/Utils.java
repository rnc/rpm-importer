package org.jboss.pnc.rpm.importer.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Utils {

    public static Path createTempDirForCloning() {
        return createTempDir("clone-", "cloning");
    }

    public static Path createTempDir(String prefix, String activity) {
        try {
            return Files.createTempDirectory(prefix);
        } catch (IOException e) {
            throw new RuntimeException("Cannot create temporary directory for " + activity, e);
        }
    }

    /**
     * The format of the file is
     *
     * <pre>
     * {@code <meadversion> <namedversion> <meadalpha> <meadrel> <serial> <namedversionrel>}
     * </pre>
     *
     * We only want the namedversion.
     *
     * @param path the directory where the ETT files are
     * @return a parsed String RH version
     */
    public static String parseVersionReleaseSerial(Path path) throws IOException {
        String found = Files.readString(Paths.get(path.toString(), "version-release-serial")).trim();
        return found.split(" ")[1];
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
    public static String parseMeadPkgName(Path path) throws IOException {
        String found = Files.readString(Paths.get(path.toString(), "mead-pkg-name")).trim();
        return found.split(" ")[0];
    }
}
