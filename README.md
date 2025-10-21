# rpm-importer

This project produces a standalone CLI to import repositories from dist-git to gitlab and patch the resulting repository (on a per branch basis) with a suitable pom for building RPMs in PNC.

For all documentation see [here](https://project-ncl.github.io/rpm-importer).

For the documentation (in the `docs` directory), the theme can be previewed locally. Assuming `ruby-devel` is installed and `bundle install` has been run, then run `bundle jekyll serve -l -w -I`

To release run `mvn release:prepare release:perform -Prelease -Pjboss-release -Pgpg`

To download a snapshot version run `mvn dependency:copy -Dartifact='org.jboss.pnc:rpm-importer-parent:1.0.0-SNAPSHOT' -DoutputDirectory="$PWD"`
