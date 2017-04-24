package com.winkelhagen.maven.depgraph.graph;

import java.util.HashMap;
import java.util.Map;

/**
 * a vertex in our graph. Corresponds to a (maven) artifact as it is uniquely identified by the artifact-name.
 * todo: improve constructors
 */
public class DependencyVertex {

    /**
     * the name of the dependencyVertex. Used as the sole basis for identification of a dependencyVertex.
     * This name is based on the (maven) artifact that is the basis for this vertex in the dependency graph.
     */
    private String name;

    /**
     * the {@link Scope} of this dependencyVertex. There might be secondary scopes that are ignored by maven.
     * DependencyVertices that are ignored as a whole might have multiple scopes also, but these would all have the same tier.
     */
    private Scope primaryScope;

    /**
     * if the dependencyVertex is ignored it is not used by maven.
     * This mostly happens because there might be <i>other versions</i> of the same artifact that are used by maven instead.
     */
    private boolean ignored;

    /**
     * create a new DependencyVertex for this artifact-name. Vertices constructed like this should not be added to a graph.
     * Such vertices are only used as a pointer a DependencyVertex already in the graph, to use in a new edge.
     * todo: find a way to force this.
     * @param name the name of the vertex, should be the derived from the artifact
     */
    public DependencyVertex(String name) {
        this(name, null, false);
    }

    /**
     * create a new, not-ignored, DependencyVertex for this artifact-name with a specified scope.
     * DependencyVertices created using this constructor are expected to be added to the graph before any 'ignored' DependencyVertices.
     * Such vertices are created by the real dependency tree and reflect the true scope of the artifact.
     * @param name the name of the vertex, should be the derived from the artifact
     * @param primaryScope the scope of the dependency targeting the artifact
     */
    public DependencyVertex(String name, Scope primaryScope) {
        this(name, primaryScope, false);
    }

    /**
     * create a new, possibly ignored, DependencyVertex for this artifact-name with a specified scope.
     * 'ignored' DependencyVertices created using this constructor might already exist in the graph.
     * In that case the suggestedPrimaryScope will not be saved to the graph.
     * (todo: save it as secondary scope (or primary if the existing artifact is also a 'ignored' artifact)
     * Vertices created by directly using this constructor are based on target artifacts found during secondary investigation of the dependency tree.
     * @param name the name of the vertex, should be the derived from the artifact
     * @param suggestedPrimaryScope the scope of the dependency targeting the artifact. Might not be the primary scope in the full graph
     * @param ignored true iff found during secondary investigation of the dependency tree.
     */
    public DependencyVertex(String name, Scope suggestedPrimaryScope, boolean ignored) {
        this.name = name;
        this.primaryScope = suggestedPrimaryScope;
        this.ignored = ignored;
    }

    public boolean isIgnored() {
        return ignored;
    }

    @Override
    public String toString(){
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DependencyVertex vertex = (DependencyVertex) o;

        return name != null ? name.equals(vertex.name) : vertex.name == null;
    }

    @Override
    public int hashCode() {
        return name != null ? name.hashCode() : 0;
    }

    /**
     * helper method for the dotExporter to map attributes of the vertex to colors and styles
     * @param dependencyVertex a dependencyVertex
     * @return the colors and styles to be used when exporting
     */
    public static Map<String, String> getComponentAttributes(DependencyVertex dependencyVertex) {
        Map<String, String> map = new HashMap<>();
        if (dependencyVertex.isIgnored()){
            map.put("style", "dotted");
        }
        map.put("color", dependencyVertex.getPrimaryScope().getColor());
        return map;
    }

    public Scope getPrimaryScope() {
        return primaryScope;
    }
}
