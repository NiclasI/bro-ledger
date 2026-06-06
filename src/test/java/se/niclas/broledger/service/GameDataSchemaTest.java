package se.niclas.broledger.service;

import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates game-data.json against game-data.schema.json on every build.
 * Catches mistyped or malformed entries that Jackson's tolerant parser would
 * silently ignore at runtime.
 */
class GameDataSchemaTest {

    private static final String DATA_BASE = "/se/niclas/broledger/data/";

    @Test
    void gameData_conformsToSchema() throws Exception {
        // networknt uses Jackson 2.x; load both files via its own ObjectMapper
        ObjectMapper mapper = new ObjectMapper();

        JsonNode schemaNode;
        try (InputStream is = GameDataSchemaTest.class.getResourceAsStream(
                DATA_BASE + "game-data.schema.json")) {
            assertNotNull(is, "game-data.schema.json not found on classpath");
            schemaNode = mapper.readTree(is);
        }

        JsonNode dataNode;
        try (InputStream is = GameDataSchemaTest.class.getResourceAsStream(
                DATA_BASE + "game-data.json")) {
            assertNotNull(is, "game-data.json not found on classpath");
            dataNode = mapper.readTree(is);
        }

        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
        JsonSchema schema = factory.getSchema(schemaNode);

        Set<ValidationMessage> errors = schema.validate(dataNode);

        if (!errors.isEmpty()) {
            StringBuilder sb = new StringBuilder(errors.size() + " schema violation(s):\n");
            errors.stream().limit(20).forEach(e -> sb.append("  ").append(e).append('\n'));
            if (errors.size() > 20) sb.append("  … and ").append(errors.size() - 20).append(" more\n");
            fail(sb.toString());
        }
    }
}
