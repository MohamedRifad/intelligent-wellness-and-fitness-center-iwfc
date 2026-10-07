# Intelligent Wellness and Fitness Center (IWFC)

IWFC is a Java 21 console prototype for managing fitness equipment, session scheduling, Member bookings, maintenance workflows, equipment usage, and automated in-system notifications.

## Architecture

The project contains exactly 15 production Java source files across five packages:

- `iwfc.app`: application Facade and console entry point
- `iwfc.domain`: users, equipment, sessions, and maintenance entities
- `iwfc.service`: booking and maintenance business rules
- `iwfc.repository`: reusable `GenericRepository<T>` storage
- `iwfc.pattern`: entity Factory
- `iwfc.exception`: custom checked exceptions

The implementation demonstrates Factory, Facade, and Observer patterns, together with inheritance, polymorphism, collections, generics, validation, and custom exception handling. Data is intentionally stored in memory; no database is required.

## Requirements

- JDK 21 or newer
- Apache Maven 3.9 or newer

Verify the installed tools:

```powershell
java -version
mvn -version
```

## Build and test

From the repository root:

```powershell
mvn --batch-mode clean test
```

Create the application JAR:

```powershell
mvn --batch-mode package
```

## Run

After packaging:

```powershell
java -cp target\iwfc-management-system-1.0.0-SNAPSHOT.jar iwfc.app.IWFCFacade
```

The console guides the user through one-time Administrator setup. Its demonstration options cover sample-data loading, equipment and session viewing, scheduling and booking, equipment usage, maintenance, and notifications.

## Strict Java 21 compilation

In PowerShell:

```powershell
$sourceFiles = Get-ChildItem src/main/java -Recurse -Filter *.java | ForEach-Object FullName
javac --release 21 -Xlint:all -Werror -d target/strict-java21 $sourceFiles
```

## Project scope

This academic prototype uses in-memory repositories and a console interface. It does not include a database, external authentication, email/SMS delivery, or a graphical interface.
