package uk.gov.moj.cpp.service.insights.model;


public class Node {
    private final String id;
    private final String label;
    private final String type;
    private final String parent; // Optional

    // Constructor without parent
    public Node(String id, String label, String type) {
        this(id, label, type, null);
    }

    // Constructor with parent
    public Node(String id, String label, String type, String parent) {
        this.id = id;
        this.label = label;
        this.type = type;
        this.parent = parent;
    }

    // Getters
    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public String type() {
        return type;
    }

    public String parent() {
        return parent;
    }
}

