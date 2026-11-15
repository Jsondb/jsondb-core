[![CircleCI](https://circleci.com/gh/Jsondb/jsondb-core.svg?style=shield)](https://circleci.com/gh/Jsondb/jsondb-core) [![Coveralls](https://img.shields.io/coveralls/Jsondb/jsondb-core/master.svg)](https://coveralls.io/github/Jsondb/jsondb-core?branch=master)

JsonDB is a pure java database that stores its data as Json Files.

A very small memory footprint, runs embedded within your java program.

Provides APIs that are very similar and inspired by MongoDB.

Provides a Apache JxPath based query system.

Visit https://jsondb.github.io  for detailed information about Jsondb, Why Jsondb and Using Jsondb

## Installation

JsonDB runs on Java 8 or later. It is published to Maven Central:

```xml
<dependency>
  <groupId>io.jsondb</groupId>
  <artifactId>jsondb-core</artifactId>
  <version>1.1.0</version>
</dependency>
```

```groovy
implementation 'io.jsondb:jsondb-core:1.1.0'
```

On Android (API 26+) or under OSGi, where package scanning cannot find your classes, pass the entity classes directly:

```java
JsonDBTemplate db = new JsonDBTemplate(dbFilesLocation, List.of(User.class, Site.class));
```

See [CHANGELOG.md](CHANGELOG.md) for what changed, including behaviour changes to check when upgrading from 1.0.x.




## Stargazers over time

[![Stargazers over time](https://starchart.cc/Jsondb/jsondb-core.svg)](https://starchart.cc/Jsondb/jsondb-core)




