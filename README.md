# Intelligent Wellness and Fitness Center (IWFC)

IWFC is a Java 21 console prototype for managing fitness equipment, session scheduling, Member bookings, maintenance workflows, equipment usage, and automated in-system notifications.

## Architecture

The project contains exactly 15 production Java source files across six packages:

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

Create the application JAR without rerunning the tests:

```powershell
mvn --batch-mode -DskipTests package
```

## Run the role-based demonstration

After packaging:

```powershell
java -cp target\iwfc-management-system-1.0.0-SNAPSHOT.jar iwfc.app.IWFCFacade
```

At startup, the console automatically creates Administrator `A1` / `Rifad` through
the existing Facade initialization operation and loads the verified sample dataset.
It then displays a demonstration account selector. User IDs are upper case, such as
`A1`, `I1`, and `M5`.

Account selection is used only to demonstrate the three roles. It is not password
authentication. Selecting an active registered user opens the corresponding
Administrator, Instructor, or Member menu, while the existing Facade authorization
checks still protect every business operation.

Each role menu contains one `0 - Logout` option. Selecting `0` returns to the account
selector without clearing in-memory application data. `EXIT` is accepted only at the
account selector and closes the application.

The role menus demonstrate equipment administration, session scheduling, recurring
weekly sessions, Member bookings, equipment usage, maintenance processing, and
filtered in-system notifications.

## Strict Java 21 compilation

In PowerShell:

```powershell
$sourceFiles = Get-ChildItem src/main/java -Recurse -Filter *.java | ForEach-Object FullName
javac --release 21 -Xlint:all -Werror -d target/strict-java21 $sourceFiles
```

## Project scope

This academic prototype uses in-memory repositories and a console interface. It does not include a database, external authentication, email/SMS delivery, or a graphical interface.
