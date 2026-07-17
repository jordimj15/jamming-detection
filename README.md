# JammingDetection

JammingDetection is a Java 21 application that ingests ADS-B data, stores it in a PostgreSQL database, and performs jamming detection analysis.

## Requirements

Before cloning the project, install:

- Java 21 (OpenJDK)
- Maven
- PostgreSQL

Verify the installation:

```bash
java --version
mvn --version
psql --version
```

---

# Installation

## 1. Clone the repository

```bash
git clone <repository-url>
cd JammingDetection
```

---

## 2. Configure the project

Copy the example configuration file:

```bash
cp src/main/resources/config.properties.example \
   src/main/resources/config.properties
```

Edit `src/main/resources/config.properties` and configure your database credentials:

```properties
db.url=jdbc:postgresql://localhost:5432/adsb_db
db.user=adsb
db.password=<your_password>
db.pool.size=10
```

You may also update the dataset path if necessary:

```properties
ingestion.data.path=data/<dataset_directory>
```

---

## 3. Create the PostgreSQL user

Launch PostgreSQL and create the user:

```bash
CREATE USER adsb WITH PASSWORD 'your_password';
```

Use the same password configured in `config.properties`.

---

## 4. Create the database

Create the database owned and grant privileges to 'adsb' user:

```bash
CREATE DATABASE adsb_db OWNER adsb;
```

---

## 5. Restore the database

An empty database template is provided under the `database/` directory.

Restore it with:

```bash
pg_restore \
    -U adsb \
    -h localhost \
    -d adsb_db \
    database/adsb_db_empty.dump
```

The dump restores:

- Schemas
- Tables
- Primary keys
- Foreign keys
- Indexes
- Sequences

No data is restored.

---

## 6. Build the project

Download all dependencies and compile the project:

```bash
mvn clean install
```

---

## 7. Generate jOOQ classes

Generate the jOOQ classes from the PostgreSQL schemas:

```bash
mvn initialize generate-sources
```

The generated classes will be placed under:

```
src/main/java/org/jammingdetection/generated
```

Run this command whenever the database schema changes.

---

## 8. Open the project

Open the project as a Maven project in IntelliJ IDEA.

If IntelliJ does not detect it automatically, set the Project SDK to **Java 21**.

---

## Project Structure

```
JammingDetection/
├── database/          # PostgreSQL database template
├── data/              # ADS-B datasets
├── libs/              # External libraries
├── src/
│   ├── main/
│   │   ├── java/
│   │   └── resources/
│   └── test/
├── pom.xml
└── README.md
```

## Development

Whenever the database schema changes:

1. Update the PostgreSQL schema.
2. Regenerate the jOOQ classes:

```bash
mvn generate-sources
```

The application configuration is stored in:

```
src/main/resources/config.properties
```

This file is ignored by Git and should never be committed.