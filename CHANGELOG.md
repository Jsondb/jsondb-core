# Changelog

## Unreleased

### New features

- A database can be opened read-only, e.g. on a read-only mount or to read another application's data safely. Collections load without lock files, so the folder does not have to be writable, and every operation that would change the database (insert, save, upsert, remove, the find-and-modify/remove operations, creating or dropping collections, schema updates, `changeEncryption` and `restore`) throws `InvalidJsonDbApiUsageException`. `JsonDBTemplate` gains a public constructor that takes a `JsonDBConfig`:

  ```java
  JsonDBConfig config = new JsonDBConfig(dbPath, List.of(User.class), null, false, null);
  config.setReadOnly(true);
  JsonDBTemplate jsonDBTemplate = new JsonDBTemplate(config);
  ```

- Documents with a numeric `@Id` (`Long`, `Integer`, `long` or `int`) inserted or upserted without an id now get the next number after the highest id in the collection, starting at 1. For `long` and `int`, 0 means no id. Until now such a document failed with `InvalidJsonDbApiUsageException` ("Failed to invoke setter method … due to wrong arguments") for a `Long` id, or was stored under 0 for an `int` id, so the next one failed as a duplicate. If you have an `int`/`long` id document stored under 0, `upsert` of it now inserts a new document instead of updating it; give it another id. Numbers are not a database sequence: when the document with the highest id is removed, that id is handed out again. `String` ids still get a random UUID. (#36)

- Ids for documents saved without one can come from your own `IdGenerator`, set for one class or as the default for all. It gets the collection name and the document, e.g. to build an id from its fields:

  ```java
  jsonDBTemplate.setIdGenerator(Order.class, (collection, order) -> "ORD-" + ((Order) order).getNumber());
  jsonDBTemplate.setIdGenerator((collection, document) -> ulids.next());   // every other collection
  ```

  JsonDB rejects an id that is null, of the wrong type or already in the collection with `InvalidJsonDbApiUsageException`, before anything is saved. The same setters are on `JsonDBConfig`.

- The database folder can be given as a `java.nio.file.Path`, on any file system, not only the default one: every `JsonDBTemplate` and `JsonDBConfig` constructor that takes the folder as a `String` has a `Path` twin. For example, a database in memory with Jimfs:

  ```java
  FileSystem fs = Jimfs.newFileSystem(Configuration.unix());
  JsonDBTemplate jsonDBTemplate = new JsonDBTemplate(fs.getPath("/db"), List.of(User.class));
  ```

  `backup()` and `restore()` take a `Path` too, so a database in memory can be backed up to disk and the other way round. A merging restore of a database that is not on the default file system extracts the backup to a temporary folder inside the database folder instead of the system's temporary folder, and deletes it afterwards. `JsonDBOperations` gains `backup(Path)` and `restore(Path, boolean)`, so a class of your own that implements it must add them. Code that passes a literal `null` as the folder or the backup path no longer compiles, because the call is ambiguous between the `String` and `Path` versions. (#17)

### Deprecations

- The methods that take or return a `java.io.File` have `Path` versions and will be removed in a future release: `JsonDBConfig.getDbFilesLocation()` (use `getDbFilesPath()`; it throws `UnsupportedOperationException` for a folder that is not on the default file system), `Util.delete(File)`, `Util.stampVersion(JsonDBConfig, File, String)`, the `File` constructors of `JsonReader` and `JsonWriter`, and the `File` methods of `JsonDbArchive`, including `resolveBackupZipFile(String)` and `resolveRestoreZipFile(String)`. Unlike `Util.delete(File)`, `Util.delete(Path)` deletes a symbolic link inside the folder instead of the contents of the folder it points to. (#17)

### Bug fixes

- In strict mode (`compatibilityMode` false), `updateCollectionSchema()` could not migrate a collection whose file still had keys the class no longer has: the file failed to load, so the update threw "Collection ... not found. Create collection first", and `createCollection()` refused because the file exists. The update now runs on the file even when the collection did not load, and loads it afterwards. (#84)
- A database folder that JsonDB cannot write to (e.g. a read-only mount, or files owned by another user) opened as an empty database: no collection loaded, and the only sign was an error in the log. Opening it now throws `InvalidJsonDbApiUsageException`, saying the folder is not writable and pointing to the read-only mode. (#111)

## 1.1.63

### New features

- `JsonDBTemplate.setSyncFolderOnWrite(true)` forces the database folder to disk after every write, so a write that returned is never lost in a crash or power loss. It makes writes several times slower, so it is off by default.

### Changes

- Errors that are thrown to the caller are no longer also logged at error level, so applications that log what they catch no longer see each failure twice. Where an exception used to drop its cause, which then only appeared in the log, it now carries it: creating or deleting a collection file, creating the database folder, and `DefaultAESCBCCipher`'s invalid-key error.

### Deprecations

- `JsonWriter.renameKeyInJsonFile()` and `Util.setFieldValueForEntity()` are no longer used by JsonDB and will be removed in a future release. Schema renames go through `updateCollectionSchema()`.

### Bug fixes

- When a write could not be saved (the collection's lock unavailable, or no temporary file could be created, e.g. in a read-only folder), `insert`, `save`, `upsert`, `remove`, `findAndRemove`, `findAllAndRemove`, `findAndModify` and `findAllAndModify` returned normally, and the remove methods returned `null`, as if the write had succeeded or matched nothing. They now throw `JsonDBException`, and the collection is unchanged in memory and on disk.
- A crash or power loss shortly after a write could leave a collection file empty or partly written, because the new file was renamed into place before its data reached the disk. Every write now flushes the file to disk before renaming it. In our measurements on an SSD this made writes no slower (about 0.4 ms per insert before and after).
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
