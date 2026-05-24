# IssueFlow — Run Guide

## Prerequisites
- Java 21 (JDK)
- Docker Desktop
- Port 5432 must be free (used by PostgreSQL)

## Start the Database
```bash
docker compose up -d
```

## Build the Project
On Mac/Linux:
```bash
./mvnw clean package -DskipTests
```

On Windows (CMD or PowerShell):
```bash
mvnw.cmd clean package -DskipTests
```

This also downloads all Maven dependencies automatically.

## Run the Application
On Mac/Linux:
```bash
./mvnw spring-boot:run
```

On Windows:
```bash
mvnw.cmd spring-boot:run
```

API available at http://localhost:8080

## Run the Tests
Make sure `docker compose up -d` is running first, then:

On Mac/Linux:
```bash
./mvnw test
```

On Windows:
```bash
mvnw.cmd test
```

## Notes
- Flyway migrations run automatically on first startup
- All integration tests run against real PostgreSQL from compose.yml (not H2)
