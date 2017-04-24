package com.winkelhagen.maven.tools;

import org.apache.maven.model.Dependency;
import org.eclipse.aether.artifact.Artifact;

import java.util.List;
import java.util.regex.Pattern;

/**
 * <p>
 * Simple filter much alike {@link org.eclipse.aether.util.filter.PatternInclusionsDependencyFilter}:
 * </p>
 *
 * A simple filter to include artifacts from a list of patterns. The artifact pattern syntax is of the form:
 *
 * <pre>
 * [groupId]:[artifactId]:[extension]:[version]
 * </pre>
 *
 * Where each pattern segment is optional and supports full and partial <code>*</code> wildcards. An empty pattern
 * segment is treated as an implicit wildcard.
 * <p>
 * Note that version ranges are not supported. Also, null filters are not supported.
 * //todo: LW: cannot we somehow use PatternInclusionsDependencyFilter? works on aether dependencies though.
 * </p>
 */
public class SimpleIncludesFilter {
    private Pattern regex = null;

    /**
     * creates a new filter with the given parameters
     * @param includes the parameters in the form [groupId]:[artifactId]:[extension]:[version]. Cannot be null.
     */
    public SimpleIncludesFilter(String includes) {
        StringBuilder sb = new StringBuilder("^");

        String[] parts = includes.split(":");
        for (String part : parts){
            if (!part.equals("")){
                sb.append(part.replace("*","[^:]*")).append(":");
            } else {
                sb.append("[^:]*:");
            }
        }
        sb.append(".*");
        regex = Pattern.compile(sb.toString());
    }

    /**
     * helper method to get the unique dependencyString from a maven Dependency
     * @param dependency dependency to determine the dependencyString for
     * @return the dependencyString
     */
    public static String toDependencyString(Dependency dependency) {
        return dependency.getGroupId() + ":" +
                dependency.getArtifactId() + ":" +
                dependency.getType() + ":" +
                dependency.getVersion() + ":";
    }

    /**
     * helper method to get the unique dependencyString from an aether Artifact
     * @param artifact artifact to determine the dependencyString for
     * @return the dependencyString
     */
    public static String toDependencyString(Artifact artifact) {
        return artifact.getGroupId() + ":" +
                artifact.getArtifactId() + ":" +
                artifact.getExtension() + ":" +
                artifact.getBaseVersion() + ":";
    }

    /**
     * convenience method to evaluate if a dependency is to be included [in the analysis]
     * @param filters a list of {@link SimpleIncludesFilter}s
     * @param dependency the dependency to evaluate
     * @return true iff the dependency matches any filter
     */
    public static boolean isIncluded(List<SimpleIncludesFilter> filters, Dependency dependency){
        return isIncluded(filters, toDependencyString(dependency));
    }

    /**
     * convenience method to evaluate if an artifact is to be included [in the analysis]
     * @param filters a list of {@link SimpleIncludesFilter}s
     * @param artifact the artifact to evaluate
     * @return true iff the artifact matches any filter
     */
    public static boolean isIncluded(List<SimpleIncludesFilter> filters, Artifact artifact){
        return isIncluded(filters, toDependencyString(artifact));
    }

    /**
     * method to evaluate if the object identified by the dependencyString is to be included [in the analysis]
     * @param filters a list of {@link SimpleIncludesFilter}s
     * @param dependencyString the key of the object to evaluate
     * @return true iff the dependencyString matches any filter
     */
    public static boolean isIncluded(List<SimpleIncludesFilter> filters, String dependencyString){
        for (SimpleIncludesFilter filter : filters){
            if (filter.isIncluded(dependencyString)){
                return true;
            }
        }
        return false;
    }

    private boolean isIncluded(String dependencyString) {
        return regex.matcher(dependencyString).matches();
    }

}
