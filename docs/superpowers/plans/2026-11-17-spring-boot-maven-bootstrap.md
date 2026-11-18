# Spring Boot Maven Bootstrap Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bootstrap a Java 21 Maven multi-module project with three independently runnable Spring Boot services.

**Architecture:** The root Maven POM is a parent/aggregator with `services/loan-service`, `services/ledger-service`, and `services/search-service` as child modules. Each service has its own Spring Boot main class, port, Actuator health endpoint, and context-load test, with no external infrastructure dependency in this baseline.

**Tech Stack:** Java 21, Maven 3.6.3+, Spring Boot 4.1.2, Spring Web, Spring Validation, Spring Boot Actuator, JUnit 5.

---

### Task 1: Configure the local Java and Maven toolchain

**Files:**
- Modify: `~/.zshrc` only if needed to expose Java 21 and Maven on `PATH`
- Create: `.mvn/jvm.config`

- [ ] **Step 1: Install Maven with Homebrew**

Run:

```bash
brew install maven
```

Expected: Homebrew installs Maven without errors.

- [ ] **Step 2: Verify the installed tools with Java 21 selected**

Run:

```bash
export JAVA_HOME="/opt/homebrew/Cellar/openjdk@21/21.0.9/libexec/openjdk.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
java -version
mvn -version
```

Expected: Java reports version 21 and Maven reports a version supported by Spring Boot 4.1.2.

- [ ] **Step 3: Add Maven JVM defaults**

Create `.mvn/jvm.config` with:

```text
-Dfile.encoding=UTF-8
-Duser.language=en
-Duser.country=US
```

This keeps Maven output and generated metadata deterministic without changing the user’s shell configuration.

### Task 2: Create the root Maven parent and aggregator

**Files:**
- Create: `pom.xml`
- Create: `.gitignore`

- [ ] **Step 1: Create the root `pom.xml`**

Use packaging `pom`, pin `org.springframework.boot:spring-boot-starter-parent` to `4.1.2`, set `java.version` to `21`, and aggregate exactly these modules:

```xml
<modules>
  <module>services/loan-service</module>
  <module>services/ledger-service</module>
  <module>services/search-service</module>
</modules>
```

The root POM must provide `spring-boot-maven-plugin` dependency management to child modules but must not itself package an executable application.

- [ ] **Step 2: Create repository ignores**

Create `.gitignore` containing Maven output, IDE metadata, macOS metadata, logs, and local environment files:

```gitignore
target/
.idea/
*.iml
.vscode/
.DS_Store
*.log
.env
```

- [ ] **Step 3: Validate the root model before adding application code**

Run:

```bash
mvn -N validate
```

Expected: Maven validates the root POM successfully; module resolution errors are not acceptable after the modules are declared, so this step is run after module POMs exist if Maven requires them.

### Task 3: Add the three independent Spring Boot service modules

**Files:**
- Create: `services/loan-service/pom.xml`
- Create: `services/ledger-service/pom.xml`
- Create: `services/search-service/pom.xml`

- [ ] **Step 1: Define each child POM**

Each child POM must inherit the root parent and include these dependencies:

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-web</artifactId>
</dependency>
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-test</artifactId>
  <scope>test</scope>
</dependency>
```

Each module must configure `spring-boot-maven-plugin` and use a distinct artifact ID: `loan-service`, `ledger-service`, and `search-service`.

- [ ] **Step 2: Confirm module coordinates are unique**

Run:

```bash
mvn help:effective-pom -pl services/loan-service -Doutput=/tmp/fincore-loan-effective-pom.xml
mvn help:effective-pom -pl services/ledger-service -Doutput=/tmp/fincore-ledger-effective-pom.xml
mvn help:effective-pom -pl services/search-service -Doutput=/tmp/fincore-search-effective-pom.xml
```

Expected: Each effective POM resolves the same pinned Spring Boot version and its own artifact coordinates.

### Task 4: Add application entry points, ports, and health endpoints

**Files:**
- Create: `services/loan-service/src/main/java/com/fincore/loan/LoanServiceApplication.java`
- Create: `services/ledger-service/src/main/java/com/fincore/ledger/LedgerServiceApplication.java`
- Create: `services/search-service/src/main/java/com/fincore/search/SearchServiceApplication.java`
- Create: `services/loan-service/src/main/resources/application.yml`
- Create: `services/ledger-service/src/main/resources/application.yml`
- Create: `services/search-service/src/main/resources/application.yml`

- [ ] **Step 1: Add one `@SpringBootApplication` entry point per service**

Each main class must be in its service package and contain a standard `main` method calling `SpringApplication.run(...)`.

- [ ] **Step 2: Configure distinct service ports and Actuator exposure**

Use these service settings:

```yaml
server:
  port: <service-port>
management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      probes:
        enabled: true
```

Use ports `8081`, `8082`, and `8083` for loan, ledger, and search respectively.

- [ ] **Step 3: Keep the baseline independent of external infrastructure**

Do not add database, Kafka, Redis, Elasticsearch, or MongoDB auto-configuration yet. The health endpoint must start with only the application classpath.

### Task 5: Add context-load tests

**Files:**
- Create: `services/loan-service/src/test/java/com/fincore/loan/LoanServiceApplicationTests.java`
- Create: `services/ledger-service/src/test/java/com/fincore/ledger/LedgerServiceApplicationTests.java`
- Create: `services/search-service/src/test/java/com/fincore/search/SearchServiceApplicationTests.java`

- [ ] **Step 1: Add one `@SpringBootTest` per service**

Each test must load the service application context and contain a test method that asserts the context starts. Use the service’s own application class in `@SpringBootTest(classes = ...)` so the test cannot accidentally load another module.

- [ ] **Step 2: Run the focused module tests**

Run:

```bash
mvn -pl services/loan-service test
mvn -pl services/ledger-service test
mvn -pl services/search-service test
```

Expected: All three modules report zero test failures.

### Task 6: Verify the complete bootstrap

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Add setup and run instructions**

Document the Java 21 requirement, Maven command, module list, service ports, and commands for running each service with `mvn spring-boot:run`.

- [ ] **Step 2: Run the full build and tests**

Run:

```bash
mvn clean verify
```

Expected: Exit code 0, all three modules compile, tests pass, and Spring Boot repackaging succeeds.

- [ ] **Step 3: Verify the health endpoints**

Start each service in separate processes, then run:

```bash
curl --fail http://localhost:8081/actuator/health
curl --fail http://localhost:8082/actuator/health
curl --fail http://localhost:8083/actuator/health
```

Expected: Each endpoint returns HTTP 200 with an `UP` health status.

- [ ] **Step 4: Inspect the final file inventory**

Run:

```bash
rg --files -g '!target' | sort
```

Expected: The inventory contains the root POM, three child POMs, three application classes, three configuration files, three tests, README, and the setup documentation.

