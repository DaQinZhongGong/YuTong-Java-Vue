package com.yutong.ai.rag.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphExtractionParserTest {

    @Test
    void parse_shouldReadMarkdownFencedJson() {
        String raw = """
                ```json
                {"nodes":[{"id":"雨桐","label":"雨桐","type":"ORG"}],"edges":[{"source":"雨桐","target":"YuTong","relation":"又名"}]}
                ```
                """;
        GraphExtractionParser.GraphSnapshot snap = GraphExtractionParser.parse(raw);
        assertEquals(1, snap.nodes().size());
        assertEquals("雨桐", snap.nodes().get(0).id());
        assertEquals(1, snap.edges().size());
        assertEquals("又名", snap.edges().get(0).relation());
    }

    @Test
    void parse_shouldAcceptEntitiesAlias() {
        String raw = "{\"entities\":[{\"name\":\"Alice\",\"type\":\"PERSON\"}],\"relations\":[{\"from\":\"Alice\",\"to\":\"Bob\",\"type\":\"KNOWS\"}]}";
        GraphExtractionParser.GraphSnapshot snap = GraphExtractionParser.parse(raw);
        assertEquals("Alice", snap.nodes().get(0).id());
        assertEquals("PERSON", snap.nodes().get(0).type());
        assertEquals("KNOWS", snap.edges().get(0).relation());
    }

    @Test
    void parse_shouldReturnEmptyOnGarbage() {
        GraphExtractionParser.GraphSnapshot snap = GraphExtractionParser.parse("not json at all");
        assertTrue(snap.nodes().isEmpty());
        assertTrue(snap.edges().isEmpty());
    }

    @Test
    void merge_shouldDedupeNodes() {
        GraphExtractionParser.GraphSnapshot a = GraphExtractionParser.parse(
                "{\"nodes\":[{\"id\":\"A\",\"label\":\"A\"}],\"edges\":[{\"source\":\"A\",\"target\":\"B\",\"relation\":\"R\"}]}");
        GraphExtractionParser.GraphSnapshot b = GraphExtractionParser.parse(
                "{\"nodes\":[{\"id\":\"A\",\"label\":\"A2\"},{\"id\":\"B\",\"label\":\"B\"}],\"edges\":[]}");
        GraphExtractionParser.GraphSnapshot merged = GraphExtractionParser.merge(java.util.List.of(a, b));
        assertEquals(2, merged.nodes().size());
        assertEquals(1, merged.edges().size());
        assertTrue(GraphExtractionParser.toGraphJson(merged).contains("\"id\":\"A\""));
    }
}
