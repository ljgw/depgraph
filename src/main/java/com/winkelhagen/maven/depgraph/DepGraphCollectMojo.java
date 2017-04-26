package com.winkelhagen.maven.depgraph;

import com.winkelhagen.maven.depgraph.graph.DependencyEdge;
import com.winkelhagen.maven.depgraph.graph.DependencyVertex;
import com.winkelhagen.maven.depgraph.graph.Scope;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecutionException;

import org.apache.maven.plugins.annotations.*;
import org.apache.maven.project.*;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;
import org.eclipse.aether.util.filter.PatternInclusionsDependencyFilter;
import org.eclipse.aether.util.graph.visitor.FilteringDependencyVisitor;
import org.eclipse.aether.util.graph.visitor.TreeDependencyVisitor;
import org.jgrapht.ext.*;
import org.jgrapht.graph.DirectedMultigraph;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * mojo to generate a visualizable dependency tree that includes all dependencies ignored by maven 3.
 * todo: investigate
 */
@Mojo( name = "depgraph", defaultPhase = LifecyclePhase.PRE_SITE)
public class DepGraphCollectMojo extends AbstractMojo {

    private DirectedMultigraph<DependencyVertex, DependencyEdge> graph;
    private DOTExporter<DependencyVertex, DependencyEdge> dotExporter;

    @Component
    private RepositorySystem repositorySystem;

    @Component
    private ProjectBuilder mavenProjectBuilder;

    @Component
    private MavenProject mavenProject;

    @Component
    private ProjectDependenciesResolver projectDependenciesResolver;

    @Component
    private BuildPluginManager pluginManager;

    @Parameter( defaultValue = "depgraph.gv", property = "outputFile" )
    private String outputFile;

    @Parameter( property = "includes" )
    private String includes;

    @Parameter( defaultValue="${repositorySystemSession}")
    private RepositorySystemSession repositorySystemSession;

    private PatternInclusionsDependencyFilter inclusionsDependencyFilter;

    /**
     * <ul>
     *     <li>setup filtering</li>
     *     <li>configure dotExporter</li>
     *     <li>create real dependency tree</li>
     *     <li>add ignored dependencies</li>
     * </ul>
     * @throws MojoExecutionException if anything goes wrong during execution
     */
    public void execute() throws MojoExecutionException {
        if (includes!=null){
            inclusionsDependencyFilter = new PatternInclusionsDependencyFilter(includes.split(","));
        } else {
            inclusionsDependencyFilter = null;
        }
        graph = new DirectedMultigraph<>(DependencyEdge.class);
        configureDOTExporter();
        createTrueDependencyGraph();
        addIgnoredDependencies();
        Path buildDir = Paths.get(mavenProject.getBuild().getDirectory());
        if (!Files.exists(buildDir)){
            try {
                Files.createDirectory(buildDir);
            } catch (IOException e) {
                throw new MojoExecutionException("problem creating build directory " + buildDir.toString(), e);
            }
        }
        String file = buildDir.resolve(outputFile).toString();
        try (FileOutputStream fos = new FileOutputStream(file)){
            dotExporter.exportGraph(graph, fos);
        } catch (ExportException | IOException e) {
            throw new MojoExecutionException("problem exporting to file " + file, e);
        }

    }

    /**
     * setup the way the graph is exported to DOT (= .gv) format.
     */
    private void configureDOTExporter() {
        dotExporter = new DOTExporter<>(
                new IntegerComponentNameProvider<>(),
                new StringComponentNameProvider<>(),
                null, //DependencyEdge::getScopeName,
                DependencyVertex::getComponentAttributes,
                DependencyEdge::getComponentAttributes);
    }

    /**
     * creates the dependency tree that would have been created by dependency:tree
     * @throws MojoExecutionException when unable to resolve dependencies of the mavenProject
     */
    private void createTrueDependencyGraph() throws MojoExecutionException {
        try {
            DependencyVisitor visitor = new TreeDependencyVisitor(new FilteringDependencyVisitor(
                    new GraphCollectingDependencyVisitor(graph, mavenProject.getArtifact().toString()),
                    inclusionsDependencyFilter
            ));
            DependencyNode dependencyNode = getRootDependencyNodeFromProject();

            dependencyNode.accept(visitor);

        } catch (DependencyResolutionException e) {
            throw new MojoExecutionException("unable to create the true dependencyGraph of " + mavenProject.getArtifact().toString(), e);
        }
    }

    /**
     * returns the root dependencyNode of the mavenProject.
     * @return the root dependencyNode of the mavenProject.
     * @throws DependencyResolutionException when dependency resolution fails
     */
    private DependencyNode getRootDependencyNodeFromProject() throws DependencyResolutionException {
        DefaultDependencyResolutionRequest dependencyResolutionRequest = new DefaultDependencyResolutionRequest(mavenProject, repositorySystemSession);
        return projectDependenciesResolver.resolve(dependencyResolutionRequest).getDependencyGraph();
    }

    /**
     * add the ignored dependencies to the graph.
     * This is done by collecting all direct dependencies of the project and then collecting all their direct dependencies transitively.
     * This is achieved by setting up a new mavenProject for each dependency.
     * @throws MojoExecutionException when unable to build the project for any included dependency
     */
    private void addIgnoredDependencies() throws MojoExecutionException {
        Set<String> uniqueDependencies = new HashSet<>();
        Queue<Dependency> dependencyQueue = new LinkedList<>();
        try {
            getDirectProjectDependencies().stream()
                    .peek((d) -> output(mavenProject, d))
                    .peek((d) -> addToGraph(mavenProject, d))
                    .filter((d) -> uniqueDependencies.add(uniqueName(d)))
                    .forEach(dependencyQueue::add);
        } catch (DependencyResolutionException e) {
            //TODO: perhaps filter these nodes - maybe with a note to the node.
            throw new MojoExecutionException("Unable to build project: "
                    + mavenProject.toString(), e);
        }
        while (true){
            Dependency dependency = dependencyQueue.poll();
            if (dependency == null){
                break;
            }
            try {
                getDirectProjectDependencies(dependency).stream()
                        .map((d) -> adjustScope(d, dependency))
                        .filter((d) -> d.getScope() != null)
                        .peek((d) -> output(dependency, d))
                        .peek((d) -> addToGraph(dependency, d))
                        .filter((d) -> uniqueDependencies.add(uniqueName(d)))
                        .forEach(dependencyQueue::add);
            } catch (DependencyCollectionException e) {
                //TODO: perhaps filter these nodes - maybe with a note to the node.
                throw new MojoExecutionException("Unable to build project: "
                        + dependency.toString(), e);
            }
        }
    }

    /**
     * adds the dependency to the graph, if it did not contain it.
     * @param project the project (to be converted to a vertex) that is the root of graph.
     * @param dependency the dependency that might result in a target vertex and a new edge
     */
    private void addToGraph(MavenProject project, Dependency dependency) {
        DependencyVertex sourceVertex = new DependencyVertex(project.getArtifact().toString());
        addToGraph(sourceVertex, dependency);
    }

    /**
     * adds the dependency to the graph, if it did not contain it.
     * @param sourceDependency the sourceDependency that should already be contained by the graph.
     * @param dependency the dependency that might result in a target vertex and a new edge
     */
    private void addToGraph(Dependency sourceDependency, Dependency dependency) {
        DependencyVertex sourceVertex = new DependencyVertex(sourceDependency.getArtifact().toString());
        addToGraph(sourceVertex, dependency);
    }

    /**
     * adds the dependency to the graph, if it did not contain it.
     * @param sourceVertex the source vertex that should already be contained by the graph.
     * @param dependency the dependency that might result in a target vertex and a new edge
     */
    private void addToGraph(DependencyVertex sourceVertex, Dependency dependency) {
        DependencyEdge edge = new DependencyEdge(Scope.byName(dependency.getScope()));
        DependencyVertex targetVertex = new DependencyVertex(dependency.getArtifact().toString(), Scope.byName(dependency.getScope()), true);
        graph.addVertex(targetVertex);
        edge.setIgnored(true);
        graph.addEdge(
                sourceVertex,
                targetVertex,
                edge
        );
    }

    /**
     * todo: do this without setting the scope of the dependency (which is a hack)
     * adjusts the scope of a dependency based on the scope of the parent dependency
     * @param dependency the dependency whose scope might be adjusted
     * @param parentDependency the parent dependency whose scope influences the new scope
     * @return a new dependency with the adjusted scope, or the same dependency with the same scope. Scope might be set to null, which will filter out the dependency from processing
     */
    private Dependency adjustScope(Dependency dependency, Dependency parentDependency) {
        if ("compile".equalsIgnoreCase(dependency.getScope()) || ("runtime".equalsIgnoreCase(dependency.getScope()) && !"compile".equalsIgnoreCase(parentDependency.getScope()))){
            return dependency.setScope(parentDependency.getScope());
        } else if ("test".equalsIgnoreCase(dependency.getScope()) || "provided".equalsIgnoreCase(dependency.getScope())){
            return dependency.setScope(null);
        }
        return dependency;
    }

    /**
     * logs a dependency for debug purposes
     * @param source the source mavenProject or dependency
     * @param target the target (or actual) dependency that the source depends upon
     */
    private void output(Object source, Dependency target) {
        if (getLog().isDebugEnabled()) {
            getLog().debug( source + " -> " + target + " (" + target.getScope() + ")");
        }
    }

    /**
     * builds the unique name for this dependency to avoid double dependencies and loops.
     * @param dependency the dependency
     * @return a unique name for this dependency
     */
    private String uniqueName(Dependency dependency){
        org.eclipse.aether.artifact.Artifact artifact = dependency.getArtifact();
        return artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getExtension() + ":" + artifact.getVersion() + ":" + artifact.getClassifier();
    }

    /**
     * takes a aether dependency and returns the list of direct dependencies that are included in this analysis.
     * @param dependency the dependency to convert
     * @return the list of direct dependencies
     * @throws DependencyCollectionException when dependency collection fails
     */
    private List<Dependency> getDirectProjectDependencies(Dependency dependency) throws DependencyCollectionException {
        CollectRequest collectRequest = new CollectRequest(dependency, null);
        return repositorySystem.collectDependencies(repositorySystemSession, collectRequest).getRoot().getChildren().stream()
                .filter((c) -> inclusionsDependencyFilter == null || inclusionsDependencyFilter.accept(c, null))
                .map(DependencyNode::getDependency).collect(Collectors.toList());
    }

    /**
     * returns the list of direct dependencies of the root mavenProject that are included in this analysis.
     * @return the list of direct dependencies
     * @throws DependencyResolutionException when dependency resolution fails
     */
    private List<Dependency> getDirectProjectDependencies() throws DependencyResolutionException {
        return getRootDependencyNodeFromProject().getChildren().stream()
                .filter((c) -> inclusionsDependencyFilter == null || inclusionsDependencyFilter.accept(c, null))
                .map(DependencyNode::getDependency).collect(Collectors.toList());
    }

}
