# Utilities for PostgreSQL based Projects

This project contains tools for common tasks when deploying docker based 
applications using PostgreSQL as a database backend. 

## Migrations

Migrations are done by diffing a vanilla schema created by application components 
to a given target database. This has many advantages:

- no need for manually creating migration files
- sql scripts do not need to be idempotent (no more `alter table`)
- manually applied schema changes on an installation are made visible

### Usage

Create one or more db setup docker images, which should meet the following requirements:

- uses PGHOST, PGPORT, PGUSER, PGPASSWORD, PGDATABASE from the 
  [postgres client environment variables](https://www.postgresql.org/docs/12/libpq-envars.html)
  to connect to the database server.
- has an executable named `setup_db` which sets up the database schemas etc.

An example Dockerfile for such a setup image might look like this:

```dockerfile
FROM alpine:3.11.5
RUN apk --update --no-cache add postgresql-client=12.2-r0
COPY setup_db /usr/bin/
RUN chmod 755 /usr/bin/setup_db
CMD true
```

A setup_db script using PostgreSQL client tools might look like this:

```shell script
#!/bin/sh
set -e
createdb myproject
psql -c 'create schema backend create table articles(id int);' myproject
```

Note: The `setup_db` script can be in any language as long as it honors the connection
environment variables.

Another `setup_db` script might build upon the first, for example the following 
script requires the `myproject` database to exist.

```shell script
#!/bin/sh
set -e
psql -c 'create schema frontend create table published_articles(id int);' myproject
```

After building the images with docker we can now use also docker to get the
diff to whatever database server like this:

```shell script
#!/bin/sh
docker run --rm \
  -e DB_URI="postgres:mypassword@staging.example.com:5432/myproject \
  -e SETUP_IMAGES="myproject-backend-db myproject-frontend-db" \
  -e SCHEMAS="backend frontend" \
  -v "$PWD/out:/out" \
  -v "/var/run/docker.sock:/var/run/docker.sock" \
  lovelysystems/pgutil:dev pgutil diff /out
```

The above example will generate a file for every changed schema in the `./out` directory.
Every file contains sql statements which make the target database be the same as
the source database. The diffs are generated using [Migra](https://djrobstep.com/docs/migra).

The following environment variabes are used by the pgutil docker image:

- `DB_URI`: the uri of the database to be migrated
- `SETUP_IMAGES`: a list of docker image names to be used as setup images
- `SCHEMAS`: optional list of schemas to diff, if not provided the full database
  is diffed.

Note that it is required to mount the docker socket into the docker container,
because the diff tool requires a docker daemon to run the required containers.

# TODO:

- Example project
- Currently roles are not synced, so the target database needs to have the roles 
  and users defined, in future we might also diff such global objects.

