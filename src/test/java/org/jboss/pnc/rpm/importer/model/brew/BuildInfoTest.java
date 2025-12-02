package org.jboss.pnc.rpm.importer.model.brew;

import static org.junit.jupiter.api.Assertions.*;

import org.jboss.pnc.rpm.importer.utils.Utils;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

class BuildInfoTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String invalidBuild = """
            {
              "build_id": 585577,
              "cg_id": null,
              "completion_time": "2017-08-19 19:47:03.709968",
              "completion_ts": 1503172023.70997,
              "creation_event_id": 16342777,
              "creation_time": "2017-08-19 19:47:02.370738",
              "creation_ts": 1503172022.37074,
              "draft": false,
              "epoch": null,
              "extra": null,
              "id": 585577,
              "name": "org.projectodd.vdx-vdx-parent",
              "nvr": "org.projectodd.vdx-vdx-parent-1.1.6.redhat_1-1",
              "owner_id": 3099,
              "owner_name": "psakar",
              "package_id": 60112,
              "package_name": "org.projectodd.vdx-vdx-parent",
              "promoter_id": null,
              "promoter_name": null,
              "promotion_time": null,
              "promotion_ts": null,
              "release": "1",
              "source": null,
              "start_time": "2017-08-19 19:47:02.370738",
              "start_ts": 1503172022.37074,
              "state": 1,
              "task_id": 13885070,
              "version": "1.1.6.redhat_1",
              "volume_id": 8,
              "volume_name": "rhel-7",
              "cg_name": null
            }
            """;

    private static final String legacyBuild = """
            {
              "build_id": 967196,
              "cg_id": null,
              "completion_time": "2019-08-07 09:12:21",
              "completion_ts": 1565169141.0,
              "creation_event_id": 26272353,
              "creation_time": "2019-09-12 16:31:11.069736",
              "creation_ts": 1568305871.06974,
              "draft": false,
              "epoch": null,
              "extra": {
                "build_system": "PNC",
                "external_build_id": "26216",
                "scmTag": "2.0.1.redhat-00001",
                "import_initiator": "lthon",
                "maven": {
                  "group_id": "org.eclipse.microprofile.health",
                  "artifact_id": "microprofile-health-parent",
                  "version": "2.0.1.redhat-00001"
                },
                "external_build_system": "/pnc-rest/rest/build-records/26216"
              },
              "id": 967196,
              "name": "org.eclipse.microprofile.health-microprofile-health-parent",
              "nvr": "org.eclipse.microprofile.health-microprofile-health-parent-2.0.1.redhat_00001-1",
              "owner_id": 3724,
              "owner_name": "newcastle",
              "package_id": 67763,
              "package_name": "org.eclipse.microprofile.health-microprofile-health-parent",
              "promoter_id": null,
              "promoter_name": null,
              "promotion_time": null,
              "promotion_ts": null,
              "release": "1",
              "start_time": "2019-08-07 08:46:26",
              "start_ts": 1565167586.0,
              "state": 1,
              "task_id": null,
              "version": "2.0.1.redhat_00001",
              "volume_id": 0,
              "volume_name": "DEFAULT",
              "cg_name": null
            }
            """;

    private static final String validBuild = """
            {
              "build_id": 2958832,
              "cg_id": 2,
              "completion_time": "2024-03-18 09:30:52",
              "completion_ts": 1710754252.0,
              "creation_event_id": 56970947,
              "creation_time": "2024-03-18 09:31:39.128410",
              "creation_ts": 1710754299.12841,
              "draft": false,
              "epoch": null,
              "extra": {
                "external_build_system": "/pnc-rest/v2/builds/A66J6LVO2DYAA",
                "external_build_id": "A66J6LVO2DYAA",
                "maven": {
                  "group_id": "org.wildfly.wildfly-http-client",
                  "artifact_id": "wildfly-http-client-parent",
                  "version": "2.0.7.Final-redhat-00001"
                },
                "build_system": "PNC",
                "import_initiator": "service-account-pnc-orchestrator",
                "scmTag": "2.0.7.Final-redhat-00001",
                "typeinfo": {
                  "maven": {
                    "group_id": "org.wildfly.wildfly-http-client",
                    "artifact_id": "wildfly-http-client-parent",
                    "version": "2.0.7.Final-redhat-00001"
                  }
                }
              },
              "id": 2958832,
              "name": "org.wildfly.wildfly-http-client-wildfly-http-client-parent",
              "nvr": "org.wildfly.wildfly-http-client-wildfly-http-client-parent-2.0.7.Final_redhat_00001-1",
              "owner_id": 7083,
              "owner_name": "projectnewcastle",
              "package_id": 61765,
              "package_name": "org.wildfly.wildfly-http-client-wildfly-http-client-parent",
              "promoter_id": null,
              "promoter_name": null,
              "promotion_time": null,
              "promotion_ts": null,
              "release": "1",
              "start_time": "2024-03-18 09:27:59",
              "start_ts": 1710754079.0,
              "state": 1,
              "task_id": null,
              "version": "2.0.7.Final_redhat_00001",
              "volume_id": 0,
              "volume_name": "DEFAULT",
              "cg_name": "Project Newcastle"
            }
            """;

    @Test
    void testInvalidBuildInfo() throws JsonProcessingException {
        BuildInfo buildInfo = MAPPER.readValue(
                invalidBuild,
                BuildInfo.class);
        assertFalse(Utils.validateBuildInfo(buildInfo));
    }

    @Test
    void testLegacyBuildInfo() throws JsonProcessingException {
        BuildInfo buildInfo = MAPPER.readValue(
                legacyBuild,
                BuildInfo.class);
        assertTrue(Utils.validateBuildInfo(buildInfo));
        assertEquals("org.eclipse.microprofile.health", buildInfo.getExtra().getTypeinfo().getMaven().getGroupId());
        assertEquals("org.eclipse.microprofile.health", buildInfo.getExtra().getMaven().getGroupId());
    }

    @Test
    void testValidBuildInfo() throws JsonProcessingException {
        BuildInfo buildInfo = MAPPER.readValue(
                validBuild,
                BuildInfo.class);
        assertTrue(Utils.validateBuildInfo(buildInfo));
        assertEquals("org.wildfly.wildfly-http-client", buildInfo.getExtra().getTypeinfo().getMaven().getGroupId());
        assertEquals("org.wildfly.wildfly-http-client", buildInfo.getExtra().getMaven().getGroupId());
        assertEquals("Project Newcastle", buildInfo.getCgName());
    }
}
