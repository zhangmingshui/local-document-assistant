# Local Document Assistant

Small Mac-first skeleton for a local document assistant side project.

## Backend

Requires Java 17 or newer.

```bash
java -version
mvn -version
mvn spring-boot:run
```

If Maven reports Java 8, point it at a newer JDK first:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
mvn clean install -DskipTests
```

Mock endpoints:

- `GET /api/folders`
- `GET /api/processing-jobs/latest`
- `POST /api/questions`

## Frontend

```bash
cd frontend
npm install
npm run dev
```

The frontend runs through Vite and proxies `/api` requests to `http://localhost:8080`.

## Scope

This skeleton uses mocked data only. Paths shown in the UI are fake strings, and the app does not read, open, edit, move, delete, scan, embed, chunk, or index real files.
