package com.winkelhagen.maven.depgraph.graph;

import org.jgrapht.graph.DefaultEdge;

import java.util.HashMap;
import java.util.Map;

/**
 * an edge in our graph. Uniquely identified by the combination of source vertex, target vertex and scope.
 * Edges can be ignored by maven when another scope takes precedence or if the target vertex is ignored by maven also.
 */
public class DependencyEdge extends DefaultEdge {

    private Scope scope;
    private boolean ignored;

    /**
     * creates a dependencyEdge with the specified scope. SourceVertex and targetVertex are added when the vertex is added to the graph.
     * @param scope the scope
     */
    public DependencyEdge(Scope scope){
        this.scope = scope;
    }

    public Scope getScope(){
        return scope;
    }

    @Override
    public String toString()
    {
        return "(" + getSource() + " : " + getTarget() + " - " + scope + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DependencyEdge that = (DependencyEdge) o;

        if (getScope() != null ? !getScope().equals(that.getScope()) : that.getScope() != null) return false;
        if (getSource() != null ? !getSource().equals(that.getSource()) : that.getSource() != null) return false;
        return getTarget() != null ? getTarget().equals(that.getTarget()) : that.getTarget() == null;
    }

    @Override
    public int hashCode() {
        int result = getScope() != null ? getScope().hashCode() : 0;
        result = 31 * result + (getSource() != null ? getSource().hashCode() : 0);
        result = 37 * result + (getTarget() != null ? getTarget().hashCode() : 0);
        return result;
    }

    /**
     * helper method for the dotExporter to map attributes of the edge to colors and styles
     * @param dependencyEdge a dependencyEdge
     * @return the colors and styles to be used when exporting
     */
    public static Map<String,String> getComponentAttributes(DependencyEdge dependencyEdge) {
        Map<String, String> map = new HashMap<>();
        map.put("color", dependencyEdge.getScope().getColor());
        if (dependencyEdge.isIgnored()){
            map.put("style", "dotted");
        }
        return map;
    }

    public void setIgnored(boolean ignored) {
        this.ignored = ignored;
    }

    public boolean isIgnored() {
        return ignored;
    }

    /**
     * returns the (lowercase) name of the scope, for display purposes
     * @return the lowercase name of the scope.
     */
    public String getScopeName() {
        return getScope().name().toLowerCase();
    }
}
