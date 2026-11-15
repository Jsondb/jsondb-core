# Changelog

## Unreleased

### New features

- `JsonDBTemplate.setSyncFolderOnWrite(true)` forces the database folder to disk after every write, so a write that returned is never lost in a crash or power loss. It makes writes several times slower, so it is off by default.

### Changes

- Errors that are thrown to the caller are no longer also logged at error level, so applications that log what they catch no longer see each failure twice. Where an exception used to drop its cause, which then only appeared in the log, it now carries it: creating or deleting a collection file, creating the database folder, and `DefaultAESCBCCipher`'s invalid-key error.

### Bug fixes

- When a write could not be saved (the collection's lock unavailable, or no temporary file could be created, e.g. in a read-only folder), `insert`, `save`, `upsert`, `remove`, `findAndRemove`, `findAllAndRemove`, `findAndModify` and `findAllAndModify` returned normally, and the remove methods returned `null`, as if the write had succeeded or matched nothing. They now throw `JsonDBException`, and the collection is unchanged in memory and on disk.
- A crash or power loss shortly after a write could leave a collection file empty or partly written, because the new file was renamed into place before its data reached the disk. Every write now flushes the file to disk first; on this Mac's SSD that cost no measurable time.
- Every write rejected because a collection is loaded read-only leaked two file handles, and a failed write of the temporary collection file left the `.tmp` file in the database folder.
- **Documents inserted without an id could be lost.** JsonDB generated an id for them but stored them under `null` instead of that id, so a second document without an id replaced the first in memory, and the next write dropped it from the file. `findById()` couldn't find them by their generated id either, and a batch `insert()`/`upsert()` of several such documents failed with "Duplicate object with id: null". Documents are now stored under their generated id.
- Nine operations (`insert`, `save`, `upsert` and `remove`, single and batch, plus `reloadCollection` and `isCollectionReadonly`) failed with a bare `NullPointerException` when given a collection name that no `@Document` class has, e.g. a typo. They now fail with an `InvalidJsonDbApiUsageException` naming the collection, like the other operations.
- Removing a file-change listener when none was registered threw `NullPointerException`.
- After the last file-change listener was removed, adding a listener again never restarted the file watcher, so the new listener received no events.
- The file watcher thread read the listener list without synchronisation, so adding or removing a listener while an event was being delivered could fail.

## 1.1.16

### Bug fixes

- Changing a document's `@Id` with `findAndModify()` or `findAllAndModify()` left the original document in place and added a copy under the new id. Such an update is now rejected with `InvalidJsonDbApiUsageException`; remove the document and insert it with the new id instead. Setting the id to its current value is still allowed. (#78)
- A `@Document` class without JsonDB's `@Id` annotation, for example one using `javax.persistence.Id`, failed with a bare `NullPointerException`. It now fails with an `InvalidJsonDbApiUsageException` naming the class and the annotation it needs. (#23)

### New

- `count(Class)` and `count(String collectionName)` return the number of documents in a collection without copying them, as `findAll()` does. (#54)
- `JsonDBTemplate` is now `AutoCloseable`. `close()` stops its file-change watcher and removes its JVM shutdown hook, which every instance used to leak, and releases its folder (see below). (#51)

### Changes

- JsonDB now logs a warning when a second `JsonDBTemplate` opens a folder that another instance in the same JVM already has open. Two instances on one folder each rewrite the files from their own in-memory copy and lose each other's writes. Share one instance between threads (it is thread-safe), or `close()` the other one first. A future release will refuse to open a folder twice. (#51)
- JsonDB no longer logs routine messages at INFO. "Encryption is (not) enabled" and the backup and restore notices are now DEBUG, so they don't appear with default logging settings. (#56)

## 1.1.0

The first release since 1.0.115 (March 2020). **It runs on Java 8 and later:** one artifact replaces the separate `-j8` and `-j11` builds.

### Security

- **Queries can no longer run Java code.** JXPath resolved extension functions to any static method or constructor on the classpath, so a query string such as `/.[java.lang.System.exit(0)]` executed arbitrary code. Applications that built queries from user input were open to remote code execution. Extension functions are now disabled; core XPath functions (`contains()`, `not()`, `count()`, ...) still work. (#67)
- **Dependencies with known vulnerabilities upgraded:** Jackson 2.18.8 → 2.23.0 (11 advisories, 5 high) and Guava 33.6.0 → 33.8.0 (1 high). An audit of every runtime, test and build dependency finds no known vulnerabilities. (#86)
- commons-beanutils, and the commons-collections it pulled in, are no longer dependencies. (#76)

### Android and OSGi

JsonDB now runs on Android (tested on an Android 16 / API 36 emulator) and under OSGi.

- New constructors take the entity classes directly instead of scanning a package, which cannot see application classes on Android or under OSGi: `new JsonDBTemplate(dbPath, List.of(User.class, Site.class))`. (#28, #74)
- Queries work without `java.beans`, which Android does not have. (#75)
- `findAndModify()` / `findAllAndModify()` no longer use commons-beanutils, which needs `java.beans`. (#76)
- The ciphers no longer require the `SunJCE` provider, which does not exist on Android. Existing encrypted data still decrypts. (#77)

### Bug fixes

- A collection file with one unparseable line could be overwritten and emptied by a later `getCollection()`. It is now left untouched. (#8, #68)
- A batch `upsert()` mixing new and existing documents silently dropped the updates. (#62, #71)
- A failed write was reported as success, leaving memory and disk out of step. (#15, #72)
- A thread that lost the race to create a collection kept its lock, hanging the collection. (#73)
- `updateCollectionSchema()`: renames lost their values, only the last of several renames applied, and a failure left the collection locked. (#57, #82)
- Merge-restore (`restore(path, true)`) encrypted `@Secret` fields a second time on encrypted databases. (#83)

### New

- `backup()` and `restore()` to and from a zip archive.
- `remove(Object)` without the entity class parameter. (#33)
- Better mapping of accessor methods. (#44)

### Behaviour changes

Check these when upgrading:

- **Queries:** JXPath extension functions now throw `JXPathException` instead of running.
- **Corrupt collection files:** `getCollection()` / `createCollection()` throw `InvalidJsonDbApiUsageException` for a collection file that exists but failed to load, instead of replacing it with an empty one.
- **Failed writes:** a write whose final file move fails throws `JsonDBException` instead of appearing to succeed.
- **`findAndModify()` / `findAllAndModify()` convert values with Jackson,** the same way the collection files are read. They now reject:
  - unknown properties;
  - `null` for primitives;
  - lossy number conversions (e.g. `7.9` into a `long`);
  - non-numeric strings for numbers;
  - boolean strings other than `"true"`/`"false"`.

  Enum names given as strings now work.
- **`updateCollectionSchema()`:**
  - `AddOperation` fills only documents where the key is missing or `null`; it no longer overwrites existing values.
  - Updates that would lose data are rejected before anything is written. That means a rename or add to a name the class does not have, a delete of a property the class still has, and a rename onto a key that already holds a value.

### Build

- **Build tooling:** the build runs on JDK 17 with Gradle 8.14 and compiles for Java 8. CI also runs every test on a Java 8 runtime.
- **CI:** CircleCI config 2.1. CI had silently stopped running after CircleCI retired config version 2.
- **Publishing:** releases are published through the Sonatype Central Portal. Previously they went through OSSRH, which Sonatype has shut down.
- **Versioning:** versions are `<line>.<commits since the line tag>`, without a Java suffix, starting from `1.1.0`. Each release is also tagged `v<version>` and published as a GitHub release.
