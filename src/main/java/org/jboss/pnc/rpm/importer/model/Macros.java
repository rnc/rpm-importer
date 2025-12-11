package org.jboss.pnc.rpm.importer.model;

import java.util.Map;

public record Macros(Map<String, String> allMacros) {
}
