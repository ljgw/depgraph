# depgraph
Maven plugin to generate transitive dependency graph information.

## usage
mvn com.winkelhagen:depgraph-maven-plugin:depgraph -Dincludes=<maven dependency style includes filter>
The includes parameter is optional, but not supplying it might cause some problems generating the dependency tree as it might fail to resolve some dependencies.
Running the goal will create a dependency graph and export it in DOT format file to target/depgraph.gv. On unix bases systems you can use xdot to view this dependency graph.

## 3rd party licences
This program depends on some 3rd party libraries that are distributed under their own terms.
Specifically it makes use of [JGraphT](http://jgrapht.org/) and [Apache Maven](https://maven.apache.org/).
The licences of the direct dependencies can be viewed in 3RD-PARTY-LICENCES.txt.