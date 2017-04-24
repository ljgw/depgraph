package com.winkelhagen.maven.depgraph;

import com.winkelhagen.maven.depgraph.graph.DependencyEdge;
import com.winkelhagen.maven.depgraph.graph.DependencyVertex;
import com.winkelhagen.maven.depgraph.graph.Scope;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;
import org.jgrapht.graph.DirectedMultigraph;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * DependencyVisitor that adds the underlying artifacts (as vertices) and dependencies themselves (as edges) it finds to a {@link org.jgrapht.Graph}.
 */
public class GraphCollectingDependencyVisitor implements DependencyVisitor {

    private final DirectedMultigraph<DependencyVertex, DependencyEdge> graph;
    private final String root;
    private Deque<DependencyNode> stack = new ArrayDeque<>();
    private Deque<DependencyVertex> vertexStack = new ArrayDeque<>();


    /**
     * constructor that takes the mandatory graph and root-name.
     * @param graph the graph to add the artifacts and dependencies to
     * @param root the name of the root-node.
     */
    public GraphCollectingDependencyVisitor(DirectedMultigraph<DependencyVertex, DependencyEdge> graph, String root) {
        this.graph = graph;
        this.root = root;
    }

    /**
     * {@inheritDoc}
     *
     * @return always true - visit all children
     */
    @Override
    public boolean visitEnter(DependencyNode node) {
        DependencyNode parent = stack.peek();
        DependencyVertex parentVertex = vertexStack.peek();
        stack.push(node);
        DependencyVertex vertex;
        if (parent!=null) {
            vertex = new DependencyVertex(node.getDependency().getArtifact().toString(), Scope.byName(node.getDependency().getScope()));
        } else {
            vertex = new DependencyVertex(root, Scope.ROOT);
        }
        vertexStack.push(vertex);
        graph.addVertex(vertex);
        if (parentVertex!=null){
            DependencyEdge edge = new DependencyEdge(Scope.byName(node.getDependency().getScope()));
            graph.addEdge(parentVertex, vertex, edge);
        }
        return true;
    }

    /**
     * {@inheritDoc}
     *
     * @return always true - visit all siblings
     */
    @Override
    public boolean visitLeave(DependencyNode node) {
        vertexStack.pop();
        return true;
    }
}
