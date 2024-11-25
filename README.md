
# Insights Maven Plugin

This Maven plugin (`uk.gov.moj.cpp.service:insights`) provides tools for managing and visualizing ACL (Access Control List) configurations, service insights, and schema visualizations. It is designed to assist in managing access control, schema dependencies, and service insight aggregates, with command and query API integrations. **The visualization feature is powered by Cytoscape**, a popular JavaScript library used to create interactive, graph-based representations.
## Table of Contents

- [Features](#features)
- [Requirements](#requirements)
- [Installation](#installation)
- [Configuration](#configuration)
- [Usage](#usage)
- [Building Locally](#building-locally)
- [Example](#example)

---

## Features

This plugin offers the following goals:

- **`service-insights`**: Aggregates service insights across different components.
- **`acl`**: Manages and extracts Access Control Lists (ACLs).
- **`visualize-schema`**: Generates a visual representation of schema dependencies and interactions.

## Requirements

- **Java**: 17 or higher
- **Maven**: 3.9.9 or higher

## Installation

Currently, this plugin is not available in Artifactory. Users must build the plugin locally and install it into their local Maven repository before use.

### Building Locally

To build and install the plugin locally, follow these steps:

1. Clone the repository:
   ```bash
   git clone <repository_url>
   cd insights
   ```

2. Build and install the plugin using Maven:
   ```bash
   mvn clean install
   ```

This will install the plugin into your local Maven repository, allowing it to be referenced as a dependency in other projects on your machine.

## Configuration

Add the plugin configuration to your CQRS service module `pom.xml` as follows:

```xml
<plugin>
    <groupId>uk.gov.moj.cpp.service</groupId>
    <artifactId>insights</artifactId>
    <version>1.0.2</version>
    <executions>
        <execution>
            <goals>
                <goal>service-insights</goal>
                <goal>acl</goal>
                <goal>visualize-schema</goal>
            </goals>
        </execution>
    </executions>
    <configuration>
        <rootDirectory>${project.basedir}/../</rootDirectory>
        <changeLogsDir>${project.basedir}/../service-name-viewstore/service-name-viewstore-liquibase</changeLogsDir>
        <commandApiDir>${project.basedir}/../service-name-command/service-name-command-api</commandApiDir>
        <queryApiDir>${project.basedir}/../service-name-query/service-name-query-api</queryApiDir>
    </configuration>
</plugin>
```

### Configuration Options

- **`rootDirectory`**: Root directory of the project, used for finding core files and resources for service-insights goal .
- **`changeLogsDir`**: Directory containing the Liquibase changelogs for the viewstore (goal visualize-schema).
- **`commandApiDir`**: Directory containing the command API source files (goal acl).
- **`queryApiDir`**: Directory containing the query API source files (goal acl).

## Usage

To execute the plugin, run the following command in the directory containing the `pom.xml`:

```bash
mvn uk.gov.moj.cpp.service:insights:1.0.2:<goal>
```

Replace `<goal>` with one of the available goals:

- `service-insights`
- `acl`
- `visualize-schema`

For example:

```bash
mvn uk.gov.moj.cpp.service:insights:1.0.2:service-insights
```

This will run the `service-insights` goal, producing output and any relevant files in the specified directories.

## Example

Here’s an example configuration within a `pom.xml` file for a project using this plugin (taken from hearing service):

```xml
<build>
    <plugins>
       <plugin>
          <groupId>uk.gov.moj.cpp.service</groupId>
          <artifactId>insights</artifactId>
          <version>1.0.2</version>
          <executions>
             <execution>
                <goals>
                   <goal>service-insights</goal>
                   <goal>acl</goal>
                   <goal>visualize-schema</goal>
                </goals>
             </execution>
          </executions>
          <configuration>
             <rootDirectory>${project.basedir}/../</rootDirectory>
             <changeLogsDir>${project.basedir}/../hearing-viewstore/hearing-viewstore-liquibase</changeLogsDir>
             <commandApiDir>${project.basedir}/../hearing-command/hearing-command-api</commandApiDir>
             <queryApiDir>${project.basedir}/../hearing-query/hearing-query-api</queryApiDir>
          </configuration>
       </plugin>
    </plugins>
</build>
```

After configuring this in your `pom.xml`, run:

```bash
mvn compile
```

This will execute the plugin's goals during the compile phase and output relevant artifacts and reports as configured.

---

### Additional Notes

- **Local Dependency**: Since the plugin is not available in Artifactory, remember that any other user will also need to build and install it locally to use it in their projects.
- **Troubleshooting**: Ensure that all directory paths specified in the configuration are valid to avoid errors during plugin execution.

---

This `README.md` file should give your users a comprehensive guide on installing, configuring, and using your Maven plugin.
