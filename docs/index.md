---
---


* Contents
{:toc}

### Overview

This tool allows the user to specify a branch in a dist-git (cgit) repository and it can import it into internal Red Hat CEE GitLab.

### Setup

This tool reuses the Bacon configuration file and so requires that you have a working [Bacon](https://project-ncl.github.io/bacon/) setup. The only addition is
for reqour:

```
  reqour:
    url: "https://reqour.pnc.engineering...."
```

You must be a member of `jboss-prod` so you have access to repositories created in CEE GitLab under the `pnc-workspace` group.


### Usage


Options:

```
Usage: rpm-importer [-hvV] [--overwrite] [--push] [--skip-sync] [--branch=<branch>] [-p=<configPath>] [--profile=<profile>] [--repository=<repository>]
                    --url=<url>

      --branch=<branch>     Branch in distgit repository
  -h, --help                Show this help message and exit.
      --overwrite           Overwrites existing pom. Dangerous!
  -p, --configPath=<configPath>
                            Path to PNC configuration folder
      --profile=<profile>   PNC Configuration profile
      --push                Pushes changes to the remote repository. Will still commit
      --repository=<repository>
                            Skips cloning and uses existing repository
      --skip-sync           Skips any syncing and only clones the repository and performs the patching
      --url=<url>           External URL to distgit repository
  -v, --verbose             Verbose output
  -V, --version             Print version information and exit.

```


Typical usage:

```
java -jar target/rpm-importer-parent-<version>.jar --url=https://pkgs.devel...../git/rpms/<repository> --branch=jb-eap-8.1-rhel-9
```

Notes:

* Unless `--push` is suppled the tool will only commit changes locally and **not** push to the remote. It is highly recommended that the user checks the resulting `pom.xml` before _manually_ running any git push.
* Skipping repository syncing only makes sense if the repository has already been mirrored to GitLab. This might be the case if the user is switching between multiple branches.
* Using an existing locally cloned repository is primarily useful for local debugging.
