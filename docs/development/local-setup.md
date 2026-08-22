# Local Data Check with pgAdmin

Use pgAdmin to inspect the local PostgreSQL data while developing.

## 1. Open pgAdmin

pgAdmin runs in Docker:

```text
http://localhost:5050
```

Login:

```text
Email: admin@example.com
Password: admin
```

These values come from the `modulebook-pgadmin` container environment:

```text
PGADMIN_DEFAULT_EMAIL=admin@example.com
PGADMIN_DEFAULT_PASSWORD=admin
```

You can change them only when recreating the pgAdmin container.

## 2. Register the database server

In pgAdmin:

`Servers` -> right click -> `Register` -> `Server...`

General tab:

```text
Name: modulebook-local
```

`modulebook-local` is only a pgAdmin display name. You can choose another name.

Connection tab:

```text
Host name/address: modulebook-postgres
Port: 5432
Maintenance database: modulebook
Username: modulebook
Password: modulebook
```

Why these values:

- `modulebook-postgres` is the Docker container name for PostgreSQL.
- `5432` is the PostgreSQL port inside Docker.
- `modulebook` is the database name from `application.properties`.
- `modulebook` / `modulebook` are the database username and password from `application.properties`.

The Spring Boot app uses port `5433` from your Mac:

```text
jdbc:postgresql://localhost:5433/modulebook
```

pgAdmin uses `modulebook-postgres:5432` because pgAdmin and PostgreSQL are both inside Docker.

## 3. View data

Open:

`Servers` -> `modulebook-local` -> `Databases` -> `modulebook` -> `Schemas` -> `public` -> `Tables`

Right click a table, then choose:

`View/Edit Data` -> `All Rows`
