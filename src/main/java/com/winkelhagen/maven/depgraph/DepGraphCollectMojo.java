package com.winkelhagen.maven.depgraph;

import com.winkelhagen.maven.depgraph.graph.DependencyEdge;
import com.winkelhagen.maven.depgraph.graph.DependencyVertex;
import com.winkelhagen.maven.depgraph.graph.Scope;
import com.winkelhagen.maven.tools.SimpleIncludesFilter;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecutionException;

import org.apache.maven.plugins.annotations.*;
import org.apache.maven.project.*;
import org.apache.maven.repository.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.graph.DependencyVisitor;
import org.eclipse.aether.util.filter.PatternInclusionsDependencyFilter;
import org.eclipse.aether.util.graph.visitor.FilteringDependencyVisitor;
import org.eclipse.aether.util.graph.visitor.TreeDependencyVisitor;
import org.jgrapht.ext.*;
import org.jgrapht.graph.DirectedMultigraph;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;

/**
 * mojo to generate a visualizable dependency tree that includes all dependencies ignored by maven 3.
 */
@Mojo( name = "depgraph", defaultPhase = LifecyclePhase.GENERATE_SOURCES, requiresDependencyResolution = ResolutionScope.TEST)
public class DepGraphCollectMojo extends AbstractMojo {

    private static final int VERY_VERBOSE = 3;

    private DirectedMultigraph<DependencyVertex, DependencyEdge> graph;
    private DOTExporter<DependencyVertex, DependencyEdge> dotExporter;


    @Component
    private RepositorySystem repositorySystem;

    @Component
    private ProjectBuilder mavenProjectBuilder;

    @Component
    private MavenProject mavenProject;

    @Component
    private MavenSession mavenSession;

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

    private List<SimpleIncludesFilter> filters = null;

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
            filters = new ArrayList<>();
            Arrays.stream(includes.split(",")).forEach((i) -> filters.add(new SimpleIncludesFilter(i)));
        } else {
            filters = null;
        }
        graph = new DirectedMultigraph<>(DependencyEdge.class);
        configureDOTExporter();
        createTrueDependencyGraph();
        addIgnoredDependencies();
        File file = new File(mavenProject.getBuild().getDirectory(), outputFile);
        try (FileOutputStream fos = new FileOutputStream(file)){
            dotExporter.exportGraph(graph, fos);
        } catch (ExportException | IOException e) {
            throw new MojoExecutionException("problem exporting to file " + file.getAbsolutePath(), e);
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
            DefaultDependencyResolutionRequest defaultDependencyResolutionRequest = new DefaultDependencyResolutionRequest(mavenProject, repositorySystemSession);
            DependencyVisitor visitor = new TreeDependencyVisitor(new FilteringDependencyVisitor(
                    new GraphCollectingDependencyVisitor(graph, mavenProject.getArtifact().toString()),
                    includes==null ? null : new PatternInclusionsDependencyFilter(includes.split(","))
            ));
            projectDependenciesResolver.resolve(defaultDependencyResolutionRequest).getDependencyGraph().accept(visitor);

        } catch (DependencyResolutionException e) {
            throw new MojoExecutionException("unable to create the true dependencygraph of " + mavenProject.getArtifact().toString(), e);
        }
    }

    /**
     * method to determine if a dependency should be included [in the current analysis]
     * @param dependency the dependency to include
     * @return true iff there are no filter or if at least one filter includes the dependency
     */
    private boolean isIncluded(Dependency dependency){
        return filters == null || SimpleIncludesFilter.isIncluded(filters, dependency);
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
        mavenProject.getDependencies().stream()
                .filter(this::isIncluded)
                .peek((d) -> output(mavenProject, d))
                .peek((d) -> addToGraph(mavenProject, d))
                .filter((d) -> uniqueDependencies.add(uniqueName(d)))
                .forEach(dependencyQueue::add);
        while (true){
            Dependency dependency = dependencyQueue.poll();
            if (dependency == null){
                break;
            }
            try {
                getMavenProject(dependency).getDependencies().stream()
                        .filter(this::isIncluded)
                        .map((d) -> adjustScope(d, dependency))
                        .filter((d) -> d.getScope() != null)
                        .peek((d) -> output(dependency, d))
                        .peek((d) -> addToGraph(dependency, d))
                        .filter((d) -> uniqueDependencies.add(uniqueName(d)))
                        .forEach(dependencyQueue::add);
            } catch (ProjectBuildingException e) {
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
        DependencyVertex sourceVertex = new DependencyVertex(sourceDependency.getGroupId() + ":" + sourceDependency.getArtifactId() + ":" + sourceDependency.getType() + ":" + sourceDependency.getVersion());
        addToGraph(sourceVertex, dependency);
    }

    /**
     * adds the dependency to the graph, if it did not contain it.
     * @param sourceVertex the source vertex that should already be contained by the graph.
     * @param dependency the dependency that might result in a target vertex and a new edge
     */
    private void addToGraph(DependencyVertex sourceVertex, Dependency dependency) {
        DependencyEdge edge = new DependencyEdge(Scope.byName(dependency.getScope()));
        DependencyVertex targetVertex = new DependencyVertex(dependency.getGroupId() + ":" + dependency.getArtifactId() + ":" + dependency.getType() + ":" + dependency.getVersion(), Scope.byName(dependency.getScope()), true);
        graph.addVertex(targetVertex);
//        if (graph.containsEdge(sourceVertex, targetVertex)){
            edge.setIgnored(true);
//        }
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
     * @return the dependency with adjusted scope. Scope might be set to null, which will filter out the dependency from processing
     */
    private Dependency adjustScope(Dependency dependency, Dependency parentDependency) {
        if ("compile".equalsIgnoreCase(dependency.getScope()) || ("runtime".equalsIgnoreCase(dependency.getScope()) && !"compile".equalsIgnoreCase(parentDependency.getScope()))){
            dependency.setScope(parentDependency.getScope());
        } else if ("test".equalsIgnoreCase(dependency.getScope()) || "provided".equalsIgnoreCase(dependency.getScope())){
            dependency.setScope(null);
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
        return dependency.getGroupId() + ":" + dependency.getArtifactId() + ":" + dependency.getType() + ":" + dependency.getVersion() + ":" + dependency.getScope();
    }

    /**
     * //todo: investigate rewrite to return a list of aether dependencies so that we can use the PatternInclusionsDependencyFilter
     * takes a maven dependency and returns a maven project for building this dependency.
     * @param dependency the dependency to convert
     * @return the mavenProject that would build this dependency
     * @throws ProjectBuildingException
     */
    private MavenProject getMavenProject(Dependency dependency) throws ProjectBuildingException {
        Artifact pomArtifact = repositorySystem.createDependencyArtifact(dependency);
        ProjectBuildingResult build = mavenProjectBuilder.build(pomArtifact, mavenSession.getProjectBuildingRequest());
//        return build.getDependencyResolutionResult().getDependencyGraph().getChildren().stream().map((c) -> c.getDependency()).collect(Collectors.toList());
        return build.getProject();
    }

}
