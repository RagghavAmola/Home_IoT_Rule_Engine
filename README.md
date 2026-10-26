# Smart Home IoT Rule Engine

A standalone desktop rule engine for smart home IoT devices built with Java 25 and JavaFX.

## Tech Stack
- **Java 25 (LTS)**: Temurin 25 on Windows 11
- **Build Tool**: Apache Maven 3.10.0+
- **UI Framework**: JavaFX 25.0.5
- **Database**: SQLite (default embedded) and MySQL (config-switchable) via JDBC
- **Concurrency**: `java.util.concurrent`, `ScheduledExecutorService`, `ConcurrentHashMap`
- **Logging**: SLF4J 2.0.16 + Logback 1.5.16
- **Testing**: JUnit 5.11.4 + Mockito 5.14.2

## Architectural Layers
All code resides under `com.smarthome`:
- `domain`: Pure POJOs, enums, and value objects (no JavaFX or JDBC dependencies).
- `exception`: Custom checked and unchecked domain/infrastructure exceptions.
- `dao`: Data Access Object interfaces only.
- `dao.jdbc`: JDBC implementations (the only layer containing SQL).
- `engine`: Pure Java dependency graphs, cycle detection, priority conflict resolution, and rule evaluation.
- `service`: Orchestration services consuming DAO interfaces and engine components.
- `simulation`: Simulated IoT devices and periodic background telemetry monitoring.
- `ui`: JavaFX views, controllers, view-models.
- `config`: Database configuration, properties loading, and dependency injection wiring (`AppBootstrap`).

## Prerequisites
- Java 25 JDK (Temurin recommended) configured in `JAVA_HOME`
- Maven 3.10+ in system `PATH`

## Building and Running
```bash
# Build and run all tests
mvn clean verify

# Launch the JavaFX desktop application
mvn javafx:run
```

## Configuration
Application settings can be adjusted in `config/app.properties`:
- `db.type`: `sqlite` or `mysql`
- `db.url`: JDBC connection URL
- `db.user`: Database username (for MySQL)
- `db.password`: Database password (for MySQL)
- `monitor.interval.ms`: IoT telemetry polling interval in milliseconds
