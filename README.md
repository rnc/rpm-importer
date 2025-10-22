# rpm-importer

This project produces a standalone CLI to import repositories from dist-git to gitlab and patch the resulting repository (on a per branch basis) with a suitable pom for building RPMs in PNC.

For all documentation see [here](https://project-ncl.github.io/rpm-importer).

### Developing Documentation

For the documentation (in the `docs` directory), the theme can be previewed locally. Assuming `ruby-devel` is installed and `bundle install` has been run, then run `bundle jekyll serve -l -w -I`

### Creating a release
To release run `mvn release:prepare release:perform -Prelease -Pjboss-release -Pgpg`

Note this assumes suitable gpg settings exist within `$HOME/.m2/settings.xml`.

### Downloading a snapshot

To download a snapshot version run `mvn dependency:copy -Dartifact='org.jboss.pnc:rpm-importer:1.0.0-SNAPSHOT' -DoutputDirectory="$PWD" -DcentralSnapshots`

This assumes you have a `$HOME/.m2/settings.xml` with a section like

```
  <profiles>
    <profile>
      <id>central-snapshots</id>
      <activation>
        <property>
          <name>centralSnapshots</name>
        </property>
      </activation>
      <repositories>
        <repository>
          <id>central-snapshots</id>
          <url>https://central.sonatype.com/repository/maven-snapshots</url>
          <releases>
            <enabled>false</enabled>
          </releases>
          <snapshots>
            <enabled>true</enabled>
          </snapshots>
        </repository>
      </repositories>
    </profile>
  </profiles>
```
