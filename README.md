# myReqEng - Requirements Engineering Tool

Ein Desktop-Tool zur Verwaltung von Anforderungen (Requirements) mit hierarchischer Baumstruktur.  
Entwickelt mit **Java Swing**, **Spring Boot 3**, **JPA/Hibernate**, **Flyway** und **PostgreSQL**.

## Features (aktueller Stand)

- Menüleiste: **File**, **Edit**, **Config**, **Help**
- Linker Bereich: Baumansicht aller Requirements eines Projekts (Anzeige: `title`)
  - Navigation mit **Pfeil-hoch / runter**
  - Subbäume auf- und zuklappen mit **Pfeil-links / rechts**
  - **STRG + N**: Neuen Kindknoten erzeugen – Titel direkt im Baum editierbar
  - **F2** oder Doppelklick: `title` des ausgewählten Knotens direkt im Baum editieren
  - **Enter** übernimmt die Titeländerung, **Escape** bricht sie ab
  - Rechtsklick-Kontextmenü:
    - **add new requirement**: neues Requirement als Subitem des angeklickten Requirements
    - **delete requirement**: Requirement nach Rückfrage löschen (inkl. Unterelementen)
    - **link requirement**: Platzhalter, noch ohne Funktion
- Rechter Bereich: Formular zum Bearbeiten eines Requirements
  - Felder: `identifier` (readonly), `title`, `description`, `parentId`
  - Datenbank-ID wird **nicht** angezeigt und ist nicht editierbar
  - Änderungen werden sofort gespeichert, es gibt keinen separaten `Speichern`-Button
- Trennlinie (JSplitPane) zwischen Baum und Formular – per Maus verstellbar
- Projekte: Anlegen, Öffnen, Recent Projects (letzte 10), Schließen
- Persistenz in PostgreSQL (Docker) + Flyway-Migrationen

## Datenmodell

- **Requirement** (`id`, `project_id`, `identifier`, `title`, `description` [optional], `parent_id`)
  - Ein Projekt ist die Menge aller Requirements mit derselben `project_id`
  - Der Root-Knoten eines Projekts ist der Knoten ohne Parent, also mit `parent_id = 0`
  - `identifier` wird automatisch als `REQ-<laufende Nummer>` erzeugt und ist unveränderlich
  - `title` ist der Name, der im Navigationsbaum angezeigt wird
- **Link** (`id`, `source`, `target`) – für Traceability (aktuell nicht in der UI genutzt)

Alle nicht-optionalen Felder sind Pflichtfelder.

## Voraussetzungen

- Java 17+ (empfohlen: 17 oder neuer)
- Apache Maven 3.9+
- Docker (für PostgreSQL) – Installation wird vorausgesetzt

## Projekt bauen und starten

### 1. PostgreSQL mit Docker starten

#### Variante A: Mit docker-compose (empfohlen)

```bash
# Im Projektverzeichnis
docker compose up -d
```

Prüfen:

```bash
docker compose ps
docker compose logs -f postgres
```

#### Variante B: Manuell mit `docker run`

```bash
docker run --name myreqeng-postgres \
  -e POSTGRES_DB=reqengdb \
  -e POSTGRES_USER=reqeng \
  -e POSTGRES_PASSWORD=reqengsecret \
  -p 5432:5432 \
  -v myreqeng_pgdata:/var/lib/postgresql/data \
  -d postgres:16-alpine
```

**Wichtig:** Volume `myreqeng_pgdata` sorgt für persistente Daten über Container-Neustarts hinweg.

Container stoppen:

```bash
docker compose down          # mit docker-compose
# oder
docker stop myreqeng-postgres
```

Datenbank komplett zurücksetzen (Vorsicht!):

```bash
docker compose down -v
```

### 2. Projekt bauen

```bash
mvn clean package -DskipTests
```

Das fertige JAR liegt unter:

```
target/myreqeng-0.1.0-SNAPSHOT.jar
```

### 2a. Docker Image bauen (optional)

Mit `mvn install` wird nach dem Bauen automatisch ein Docker Image erstellt und im lokalen Docker-Daemon verfügbar gemacht:

```bash
mvn clean install -DskipTests
```

Das Image ist danach unter folgendem Namen verfügbar:

```
myreqeng:0.1.0-SNAPSHOT
```

Prüfen:

```bash
docker images myreqeng
```

Verwendet wird das `Dockerfile` im Projektverzeichnis (Basis: `eclipse-temurin:17-jre-jammy`).
Alpine-Images enthalten keine X11-Bibliotheken – da die Anwendung Swing nutzt, ist ein Ubuntu-basiertes Image mit X11-Support (`libx11-6`, `libxrender1` etc.) erforderlich.

**Zu einer lokalen Registry pushen** (z.B. `localhost:5000`): Image-Namen in `pom.xml` auf `localhost:5000/myreqeng:${project.version}` ändern und eine zweite Execution mit `<goal>push</goal>` ergänzen.

### 3. Anwendung starten

#### Mit Maven (einfach für Entwicklung)

```bash
mvn spring-boot:run
```

#### Mit dem gebauten JAR

```bash
java -jar target/myreqeng-0.1.0-SNAPSHOT.jar
```

#### Mit Docker (Swing-App benötigt Display)

```bash
docker run -e DISPLAY=$DISPLAY -v /tmp/.X11-unix:/tmp/.X11-unix myreqeng:0.1.0-SNAPSHOT
```

Datenbank-Verbindung per Umgebungsvariablen übergeben (z.B. wenn PostgreSQL auf dem Host läuft):

```bash
docker run \
  -e DISPLAY=$DISPLAY \
  -v /tmp/.X11-unix:/tmp/.X11-unix \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5433/reqengdb \
  -e SPRING_DATASOURCE_USERNAME=reqeng \
  -e SPRING_DATASOURCE_PASSWORD=reqengsecret \
  myreqeng:0.1.0-SNAPSHOT
```

Beim ersten Start legt Flyway automatisch die Tabellen an (`V1__init_schema.sql`).

## Konfiguration der Datenbank

Die Verbindungseinstellungen stehen in `src/main/resources/application.properties`:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/reqengdb
spring.datasource.username=reqeng
spring.datasource.password=reqengsecret
```

### Anpassen / Überschreiben

- Umgebungsvariablen (empfohlen für Produktion / andere Umgebungen):
  ```bash
  export SPRING_DATASOURCE_URL=jdbc:postgresql://meine-db:5432/reqengdb
  export SPRING_DATASOURCE_USERNAME=...
  export SPRING_DATASOURCE_PASSWORD=...
  ```

- Oder eigene `application-local.properties` anlegen und per Profile aktivieren.

## Bedienung

1. **Neues Projekt** → `File` → `New Project...`
   - Es wird automatisch ein Root-Requirement angelegt.
2. Im linken Baum navigieren und auswählen.
3. Rechts im Formular bearbeiten, die Änderungen werden sofort gespeichert.
4. **Neuen Kindknoten** erzeugen: Knoten auswählen + **STRG+N** → Titel direkt im Baum editieren.
5. **Titel im Baum ändern**: Knoten auswählen + **F2** oder Doppelklick → `Enter` übernimmt, `Escape` bricht ab.
6. **Kontextmenü im Baum**: Rechtsklick auf ein Requirement → Kindknoten anlegen, Requirement löschen oder Link-Platzhalter anzeigen.
7. **Recent Projects**: Die letzten 10 geöffneten Projekte stehen unter `File` → `Recent Projects`.

## Projektstruktur (wichtigste Dateien)

```
src/main/java/de/myreqeng/
├── MyReqEngApplication.java      # Einstiegspunkt (Spring + Swing)
├── domain/                       # JPA-Entities (Requirement, Link)
├── repository/                   # Spring Data Repositories
├── service/                      # Business-Logik + RecentProjectsService
└── ui/                           # Swing-Komponenten (MainFrame, Form, Wrapper)

src/main/resources/
├── application.properties
└── db/migration/
    ├── V1__init_schema.sql       # Flyway-Initialisierung
    └── V2__remove_project_table.sql

docker-compose.yml
README.md
pom.xml
```

## Hinweise & Bekannte Einschränkungen (v0.1)

- `parentId` darf derzeit auf jeden Wert gesetzt werden (inkl. Zyklen). Validierung folgt später.
- Links (Traceability) sind im Datenmodell vorhanden, aber noch nicht in der Oberfläche sichtbar.
- `link requirement` ist im Baum-Kontextmenü sichtbar, aber noch ohne Funktion.
- Bei Strukturänderungen (parentId) wird der Baum komplett neu geladen.
- Die Anwendung geht von einem lokal laufenden PostgreSQL aus.
- Ein Projekt hat keine eigene Tabelle mehr, sondern wird ausschließlich über `project_id` auf den Requirements modelliert.

## Nächste Schritte / Erweiterungsideen

- Kontextmenü am Baum erweitern (Move, Link erstellen)
- Bessere Validierung (Zyklen verhindern, Identifier-Unique pro Projekt)
- Export/Import (ReqIF, Excel, Markdown)
- Versionierung / Historie von Requirements
- Dunkelmodus / Theming

## Lizenz

MIT (oder nach Belieben anpassen)

---

Viel Spaß beim Anforderungsmanagement! Bei Fragen oder Problemen einfach die Konsole beobachten oder ein Issue anlegen.
