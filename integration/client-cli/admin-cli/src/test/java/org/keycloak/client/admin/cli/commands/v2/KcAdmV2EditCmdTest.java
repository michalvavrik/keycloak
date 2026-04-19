package org.keycloak.client.admin.cli.commands.v2;

import java.io.IOException;

import org.keycloak.client.admin.cli.v2.KcAdmV2EditCmd;
import org.keycloak.client.cli.util.OutputUtil;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class KcAdmV2EditCmdTest {

    @Test
    public void testIdenticalObjectsProduceEmptyDiff() throws Exception {
        ObjectNode diff = diff(
                """
                {"clientId": "test", "enabled": true, "description": "hello"}
                """,
                """
                {"clientId": "test", "enabled": true, "description": "hello"}
                """);

        assertTrue("identical objects should produce empty diff", diff.isEmpty());
    }

    @Test
    public void testFieldOrderDoesNotMatter() throws Exception {
        ObjectNode diff = diff(
                """
                {"a": 1, "b": 2, "c": 3}
                """,
                """
                {"c": 3, "a": 1, "b": 2}
                """);

        assertTrue("different field order with same values should produce empty diff", diff.isEmpty());
    }

    @Test
    public void testChangedStringValue() throws Exception {
        ObjectNode diff = diff(
                """
                {"name": "old-name", "other": "unchanged"}
                """,
                """
                {"name": "new-name", "other": "unchanged"}
                """);

        assertEquals(1, diff.size());
        assertNotNull("diff should contain 'name'", diff.get("name"));
        assertEquals("new-name", diff.get("name").asText());
    }

    @Test
    public void testChangedBooleanValue() throws Exception {
        ObjectNode diff = diff(
                """
                {"enabled": true, "active": true}
                """,
                """
                {"enabled": false, "active": true}
                """);

        assertEquals(1, diff.size());
        assertNotNull("diff should contain 'enabled'", diff.get("enabled"));
        assertFalse(diff.get("enabled").asBoolean());
    }

    @Test
    public void testChangedNumberValue() throws Exception {
        ObjectNode diff = diff(
                """
                {"count": 1, "limit": 100}
                """,
                """
                {"count": 42, "limit": 100}
                """);

        assertEquals(1, diff.size());
        assertNotNull("diff should contain 'count'", diff.get("count"));
        assertEquals(42, diff.get("count").asInt());
    }

    @Test
    public void testAddedField() throws Exception {
        ObjectNode diff = diff(
                """
                {"a": 1}
                """,
                """
                {"a": 1, "b": 2}
                """);

        assertEquals(1, diff.size());
        assertNotNull("diff should contain added field 'b'", diff.get("b"));
        assertEquals(2, diff.get("b").asInt());
    }

    @Test
    public void testRemovedFieldBecomesNull() throws Exception {
        ObjectNode diff = diff(
                """
                {"a": 1, "b": 2}
                """,
                """
                {"a": 1}
                """);

        assertEquals(1, diff.size());
        assertNotNull("diff should contain removed field 'b'", diff.get("b"));
        assertTrue("removed field should be null in merge-patch", diff.get("b").isNull());
    }

    @Test
    public void testMultipleChanges() throws Exception {
        ObjectNode diff = diff(
                """
                {"keep": "same", "change": "old", "remove": "gone", "num": 1}
                """,
                """
                {"keep": "same", "change": "new", "num": 1, "added": true}
                """);

        assertEquals(3, diff.size());
        assertNotNull("diff should contain changed field", diff.get("change"));
        assertEquals("new", diff.get("change").asText());
        assertNotNull("diff should contain removed field", diff.get("remove"));
        assertTrue("removed field should be null", diff.get("remove").isNull());
        assertNotNull("diff should contain added field", diff.get("added"));
        assertTrue(diff.get("added").asBoolean());
        assertNull("unchanged fields should not appear in diff", diff.get("keep"));
        assertNull("unchanged fields should not appear in diff", diff.get("num"));
    }

    @Test
    public void testNestedObjectWithInnerChange() throws Exception {
        ObjectNode diff = diff(
                """
                {"nested": {"a": 1, "b": 2}, "top": "same"}
                """,
                """
                {"nested": {"a": 1, "b": 99}, "top": "same"}
                """);

        assertEquals(1, diff.size());
        JsonNode nestedDiff = diff.get("nested");
        assertNotNull("diff should contain 'nested'", nestedDiff);
        assertEquals("nested diff should only contain changed field", 1, nestedDiff.size());
        assertNotNull("nested diff should contain 'b'", nestedDiff.get("b"));
        assertEquals(99, nestedDiff.get("b").asInt());
    }

    @Test
    public void testNestedObjectUnchangedExcludedFromDiff() throws Exception {
        ObjectNode diff = diff(
                """
                {"nested": {"a": 1}, "top": "old"}
                """,
                """
                {"nested": {"a": 1}, "top": "new"}
                """);

        assertEquals(1, diff.size());
        assertNotNull("diff should contain 'top'", diff.get("top"));
        assertEquals("new", diff.get("top").asText());
        assertNull("unchanged nested object should not appear in diff", diff.get("nested"));
    }

    @Test
    public void testArrayReplacedEntirely() throws Exception {
        ObjectNode diff = diff(
                """
                {"arr": [1, 2, 3]}
                """,
                """
                {"arr": [4, 5]}
                """);

        assertEquals(1, diff.size());
        JsonNode arr = diff.get("arr");
        assertNotNull("diff should contain 'arr'", arr);
        assertTrue(arr.isArray());
        assertEquals(2, arr.size());
        assertEquals(4, arr.get(0).asInt());
        assertEquals(5, arr.get(1).asInt());
    }

    @Test
    public void testArrayUnchanged() throws Exception {
        ObjectNode diff = diff(
                """
                {"arr": [1, 2, 3], "x": "same"}
                """,
                """
                {"arr": [1, 2, 3], "x": "same"}
                """);

        assertTrue("unchanged array should produce empty diff", diff.isEmpty());
    }

    @Test
    public void testValueToNull() throws Exception {
        ObjectNode diff = diff(
                """
                {"a": "value"}
                """,
                """
                {"a": null}
                """);

        assertEquals(1, diff.size());
        assertNotNull("diff should contain 'a'", diff.get("a"));
        assertTrue(diff.get("a").isNull());
    }

    @Test
    public void testNullToValue() throws Exception {
        ObjectNode diff = diff(
                """
                {"a": null}
                """,
                """
                {"a": "value"}
                """);

        assertEquals(1, diff.size());
        assertNotNull("diff should contain 'a'", diff.get("a"));
        assertEquals("value", diff.get("a").asText());
    }

    @Test
    public void testEmptyObjects() throws Exception {
        ObjectNode diff = diff("{}", "{}");

        assertTrue(diff.isEmpty());
    }

    @Test
    public void testDeeplyNestedChange() throws Exception {
        ObjectNode diff = diff(
                """
                {"l1": {"l2": {"l3": {"deep": "old", "keep": true}}}}
                """,
                """
                {"l1": {"l2": {"l3": {"deep": "new", "keep": true}}}}
                """);

        assertEquals(1, diff.size());
        assertNotNull("diff should contain 'l1'", diff.get("l1"));
        assertNotNull("l1 diff should contain 'l2'", diff.get("l1").get("l2"));
        JsonNode l3Diff = diff.get("l1").get("l2").get("l3");
        assertNotNull("l2 diff should contain 'l3'", l3Diff);
        assertEquals(1, l3Diff.size());
        assertNotNull("l3 diff should contain 'deep'", l3Diff.get("deep"));
        assertEquals("new", l3Diff.get("deep").asText());
    }

    @Test
    public void testObjectReplacedWithPrimitive() throws Exception {
        ObjectNode diff = diff(
                """
                {"a": {"nested": true}}
                """,
                """
                {"a": "flat"}
                """);

        assertEquals(1, diff.size());
        assertNotNull("diff should contain 'a'", diff.get("a"));
        assertEquals("flat", diff.get("a").asText());
    }

    @Test
    public void testPrimitiveReplacedWithObject() throws Exception {
        ObjectNode diff = diff(
                """
                {"a": "flat"}
                """,
                """
                {"a": {"nested": true}}
                """);

        assertEquals(1, diff.size());
        assertNotNull("diff should contain 'a'", diff.get("a"));
        assertTrue(diff.get("a").isObject());
        assertNotNull("nested diff should contain 'nested'", diff.get("a").get("nested"));
        assertTrue(diff.get("a").get("nested").asBoolean());
    }

    private ObjectNode diff(String original, String modified) throws IOException {
        JsonNode originalNode = OutputUtil.MAPPER.readTree(original);
        JsonNode modifiedNode = OutputUtil.MAPPER.readTree(modified);
        return KcAdmV2EditCmd.computeMergePatchDiff(originalNode, modifiedNode);
    }
}
