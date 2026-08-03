# Angular + Quarkus + OracleDB

A full-stack application with an **Angular** frontend, a **Quarkus** (Java) backend, and **Oracle Database** as the datastore. Oracle runs in a container via **Podman**.

## Tech Stack

| Layer      | Technology                          |
|------------|-------------------------------------|
| Frontend   | Angular 17+                         |
| Backend    | Quarkus 3.x (Java 17+)              |
| Database   | Oracle Database 23ai Free           |
| ORM        | Hibernate ORM with Panache          |
| Container  | Podman                              |

## Prerequisites

- **Node.js** 18+ and npm
- **Angular CLI** (`npm install -g @angular/cli`)
- **JDK** 17+
- **Maven** 3.9+ (or use the bundled `./mvnw`)
- **Podman** installed and running

## Project Structure

```
.
├── frontend/          # Angular application
│   ├── src/
│   ├── angular.json
│   └── package.json
├── backend/           # Quarkus application
│   ├── src/main/java/
│   ├── src/main/resources/
│   │   └── application.properties
│   └── pom.xml
└── README.md
```

## 1. Database Setup (Oracle via Podman)

Pull the Oracle 23ai Free image (no registry login required):

```bash
podman pull container-registry.oracle.com/database/free:latest
```

Run the container with a persistent volume so data survives restarts:

```bash
podman run -d --name oracle-db \
  -p 1521:1521 \
  -e ORACLE_PWD=YourPassword123 \
  -v oracle-data:/opt/oracle/oradata \
  container-registry.oracle.com/database/free:latest
```

Wait for the database to be ready (first boot takes a couple of minutes):

```bash
podman logs -f oracle-db
```

Look for the line:

```
DATABASE IS READY TO USE!
```

### Connection Details

| Property        | Value                                          |
|-----------------|------------------------------------------------|
| Host / Port     | `localhost:1521`                               |
| Service (PDB)   | `FREEPDB1`                                      |
| Service (CDB)   | `FREE`                                          |
| Admin users     | `SYS` / `SYSTEM` (password from `ORACLE_PWD`)   |
| JDBC URL        | `jdbc:oracle:thin:@//localhost:1521/FREEPDB1`  |

### Create an Application User (recommended)

```bash
podman exec -it oracle-db sqlplus system/YourPassword123@//localhost:1521/FREEPDB1
```

```sql
CREATE USER appuser IDENTIFIED BY apppassword;
GRANT CONNECT, RESOURCE, UNLIMITED TABLESPACE TO appuser;
EXIT;
```

## 2. Backend Setup (Quarkus)

Add the Oracle JDBC and Hibernate extensions if not already present:

```bash
cd backend
./mvnw quarkus:add-extension -Dextensions="jdbc-oracle,hibernate-orm-panache,rest-jackson"
```

Configure `src/main/resources/application.properties`:

```properties
# Datasource
quarkus.datasource.db-kind=oracle
quarkus.datasource.username=appuser
quarkus.datasource.password=apppassword
quarkus.datasource.jdbc.url=jdbc:oracle:thin:@//localhost:1521/FREEPDB1

# Hibernate
quarkus.hibernate-orm.database.generation=update
quarkus.hibernate-orm.log.sql=true

# HTTP / CORS (allow Angular dev server)
quarkus.http.port=8080
quarkus.http.cors=true
quarkus.http.cors.origins=http://localhost:4200
```

Run in dev mode (live reload):

```bash
./mvnw quarkus:dev
```

The API is available at `http://localhost:8080`.

## 3. Frontend Setup (Angular)

```bash
cd frontend
npm install
```

Point the frontend at the backend in `src/environments/environment.ts`:

```typescript
export const environment = {
  production: false,
  apiUrl: 'http://localhost:8080'
};
```

Run the dev server:

```bash
ng serve
```

The app is available at `http://localhost:4200`.

## Running the Full Stack

Open three terminals:

```bash
# Terminal 1 — Database
podman start oracle-db

# Terminal 2 — Backend
cd backend && ./mvnw quarkus:dev

# Terminal 3 — Frontend
cd frontend && ng serve
```

Then visit **http://localhost:4200**.

## Building for Production

**Backend** (produces a runnable JAR under `target/quarkus-app/`):

```bash
cd backend
./mvnw package
java -jar target/quarkus-app/quarkus-run.jar
```

Optional native build (requires GraalVM or a container build):

```bash
./mvnw package -Dnative -Dquarkus.native.container-build=true
```

**Frontend** (produces static assets under `dist/`):

```bash
cd frontend
ng build --configuration production
```

## Useful Podman Commands

```bash
podman ps                    # list running containers
podman stop oracle-db        # stop the database
podman start oracle-db       # start it again
podman logs -f oracle-db     # follow logs
podman rm -f oracle-db       # remove the container (data persists in the volume)
podman volume rm oracle-data # delete the persisted data
```

## Troubleshooting

- **Backend can't connect:** confirm the DB shows `DATABASE IS READY TO USE!` and that the JDBC URL uses `FREEPDB1`, not `FREE`.
- **CORS errors in the browser:** verify `quarkus.http.cors.origins` matches the Angular dev URL.
- **Port already in use:** change the mapped port (e.g. `-p 1522:1521`) and update the JDBC URL accordingly.
- **Slow first startup:** the initial container boot initializes the database; subsequent starts are fast.
