# Apache Flink Learning Environment (Java + Docker + VS Code)

This repository is a hands‑on LEARNING LAB for Apache Flink using Java.  
You will run a **local Flink cluster** (JobManager + TaskManager) inside Docker containers, while writing **multiple independent Java Flink projects** on your Mac in VS Code.  
Each project will build a **shaded (fat) JAR** that you drop into a shared `jars/` folder. The Dockerized Flink cluster automatically sees that folder (it is "mounted" inside the containers). You then submit jobs via:
- Flink Web UI (easiest)
- Flink CLI inside the JobManager container
- REST API (advanced)

You can keep adding more projects side‑by‑side (e.g. `flink-wordcount/`, `flink-kafka-demo/`, `flink-table-sql/`), all submitting to the SAME local cluster.  
This setup mimics a **session cluster** useful for experimentation and learning.

---

## Table of Contents

1. Concept Overview
2. High-Level Architecture
3. Prerequisites & Installation
4. Repository Layout
5. Step-by-Step Setup
6. Creating Your First Project (WordCount)
7. Building the Shaded JAR
8. Running / Submitting the Job (3 Methods)
9. Development Loop (Modify → Rebuild → Resubmit)
10. Common Errors & Fixes
11. Explaining the Files & Code
12. Adding More Projects
13. Cleaning Up
14. Next Learning Steps

---

## 1. Concept Overview

- **Apache Flink**: A framework for building real-time (stream) and batch data processing jobs.
- **JobManager**: Coordinates jobs (planning, scheduling).
- **TaskManager**: Executes tasks (your operators/functions run here).
- **Session Cluster**: A persistent cluster where you submit many different jobs.
- **Shaded JAR**: A single JAR that bundles your code + dependencies (except those Flink already provides) so the cluster can run it.

---

## 2. High-Level Architecture

```
Mac (Host)
│
├── VS Code (editing Java projects)
│
├── Docker (runs Flink services)
│   ├── Container: JobManager
│   └── Container: TaskManager (can scale to more)
│
└── Shared directory: ./jars  <-- mapped into containers at /opt/flink/usrlib
       ↑
   You copy built shaded JARs here
```

Flow:
1. You write code locally.
2. Build with Maven → produces `target/<project>-assembly.jar`.
3. Copy jar to `./jars/`.
4. Submit job (Web UI / CLI / REST).
5. View output in TaskManager logs.

---

## 3. Prerequisites & Installation

| Tool | Why Needed | Install (macOS) |
|------|------------|-----------------|
| Homebrew (optional) | Easy package installs | https://brew.sh |
| Java 17 JDK | Compile & run Java code | `brew install openjdk@17` or Temurin |
| Maven | Build Java (create shaded JAR) | `brew install maven` |
| Docker Desktop | Run Flink containers | https://www.docker.com/products/docker-desktop |
| VS Code | Editor | https://code.visualstudio.com |
| VS Code Extensions | Java support + Docker | Install: "Extension Pack for Java", "Docker" |
| (Optional) `tree`, `jq` | Structure display / JSON formatting | `brew install tree jq` |

Check versions:
```bash
java -version
mvn -version
docker version
docker compose version
```

If `mvn` says "command not found":
```bash
brew install maven
```

If Homebrew path not loaded (Apple Silicon):
```bash
echo 'eval "$(/opt/homebrew/bin/brew shellenv)"' >> ~/.zprofile
eval "$(/opt/homebrew/bin/brew shellenv)"
```

---

## 4. Repository Layout

After setup it will look like:

```
LEARN-apache-flink-java/
├── docker-compose.yml
├── jars/                   # holds all runnable shaded jars
├── flink-wordcount/         # first Java project
│   ├── pom.xml
│   └── src/...
└── (future projects) e.g. flink-kafka-demo/, flink-table-sql/, ...
```

---

## 5. Step-by-Step Setup

Create base folder and enter it:
```bash
mkdir -p ~/LEARN-apache-flink-java
cd ~/LEARN-apache-flink-java
```

Create `jars/` folder (shared volume):
```bash
mkdir -p jars
```

Create the Docker Compose file (see section 11 for explanation).  
File: `docker-compose.yml`:

```yaml
services:
  jobmanager:
    image: flink:1.19.1-scala_2.12-java17
    container_name: flink-jobmanager
    ports:
      - "8081:8081"
    environment:
      - |
        FLINK_PROPERTIES=
        jobmanager.rpc.address: jobmanager
        taskmanager.numberOfTaskSlots: 2
        parallelism.default: 2
    command: jobmanager
    volumes:
      - ./jars:/opt/flink/usrlib

  taskmanager:
    image: flink:1.19.1-scala_2.12-java17
    container_name: flink-taskmanager-1
    depends_on:
      - jobmanager
    environment:
      - |
        FLINK_PROPERTIES=
        jobmanager.rpc.address: jobmanager
        taskmanager.numberOfTaskSlots: 2
        parallelism.default: 2
    command: taskmanager
    volumes:
      - ./jars:/opt/flink/usrlib

networks:
  default:
    name: flink-net
```

Start the cluster:
```bash
docker compose up -d
```

Verify:
```bash
docker compose ps
```
Open the Flink Web UI: http://localhost:8081

(If you haven’t built a jar yet, UI will show an empty session.)

---

## 6. Creating Your First Project (WordCount)

Generate project via Maven Archetype (run inside repo root):
```bash
mvn archetype:generate \
  -DarchetypeGroupId=org.apache.flink \
  -DarchetypeArtifactId=flink-quickstart-java \
  -DarchetypeVersion=1.19.1 \
  -DgroupId=com.example \
  -DartifactId=flink-wordcount \
  -Dversion=1.0-SNAPSHOT \
  -Dpackage=com.example \
  -DinteractiveMode=false
```

Enter project:
```bash
cd flink-wordcount
```

Replace `pom.xml` with this (explanation in section 11):

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <groupId>com.example</groupId>
  <artifactId>flink-wordcount</artifactId>
  <version>1.0-SNAPSHOT</version>
  <name>flink-wordcount</name>

  <properties>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <maven.compiler.source>17</maven.compiler.source>
    <maven.compiler.target>17</maven.compiler.target>
    <flink.version>1.19.1</flink.version>
    <execution.mainClass>com.example.WordCount</execution.mainClass>
  </properties>

  <dependencies>
    <dependency>
      <groupId>org.apache.flink</groupId>
      <artifactId>flink-java</artifactId>
      <version>${flink.version}</version>
      <scope>provided</scope>
    </dependency>
    <dependency>
      <groupId>org.apache.flink</groupId>
      <artifactId>flink-streaming-java</artifactId>
      <version>${flink.version}</version>
      <scope>provided</scope>
    </dependency>
    <dependency>
      <groupId>org.slf4j</groupId>
      <artifactId>slf4j-log4j12</artifactId>
      <version>1.7.36</version>
      <scope>runtime</scope>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-shade-plugin</artifactId>
        <version>3.5.1</version>
        <executions>
          <execution>
            <phase>package</phase>
            <goals><goal>shade</goal></goals>
            <configuration>
              <createDependencyReducedPom>false</createDependencyReducedPom>
              <finalName>${project.artifactId}-assembly</finalName>
              <transformers>
                <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                  <mainClass>${execution.mainClass}</mainClass>
                </transformer>
              </transformers>
            </configuration>
          </execution>
        </executions>
      </plugin>

      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-compiler-plugin</artifactId>
        <version>3.11.0</version>
        <configuration>
          <source>${maven.compiler.source}</source>
          <target>${maven.compiler.target}</target>
        </configuration>
      </plugin>
    </plugins>
  </build>
</project>
```

Create the main class `src/main/java/com/example/WordCount.java`:

```java
package com.example;

import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.common.restartstrategy.RestartStrategies;
import org.apache.flink.api.common.time.Time;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.util.Collector;

public class WordCount {
    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        env.getConfig().setRestartStrategy(RestartStrategies.fixedDelayRestart(
                3, Time.seconds(10)
        ));

        env.fromElements(
                "hello world",
                "hello flink",
                "flink loves streams",
                "hello docker"
        )
        .flatMap(new Tokenizer())
        .keyBy(value -> value.f0)
        .sum(1)
        .print();

        env.execute("Streaming WordCount Example");
    }

    public static final class Tokenizer implements FlatMapFunction<String, Tuple2<String,Integer>> {
        @Override
        public void flatMap(String value, Collector<Tuple2<String, Integer>> out) {
            for (String token : value.toLowerCase().split("\\W+")) {
                if (!token.isEmpty()) {
                    out.collect(Tuple2.of(token, 1));
                }
            }
        }
    }
}
```

Return to repo root:
```bash
cd ..
```

---

## 7. Building the Shaded JAR

From project folder:
```bash
cd flink-wordcount
mvn clean package -DskipTests
cd ..
```

Copy jar into shared `jars/` folder:
```bash
cp flink-wordcount/target/flink-wordcount-assembly.jar jars/
```

Verify:
```bash
ls -l jars/
```

---

## 8. Running / Submitting the Job

### Method A: Web UI (Recommended first time)
1. Open http://localhost:8081
2. Click "Submit new job"
3. You should see `flink-wordcount-assembly.jar`
4. Select it → If Main Class not auto-filled, type `com.example.WordCount`
5. Click "Submit"

### Method B: CLI inside JobManager
```bash
docker compose exec jobmanager /opt/flink/bin/flink run /opt/flink/usrlib/flink-wordcount-assembly.jar
```

### Method C: REST API
```bash
curl -F "jarfile=@flink-wordcount/target/flink-wordcount-assembly.jar" http://localhost:8081/jars/upload
# Output JSON shows something like ..."filename":"/.../jars/<id>"...
curl -X POST http://localhost:8081/jars/<id>/run
```

---

## 9. Development Loop

After editing code:
```bash
cd flink-wordcount
mvn -q package -DskipTests
cp target/flink-wordcount-assembly.jar ../jars/
cd ..
```
Resubmit using any method (cancel old job first in UI if still running).

Tip: Add a VS Code Task (`.vscode/tasks.json`) to automate build + copy:
```json
{
  "version": "2.0.0",
  "tasks": [
    {
      "label": "Build Flink Jar",
      "type": "shell",
      "command": "mvn clean package -DskipTests && cp target/flink-wordcount-assembly.jar ../jars/",
      "options": { "cwd": "${workspaceFolder}/flink-wordcount" }
    }
  ]
}
```

Run via: Command Palette → "Tasks: Run Task" → "Build Flink Jar".

---

## 10. Common Errors & Fixes

| Symptom / Error | Cause | Fix |
|-----------------|-------|-----|
| `zsh: command not found: mvn` | Maven not installed or PATH not set | `brew install maven`; ensure brew shellenv loaded |
| JAR not listed in Web UI | Not in `jars/` at container start OR wrong directory | Copy jar to `./jars/`; refresh UI; or use upload |
| `JAR file does not exist: /opt/flink/usrlib/...` | Volume empty in container | Ensure you ran `docker compose` in correct folder with `jars/` present |
| `UnsupportedClassVersionError ... compiled by a more recent version` | Java version mismatch (compiled with 17, runtime 11) | Use Java 17 image (`flink:...-java17`) OR compile with `<maven.compiler.*>11</...>` |
| No output / job never finishes | Using `print()` → check TaskManager logs | `docker logs -f flink-taskmanager-1` |
| Port 8081 busy | Another service occupying port | Change mapping: `"8082:8081"` in compose |
| `ClassNotFoundException` for your class | Wrong main class or jar not shaded | Use shaded jar (**ends with -assembly.jar**) |
| `Permission denied` copying jar | File permissions | `chmod 644 jars/*.jar` |
| Web UI empty after rebuild | Old jar cached or job not resubmitted | Cancel old job; resubmit new jar |
| `Unknown module: jdk.compiler` warnings | Harmless flags referencing absent module | Ignore (no functional impact) |

Diagnostic commands:
```bash
docker compose exec jobmanager ls -l /opt/flink/usrlib
docker compose exec jobmanager java -version
docker compose logs jobmanager
docker compose logs taskmanager
```

---

## 11. Explaining the Files & Code

### docker-compose.yml Key Lines
- `image: flink:1.19.1-scala_2.12-java17`: Uses Flink 1.19.1 built with Scala 2.12 & Java 17 runtime.
- `ports: "8081:8081"`: Exposes Flink Web UI to your browser.
- `volumes: ./jars:/opt/flink/usrlib`: Maps host folder `jars/` into container; Flink scans `/opt/flink/usrlib` for jars.
- `taskmanager.numberOfTaskSlots: 2`: Each TaskManager container offers 2 parallel slots.
- `command: jobmanager` / `command: taskmanager`: Runs respective daemons.

### pom.xml Highlights
- `<scope>provided</scope>`: Tells Maven these dependencies (Flink) are provided by the cluster runtime; they are NOT bundled.
- `maven-shade-plugin`: Creates a fat jar combining your classes + (non-provided) dependencies.
- `<finalName>${project.artifactId}-assembly</finalName>`: Makes output file predictable: `flink-wordcount-assembly.jar`.
- `<execution.mainClass>` and Manifest transformer: Ensures Flink can detect the main class automatically.

### WordCount.java Logic (Layman’s Terms)
1. Get a runtime environment (`getExecutionEnvironment()`).
2. Define input using `fromElements(...)` (in real jobs this would be Kafka, files, sockets).
3. Break each line into words (Tokenizer).
4. Convert each word to a pair (word, 1).
5. Group by the word (`keyBy`).
6. Sum counts per word.
7. `print()` results to standard output (goes to TaskManager logs).
8. `env.execute("Streaming WordCount Example")` starts the job on the cluster.

### Why "Shaded" JAR?
Without shading, dependencies might be missing or classpaths conflict. Shading centralizes all needed user-code dependencies into one jar so the cluster can run it reliably.

---

## 12. Adding More Projects

Example: add a Kafka demo project.
```bash
cd ~/LEARN-apache-flink-java
mvn archetype:generate -DarchetypeGroupId=org.apache.flink \
  -DarchetypeArtifactId=flink-quickstart-java \
  -DarchetypeVersion=1.19.1 \
  -DgroupId=com.example \
  -DartifactId=flink-kafka-demo \
  -Dversion=1.0-SNAPSHOT \
  -Dpackage=com.example.kafka \
  -DinteractiveMode=false
```
Repeat steps: adjust `pom.xml`, add code, build, copy jar to `jars/`, submit.

You can safely have multiple jars in `jars/`; choose which to run in the UI.

---

## 13. Cleaning Up

Stop containers:
```bash
docker compose down
```

Remove containers + network + any anonymous volumes:
```bash
docker compose down -v
```

Remove all built jars:
```bash
rm -f jars/*.jar
```

Remove everything (CAUTION):
```bash
cd ..
rm -rf LEARN-apache-flink-java
```

---

## 14. Next Learning Steps

Progressively extend:
1. Add a **Socket source** (e.g. `nc -lk 9000`) and read live text.
2. Integrate **Kafka** (add `flink-connector-kafka` dependency and run a Kafka docker compose).
3. Explore **Table API / SQL** (add SQL dependencies, start SQL client).
4. Practice **stateful operations** and enable **checkpointing**:
   ```java
   env.enableCheckpointing(10_000); // every 10s
   ```
5. Try **Event Time & Watermarks**.
6. Use **RocksDB state backend** for large keyed state.
7. Add **Prometheus + Grafana** for metrics.
8. Package as **Application Mode** (Flink container starts directly with your jar).
9. Deploy a job to a remote cluster (Kubernetes / Yarn / Standalone remote).

Ask for any of these and we can extend the README or create new project templates.

---

## Quick Reference Command Cheat Sheet

| Task | Command |
|------|---------|
| Start cluster | `docker compose up -d` |
| Stop cluster | `docker compose down` |
| Build project | `mvn clean package -DskipTests` |
| Copy jar | `cp flink-wordcount/target/flink-wordcount-assembly.jar jars/` |
| List jars in container | `docker compose exec jobmanager ls -l /opt/flink/usrlib` |
| Submit (CLI) | `docker compose exec jobmanager /opt/flink/bin/flink run /opt/flink/usrlib/<jar>` |
| View TaskManager logs | `docker logs -f flink-taskmanager-1` |
| Scale TaskManagers | `docker compose up -d --scale taskmanager=3` |
| Upload via REST | `curl -F "jarfile=@path/to.jar" http://localhost:8081/jars/upload` |
| Run uploaded jar | `curl -X POST http://localhost:8081/jars/<id>/run` |

---

## FAQ

Q: Can I keep multiple versions of the same project jar?  
A: Yes. Rename them before copying (e.g. `cp target/flink-wordcount-assembly.jar ../jars/flink-wordcount-v2.jar`).

Q: Why not include Flink libraries inside my jar?  
A: They are provided by the cluster image; bundling them increases size and risks version conflicts.

Q: Where are results written?  
A: For `print()`, they go to TaskManager stdout logs. In real jobs you would sink to Kafka, files, or databases.

---

## Need More?

Open an issue or ask:  
- "Add Kafka example"  
- "Add Table API example"  
- "Add checkpointing & RocksDB"  
- "Convert to Application Mode"  

I’ll guide you further.

Happy streaming!
