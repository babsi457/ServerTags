# Tests

Die Tests brauchen ein Java-25-JDK. Der Gradle-Wrapper lädt Minecraft, Fabric und die weiteren Build-Abhängigkeiten herunter.

## Unit-Tests

Windows:

```powershell
.\gradlew.bat test
```

Linux oder macOS:

```sh
./gradlew test
```

Derzeit gibt es 19 Tests für Speicherung, Konfigurationsfehler, Vergabesperren, Icon-Migration und das Resourcepack. Die Font-Tests verwenden Minecrafts Client-Codec und Bitmap-Loader, ohne ein Spielfenster zu öffnen. Der Bericht liegt unter `build/reports/tests/test/index.html`.

## Testserver

Das PowerShell-Skript baut eine separate Test-Mod und startet einen lokalen Fabric-Server:

```powershell
.\tools\Test-Server.ps1 -AcceptMinecraftEula
```

`-AcceptMinecraftEula` bestätigt die Minecraft-EULA für diesen Testserver. Ohne den Parameter startet das Skript keinen Server.

Der Server bindet an `127.0.0.1:25585` und verwendet den Ordner `run-test/`. Der Test-Harness prüft Befehle mit Nicht-OP-, OP- und Konsolenrechten sowie Chatpakete und die TAB-Anbindung. Dazu gehören `PlayerLoadEvent`, ein simulierter Rejoin und `/tab reload`.

Die Ergebnisdatei ist `run-test/integration-results.txt`. Ein erfolgreicher Lauf endet mit `PASS ALL SERVER INTEGRATION TESTS`. Auf einem bereits verwendeten Testserver werden zusätzlich die gespeicherten Daten aus dem vorherigen Lauf geprüft. Der Harness beendet den Server nach den Tests selbst und gehört deshalb nicht auf einen Spielserver.

Eine zweite Laufzeitkombination lässt sich so prüfen:

```powershell
.\tools\Test-Server.ps1 -AcceptMinecraftEula -LoaderVersion 0.20.1 -FabricApiVersion 0.165.0+26.3
```

Geprüfte Kombinationen für Minecraft 26.3 mit TAB 6.2.1:

| Fabric Loader | Fabric API |
|---|---|
| 0.19.5 | 0.162.0+26.3 |
| 0.20.1 | 0.165.0+26.3 |

## Manuelle Prüfungen

Die Tests ersetzen keine Sichtprüfung im Spiel. Offen sind insbesondere verschiedene GUI-Skalierungen, zwei Clients mit unterschiedlichem Packstatus, die optionale Anzeige über Köpfen und das Zusammenspiel mit weiteren Chat- oder Tablist-Mods.

Die Chatprüfung vergleicht die übertragenen Signatur- und Inhaltsfelder mit einem synthetischen Prüfwert. Ein authentifizierter Online-Mode-Client ist dabei nicht beteiligt. Auch der Download von einer öffentlichen Resourcepack-URL gehört nicht zum lokalen Testlauf.
