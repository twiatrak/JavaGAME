# Timonipumba — 2D Game Engine (LibGDX)

## Overview
This project is a **2D game engine built with Java and LibGDX**, designed for academic purposes.  
Its main goal is to demonstrate a **clean, extensible architecture** allowing game content
(levels, entities, interactions) to be enriched **without modifying Java code**, primarily
through external tools such as **Tiled** and configuration files.

The engine follows an **ECS (Entity–Component–System)** approach using **Ashley** and is structured
to remain readable, modular, and easy to extend.

---

## Build & Run

All commands must be run **from the project root**.

### Build the project
```bash
./gradlew build
```

### Run the game (desktop)
```bash
./gradlew desktop:run
```

### Run tests
```bash
./gradlew test
```

On Windows (PowerShell / CMD):
```bat
gradlew.bat build
gradlew.bat desktop:run
```

## Project Structure

```
.
├── core/                  # Main game logic (engine)
│   └── src/main/java/
│       └── com/timonipumba/
│           ├── components/    # ECS Components (data-only)
│           ├── systems/       # ECS Systems (game logic)
│           ├── assets/        # Asset loading & management
│           ├── automata/      # Automata / logic utilities
│           ├── camera/        # Camera handling
│           ├── level/         # Level & content loading (JSON/Tiled)
│           └── util/          # Utility helpers
│
├── desktop/               # Desktop launcher (LWJGL)
├── uml/                   # Architecture diagrams (Graphviz .dot), grouped by package
├── gradle/                # Gradle wrapper
├── build.gradle           # Root Gradle configuration
├── settings.gradle
└── README.md
```

---

## Architecture

### ECS (Entity–Component–System)
- **Components**: simple data containers (Position, Health, Collision, etc.)
- **Systems**: game logic operating on entities matching component families
- **Entities**: dynamically composed at runtime

This architecture:
- reduces coupling
- improves extensibility
- aligns with LibGDX + Ashley best practices

### Content-Driven Design
- Levels and entities are described using **Tiled (.tmx)** and **JSON**
- The project also uses **procedural generation** to create/populate some Tiled maps
- New content can be added without changing Java code
- Java focuses on *engine logic*, not *game data*

---

## UML Diagrams

All architecture/UML-style diagrams live under the `uml/` directory at the repo root.

- Format: **Graphviz `.dot`** (not PlantUML)
- Organization: separated by **package / subsystem folders** (e.g. `uml/components/`, `uml/systems/`, `uml/world/`)
- Some folders also contain supporting notes (`*_notes.txt`).

To render a diagram locally (requires Graphviz):
```bash
dot -Tsvg uml/root/root_game_bootstrap.dot -o root_game_bootstrap.svg
```

If you have Graphviz installed and on PATH, you can also open `.dot` files directly in many editors (or use a VS Code Graphviz preview extension).

---

## Requirements

- Java **17+**
- Gradle (via wrapper)
- Optional: Graphviz (for rendering `.dot` diagrams)

---

## Educational Objectives

This project demonstrates:
- Object-Oriented Programming in Java
- ECS architectural pattern
- Use of LibGDX and Ashley
- External content loading (Tiled / JSON)
- Clean code organization and documentation

The project prioritizes **clarity and structure** over feature complexity.

---

## Notes

- The project is intentionally modular and extensible
- The game itself is a proof-of-concept for the engine
- UML diagrams are provided to support architectural understanding

---

## Author

Academic project developed as part of a **2D Game Engine coursework** using LibGDX.
