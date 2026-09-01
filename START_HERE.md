# MiTRAA Hackathons — Start Here

## Requirements

- Java 21
- Maven 3.9+
- MySQL 8

## Migration order

- V11: payment production operations
- V12: admin controls, MFA and audit
- V13: jury evaluation panel
- V14: submission management
- V15: launch-readiness modules
- V16: Google/GitHub OAuth identities

Do not rename or edit applied migration files.

## Existing local database only

If this database was used during the migration-number correction, ensure its history contains:

```text
V11 checksum: -346885083
V12 checksum: -1240107925
```

V16 must not be inserted manually; Flyway applies it automatically.

## Build and start

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn clean package -DskipTests
mvn spring-boot:run
```

Open `http://localhost:8080`.
