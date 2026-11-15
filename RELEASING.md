# Releasing jsondb-core to Maven Central

Releases are uploaded from the maintainer's machine to the [Sonatype Central Portal](https://central.sonatype.com) as a deployment that **waits for a manual Publish click**. Nothing reaches Maven Central until it is published on the portal. **A published version can never be changed or deleted.**

## Versions

The version is computed from git: `<line>.<commits since the line tag>`.

- On the commit tagged `1.1`, the version is `1.1.0`.
- Five commits later, it is `1.1.5`.

There are two kinds of tag:
- **Version-line tags** are bare: `1.0`, `1.1`. Only these drive the version; the build runs `git describe --match '[0-9]*'`.
- **Release tags** have a `v` prefix: `v1.0.115`, `v1.1.0`. They mark exactly what was published and carry the GitHub release. They never affect the version.

To start a new minor line, tag the release commit, e.g. `1.2`. Run `./gradlew help -q` to print the version of the current commit.

The code is compiled for Java 8 and must keep working there. `./gradlew testOnJava8` runs the tests on a Java 8 runtime, and Gradle downloads one if needed.

## One-time setup

1. **Central Portal token.** Sign in at central.sonatype.com (the `io.jsondb` namespace belongs to the maintainer's account) and generate a user token under *View Account → Generate User Token*.
2. **Signing key.** Install GnuPG (`brew install gnupg pinentry-mac`). Import the signing key, or create one, and publish its public key to `keyserver.ubuntu.com` and `keys.openpgp.org`. Keep a backup of the secret key and its revocation certificate somewhere safe, outside this machine.
3. **`~/.gradle/gradle.properties`** (in your home directory, never in this repository):

   ```properties
   mavenCentralUsername=<token username>
   mavenCentralPassword=<token password>
   signing.gnupg.keyName=<signing key fingerprint>
   signing.gnupg.executable=gpg
   ```

   Lock the file down: `chmod 600 ~/.gradle/gradle.properties`.

## Each release

1. Make sure `master` has everything for the release, CI is green, and the `## Unreleased` section of `CHANGELOG.md` describes the changes. The section is renamed only after the release (step 8), because every commit changes the version number.
2. Check out the exact commit to release and confirm the tree is clean:

   ```sh
   git checkout master && git pull --ff-only && git status
   ```

3. For a new minor line, tag the commit and push the tag:

   ```sh
   git tag -a 1.1 -m "jsondb-core 1.1"
   git push origin 1.1
   ```

   For a patch release on an existing line, skip this; the version is the commit count since the tag.
4. Check the version, then upload:

   ```sh
   ./gradlew help -q          # prints "Build Version: ..."
   ./gradlew clean test publishToMavenCentral
   ```

   GnuPG asks for the key's passphrase. The task prints the deployment id.
5. On central.sonatype.com, open *Deployments*. Wait for the deployment to be **Validated**, check the files (jar, sources, javadoc, pom and a `.asc` signature for each), then click **Publish**. To abandon a bad upload, click **Drop** instead.
6. After 10–30 minutes, confirm it is available at `https://repo1.maven.org/maven2/io/jsondb/jsondb-core/`.
7. Still on the published commit, create the GitHub release:

   ```sh
   scripts/github-release.sh 1.1.0
   ```

   The script checks that the current commit builds that version, tags it `v1.1.0`, pushes the tag, and creates the release from the `## 1.1.0` section of `CHANGELOG.md` if there is one, otherwise from `## Unreleased`, marked **Latest**. It needs `gh` signed in with write access to the repository.
8. In a follow-up PR, rename `## Unreleased` in `CHANGELOG.md` to the version that was published, e.g. `## 1.1.4`, and add a new empty `## Unreleased` section above it.
9. Update the version on the website's download page.
