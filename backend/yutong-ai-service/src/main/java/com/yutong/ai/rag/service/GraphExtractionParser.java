package com.yutong.ai.rag.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 从 LLM 输出解析知识图谱 JSON。容忍 markdown 围栏与多余前后文。
 */
public final class GraphExtractionParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private GraphExtractionParser() {
    }

    public record Node(String id, String label, String type) {}

    public record Edge(String source, String target, String relation) {}

    public record GraphSnapshot(List<Node> nodes, List<Edge> edges) {}

    public static GraphSnapshot parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return new GraphSnapshot(List.of(), List.of());
        }
        String json = extractJsonObject(raw);
        try {
            JsonNode root = MAPPER.readTree(json);
            List<Node> nodes = new ArrayList<>();
            JsonNode nodeArr = firstArray(root, "nodes", "entities");
            if (nodeArr != null) {
                for (JsonNode n : nodeArr) {
                    String id = text(n, "id", "name", "label");
                    String label = text(n, "label", "name", "id");
                    String type = text(n, "type", "category");
                    if (id == null || id.isBlank()) {
                        continue;
                    }
                    nodes.add(new Node(id.trim(), label == null ? id : label.trim(),
                            type == null || type.isBlank() ? "ENTITY" : type.trim()));
                }
            }
            List<Edge> edges = new ArrayList<>();
            JsonNode edgeArr = firstArray(root, "edges", "relations", "relationships");
            if (edgeArr != null) {
                for (JsonNode e : edgeArr) {
                    String source = text(e, "source", "from", "head");
                    String target = text(e, "target", "to", "tail");
                    String relation = text(e, "relation", "type", "label");
                    if (source == null || target == null) {
                        continue;
                    }
                    edges.add(new Edge(source.trim(), target.trim(),
                            relation == null || relation.isBlank() ? "RELATED_TO" : relation.trim()));
                }
            }
            return new GraphSnapshot(nodes, edges);
        } catch (Exception e) {
            return new GraphSnapshot(List.of(), List.of());
        }
    }

    public static GraphSnapshot merge(List<GraphSnapshot> parts) {
        Map<String, Node> nodes = new LinkedHashMap<>();
        List<Edge> edges = new ArrayList<>();
        for (GraphSnapshot part : parts) {
            if (part == null) {
                continue;
            }
            for (Node n : part.nodes()) {
                nodes.putIfAbsent(n.id(), n);
            }
            edges.addAll(part.edges());
        }
        return new GraphSnapshot(List.copyOf(nodes.values()), List.copyOf(edges));
    }

    public static String toGraphJson(GraphSnapshot snapshot) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            List<Map<String, String>> nodes = new ArrayList<>();
            for (Node n : snapshot.nodes()) {
                Map<String, String> row = new LinkedHashMap<>();
                row.put("id", n.id());
                row.put("label", n.label());
                row.put("type", n.type());
                nodes.add(row);
            }
            List<Map<String, String>> edges = new ArrayList<>();
            for (Edge e : snapshot.edges()) {
                Map<String, String> row = new LinkedHashMap<>();
                row.put("source", e.source());
                row.put("target", e.target());
                row.put("relation", e.relation());
                edges.add(row);
            }
            body.put("nodes", nodes);
            body.put("edges", edges);
            return MAPPER.writeValueAsString(body);
        } catch (Exception e) {
            return "{\"nodes\":[],\"edges\":[]}";
        }
    }

    static String extractJsonObject(String raw) {
        String text = raw.trim();
        if (text.startsWith("```")) {
            int nl = text.indexOf('\n');
            if (nl > 0) {
                text = text.substring(nl + 1);
            }
            int fence = text.lastIndexOf("```");
            if (fence >= 0) {
                text = text.substring(0, fence);
            }
            text = text.trim();
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    private static JsonNode firstArray(JsonNode root, String... keys) {
        for (String key : keys) {
            JsonNode n = root.get(key);
            if (n != null && n.isArray()) {
                return n;
            }
        }
        return null;
    }

    private static String text(JsonNode n, String... keys) {
        for (String key : keys) {
            JsonNode v = n.get(key);
            if (v != null && !v.isNull() && v.isValueNode()) {
                String s = v.asText();
                if (s != null && !s.isBlank()) {
                    return s;
                }
            }
        }
        return null;
    }
}
