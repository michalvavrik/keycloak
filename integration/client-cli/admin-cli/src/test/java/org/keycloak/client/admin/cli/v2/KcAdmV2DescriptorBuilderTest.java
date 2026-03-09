package org.keycloak.client.admin.cli.v2;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class KcAdmV2DescriptorBuilderTest {

    @Test
    public void testConvertProducesCorrectResourceName() {
        OpenAPI openApi = loadBundledOpenApi();
        KcAdmV2CommandDescriptor descriptor = KcAdmV2DescriptorBuilder.convert(openApi);

        assertNotNull(descriptor.getResources());
        assertEquals(1, descriptor.getResources().size());
        assertEquals("client", descriptor.getResources().get(0).getName());
    }

    @Test
    public void testConvertProducesAllCommands() {
        OpenAPI openApi = loadBundledOpenApi();
        KcAdmV2CommandDescriptor descriptor = KcAdmV2DescriptorBuilder.convert(openApi);

        List<KcAdmV2CommandDescriptor.CommandDescriptor> commands =
                descriptor.getResources().get(0).getCommands();

        Map<String, KcAdmV2CommandDescriptor.CommandDescriptor> byName = commands.stream()
                .collect(Collectors.toMap(KcAdmV2CommandDescriptor.CommandDescriptor::getName, c -> c));

        assertEquals("Should have 6 commands", 6, byName.size());
        assertTrue(byName.containsKey("list"));
        assertTrue(byName.containsKey("create"));
        assertTrue(byName.containsKey("get"));
        assertTrue(byName.containsKey("update"));
        assertTrue(byName.containsKey("patch"));
        assertTrue(byName.containsKey("delete"));
    }

    @Test
    public void testCollectionCommandsDoNotRequireId() {
        KcAdmV2CommandDescriptor descriptor = KcAdmV2DescriptorBuilder.convert(loadBundledOpenApi());
        Map<String, KcAdmV2CommandDescriptor.CommandDescriptor> byName = commandsByName(descriptor);

        assertFalse("list should not require id", byName.get("list").isRequiresId());
        assertFalse("create should not require id", byName.get("create").isRequiresId());
    }

    @Test
    public void testSingleResourceCommandsRequireId() {
        KcAdmV2CommandDescriptor descriptor = KcAdmV2DescriptorBuilder.convert(loadBundledOpenApi());
        Map<String, KcAdmV2CommandDescriptor.CommandDescriptor> byName = commandsByName(descriptor);

        assertTrue("get should require id", byName.get("get").isRequiresId());
        assertTrue("update should require id", byName.get("update").isRequiresId());
        assertTrue("patch should require id", byName.get("patch").isRequiresId());
        assertTrue("delete should require id", byName.get("delete").isRequiresId());
    }

    @Test
    public void testHttpMethodsAreCorrect() {
        KcAdmV2CommandDescriptor descriptor = KcAdmV2DescriptorBuilder.convert(loadBundledOpenApi());
        Map<String, KcAdmV2CommandDescriptor.CommandDescriptor> byName = commandsByName(descriptor);

        assertEquals("GET", byName.get("list").getHttpMethod());
        assertEquals("POST", byName.get("create").getHttpMethod());
        assertEquals("GET", byName.get("get").getHttpMethod());
        assertEquals("PUT", byName.get("update").getHttpMethod());
        assertEquals("PATCH", byName.get("patch").getHttpMethod());
        assertEquals("DELETE", byName.get("delete").getHttpMethod());
    }

    @Test
    public void testDescriptionsFromOpenApiSummary() {
        KcAdmV2CommandDescriptor descriptor = KcAdmV2DescriptorBuilder.convert(loadBundledOpenApi());
        Map<String, KcAdmV2CommandDescriptor.CommandDescriptor> byName = commandsByName(descriptor);

        assertEquals("Get all clients", byName.get("list").getDescription());
        assertEquals("Create a new client", byName.get("create").getDescription());
    }

    @Test
    public void testSerializeDeserializeRoundtrip() throws Exception {
        KcAdmV2CommandDescriptor original = KcAdmV2DescriptorBuilder.convert(loadBundledOpenApi());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        KcAdmV2DescriptorBuilder.writeDescriptor(original,
                java.nio.file.Files.createTempFile("test-descriptor", ".json"));

        // Simulate reading from classpath
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        byte[] json = mapper.writeValueAsBytes(original);
        KcAdmV2CommandDescriptor deserialized = KcAdmV2DescriptorBuilder.readDescriptor(
                new ByteArrayInputStream(json));

        assertEquals(original.getVersion(), deserialized.getVersion());
        assertEquals(original.getResources().size(), deserialized.getResources().size());
        assertEquals(
                original.getResources().get(0).getCommands().size(),
                deserialized.getResources().get(0).getCommands().size());
    }

    private Map<String, KcAdmV2CommandDescriptor.CommandDescriptor> commandsByName(KcAdmV2CommandDescriptor descriptor) {
        return descriptor.getResources().get(0).getCommands().stream()
                .collect(Collectors.toMap(KcAdmV2CommandDescriptor.CommandDescriptor::getName, c -> c));
    }

    private OpenAPI loadBundledOpenApi() {
        return KcAdmV2DescriptorBuilder.parseOpenApi(
                () -> getClass().getResourceAsStream("/META-INF/openapi.json"));
    }
}
