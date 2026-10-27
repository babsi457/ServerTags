# Änderungen

## 1.2.0 – 27.10.2026

- `/tag admin lock <id>` sperrt vorhandene Tags für die Selbstauswahl. `/tag admin unlock <id>` gibt sie wieder frei. Beide Befehle benötigen native OP-Rechte ab Level 2.
- Gesperrte Tags werden ausschließlich mit `/tag admin set <spieler> <id>` vergeben. Die Sperre wird auch bei direkter Befehlseingabe geprüft; Menü und Vorschläge berücksichtigen sie.
- Jeder Empfänger darf seinen Präfix weiterhin selbst mit `/tag clear` ablegen oder gegen einen freien Tag tauschen. Bestehende Zuweisungen werden beim Sperren nicht entfernt.
- Speicherung als `adminOnly` pro Tag; alte Tag-Dateien ohne dieses Feld bleiben standardmäßig frei wählbar. Umbenennen und Farbänderungen bewahren die Einstellung.
- Es werden keine zusätzlichen Präfix-Tags oder Rollen angelegt. Resourcepack und SHA-1 sind identisch zu 1.1.0.

Update von 1.1.0: Server stoppen, alte ServerTags-JAR durch `servertags-1.2.0.jar` ersetzen und starten. Konfiguration, Spielerdaten und bereits verteiltes Resourcepack bleiben erhalten.

## 1.1.0 – 27.10.2026

- Font-Ladefehler behoben: Minecraft erwartet `bitmap` und `reference` als Provider-Typen. Der Client hat die bisher verwendeten Namen `minecraft:bitmap`/`minecraft:reference` abgelehnt und Kästchen angezeigt.
- TAB erhält für Icon-Suffixe zusätzlich den expliziten Font `servertags:icons` über seine dokumentierte Font-Syntax.
- 32 reine Itemgrafiken ohne Rahmen: Metalle, Diamant/Smaragd, Holz/Setzling, Landwirtschaft, Enderperle/Enderauge/Drachenei und weitere Auszeichnungen.
- Vorhandene `icons.json` wird um fehlende Standard-Icons ergänzt. Eigene Werte und Codepoint-Belegungen werden erhalten; Konflikte werden mit einer Warnung übersprungen.
- Regressionstests verwenden Minecrafts tatsächlichen Client-Codec und laden sämtliche Bitmap-Glyphen samt PNGs. Die Servertests prüfen nun auch den effektiven Icon-Font in TAB-Paketen.

Update: Alte Mod-JAR durch `servertags-1.1.0.jar` ersetzen, neues Resourcepack hosten, `resource-pack-sha1` aktualisieren und neu verbinden. Konfiguration und Spielerdaten können bestehen bleiben.

## Resourcepack-Vorschaubild – 27.10.2026

- Eigenes ServerTags-Vorschaubild als `pack.png` (128×128 Pixel) im ZIP-Hauptverzeichnis ergänzt.
- Pack-Generator liefert das Bild bei jedem Neubau mit; ZIP, SHA-1-Datei und Server-Properties-Vorlage wurden neu erzeugt. Die installierte Mod-JAR muss dafür nicht ersetzt werden.

## 1.0.1 – 27.10.2026

- Startabbruch auf Fabric Loader 0.19.5 mit Fabric API 0.162.0+26.3 behoben. Die Mod wird gegen diese Versionen gebaut und mit ihnen auf einem echten Fabric-26.3-Server getestet.
- Compile-Abhängigkeiten und veröffentlichte Mindestversionen beziehen ihre Werte aus denselben Gradle-Eigenschaften.
- Regressionstest für die veröffentlichten Abhängigkeiten ergänzt; Servertests prüfen die tatsächlichen geladenen Fabric-Versionen.
- Testskript übernimmt die Projektversion automatisch und entfernt vor jedem Lauf alte verwaltete Test-JARs, damit keine zwei Versionen gleichzeitig geladen werden.

Installation: Server stoppen, `servertags-1.0.0.jar` aus `mods/` entfernen, `servertags-1.0.1.jar` hineinkopieren und starten. Bestehende JSON-Dateien, Spielerauswahlen, Suffixe und Resourcepack bleiben kompatibel.
