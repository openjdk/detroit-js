This sample shows how to use the org.openjdk.engine.javascript jar in a maven project.

Before being able to build this project, the jar has to be installed in the local maven repository.
The following command may be used for this:

```
mvn install:install-file \
    -Dfile=/path/to/org.openjdk.engine.javascript.jar \
    -DgroupId=org.openjdk \
    -DartifactId=engine-javascript \
    -Dversion=0.1-SNAPSHOT \
    -Dpackaging=jar
```

Alternatively, the make build has a `mvn-install` target which does the same.

After that, the project can be built, and tests can be run with:

```
mvn test
```
