# ServerTags

Mit `/tag` suchen sich Spieler einen Tag für Chat und Tablist aus. Welche Tags es gibt, legen die Admins fest. Sie können außerdem Item-Icons oder kurze Texte hinter dem Spielernamen vergeben.

Tags für Rollen wie OP oder Bürgermeister lassen sich für die freie Auswahl sperren. Der Träger kann seinen Tag trotzdem selbst ablegen.

ServerTags läuft auf dem Server. Spieler brauchen keine zusätzliche Mod. Für die Item-Icons laden sie ein Server-Resourcepack. Die Tablist übernimmt weiterhin TAB von NEZNAMY. Berechtigungen laufen über Minecraft-OPs; LuckPerms wird nicht benötigt.

## Installation

Version 1.2.0 braucht:

| Voraussetzung | Version |
|---|---|
| Minecraft Java | 26.3 |
| Java | 25 |
| Fabric Loader | ab 0.19.5 |
| Fabric API | ab 0.162.0+26.3 |
| NEZNAMY TAB | 6.2.1 |

1. Server stoppen.
2. Fabric API und die Fabric-Version von [TAB](https://modrinth.com/plugin/tab-was-taken/version/HlsRTW5F) installieren, falls sie noch fehlen.
3. [servertags-1.2.0.jar](dist/servertags-1.2.0.jar) in `mods/` kopieren.
4. Server starten und im Spiel `/tag` eingeben.

Beim ersten Start wird `config/servertags/` angelegt. Mitgeliefert sind Builder, Redstone, Farmer, Miner und Explorer. Bei einem Update bleiben deine eigenen Tags und Spielerdaten erhalten.

**Von 1.1.0 auf 1.2.0:** Die alte ServerTags-JAR ersetzen. Konfiguration und Resourcepack kannst du behalten. Tags ohne das neue Feld `adminOnly` bleiben frei wählbar. Zusätzliche Rollen werden nicht angelegt.

Bei einem Update von 1.0.x auch das Resourcepack austauschen: Die alten Font-Dateien konnten Kästchen statt Icons anzeigen. Weitere Änderungen stehen im [Changelog](CHANGELOG.md).

## Befehle für Spieler

| Befehl | Funktion |
|---|---|
| `/tag` | Tag-Menü öffnen und per Klick auswählen |
| `/tag list` | Tags mit ihren IDs anzeigen |
| `/tag select <id>` | Einen freien Tag auswählen |
| `/tag clear` | Eigenen Tag ablegen |
| `/tag help` | Hilfe anzeigen |

Ein Spieler kann einen Präfix-Tag tragen. Beim Wechsel wird der vorherige ersetzt. Die Auswahl bleibt nach dem Ausloggen und nach Serverneustarts gespeichert.

`/tag clear` entfernt auch einen Tag, den ein Admin zugewiesen hat. Ein zusätzlicher Item- oder Text-Suffix bleibt dabei erhalten.

## Befehle für Admins

Diese Befehle brauchen **OP-Level 2 oder höher**. In der Serverkonsole funktionieren sie ohne den führenden Schrägstrich.

| Befehl | Funktion |
|---|---|
| `/tag admin create <id> <text>` | Tag anlegen |
| `/tag admin delete <id>` | Tag und seine Zuweisungen löschen |
| `/tag admin rename <id> <text>` | Anzeigetext ändern |
| `/tag admin color <id> <farbe>` | Farbe setzen, etwa `gold` oder `#55FFFF` |
| `/tag admin list` | Alle Tags und ihre IDs anzeigen |
| `/tag admin lock <id>` | Tag für die Selbstauswahl sperren |
| `/tag admin unlock <id>` | Tag wieder freigeben |
| `/tag admin set <spieler> <id>` | Tag zuweisen, auch einen gesperrten |
| `/tag admin unset <spieler>` | Präfix eines Spielers entfernen |
| `/tag admin suffix set <spieler> <wert>` | Item-Icon oder Text vergeben |
| `/tag admin suffix clear <spieler>` | Suffix entfernen |
| `/tag admin suffix get <spieler>` | Suffix anzeigen |
| `/tag admin icons` | Verfügbare Icons mit Vorschau anzeigen |
| `/tag admin reload` | Konfiguration neu laden |

Für `<spieler>` kannst du einen Online-Namen verwenden. Für Offline-Spieler brauchst du die UUID. Tag-IDs bestehen aus Kleinbuchstaben, Zahlen, `_` und `-` und dürfen bis zu 32 Zeichen lang sein. Tab-Completion hilft bei Tags, Icons und Online-Namen.

### Tags nur durch OPs vergeben

Angenommen, du hast bereits einen Tag mit der ID `op`:

```text
/tag admin lock op
/tag admin set Steve op
```

Damit kann nur noch ein OP diesen Tag vergeben. Spieler, die ihn schon tragen, behalten ihn. Steve kann ihn jederzeit mit `/tag clear` ablegen oder einen freien Tag wählen. Will er den gesperrten Tag danach zurück, muss ihn ein OP erneut zuweisen.

Mit `/tag admin unlock op` gibst du den Tag wieder frei. Die Sperre wird in `tags.json` als `"adminOnly": true` gespeichert. Auch OPs verwenden für gesperrte Tags den Befehl `admin set`.

### Item-Icons und Texte

```text
/tag admin suffix set Steve netherite_ingot
/tag admin suffix set Alex diamond
/tag admin suffix set Steve wheat
/tag admin suffix set Alex dragon_egg
/tag admin suffix set Steve &6Gründer
```

Das Pack enthält 32 Item-Icons. Alle IDs stehen in der [Icon-Liste](docs/ICONS.md); im Spiel zeigt `/tag admin icons` die Vorschau. `text:diamond` zeigt das Wort „diamond“ an, statt das Icon zu verwenden. Normale Spieler können ihren Suffix weder ändern noch entfernen.

## Resourcepack einrichten

Lade [ServerTags-ResourcePack.zip](dist/ServerTags-ResourcePack.zip) auf deinen Webserver oder einen Datei-Host hoch. Der Link muss die ZIP-Datei direkt und ohne Anmeldung herunterladen. ServerTags hostet das Pack nicht selbst.

Trage bei gestopptem Server diese Werte in `server.properties` ein. Ersetze die Platzhalter für URL und Hash. Den Hash findest du in [ServerTags-ResourcePack.zip.sha1](dist/ServerTags-ResourcePack.zip.sha1). Es gibt auch eine [Vorlage mit dem aktuellen Hash](dist/server-resourcepack.properties.example).

```properties
resource-pack=https\://download.mc-packs.net/pack/3ff27ee083680f008075119098aa191ab1fd52f5.zip
resource-pack-sha1=3ff27ee083680f008075119098aa191ab1fd52f5
resource-pack-id=9ec9cb9a-a0a0-4e8e-a3bd-c97c81640bac
require-resource-pack=false
resource-pack-prompt={"text":"Item-Icons für ServerTags laden"}
```

Danach den Server starten und neu verbinden. Mit `require-resource-pack=true` machst du das Pack verpflichtend; wer es ablehnt, kann dann nicht beitreten. Nach jeder Änderung am ZIP muss auch der SHA-1 aktualisiert werden.

Hast du schon ein Serverpack, musst du die Dateien zusammenführen und das gemeinsame ZIP hochladen. Hinweise dazu stehen in der [Icon-Dokumentation](docs/ICONS.md#packs-zusammenführen).

Bis der Client das Pack als geladen bestätigt, zeigt ServerTags einen Text anstelle des Icons. Ein nur lokal aktiviertes Pack liefert diese Serverbestätigung nicht. Die Pack-ID liest die Mod aus den Servereinstellungen; `resourcePackId` in der Mod-Konfiguration kann normalerweise leer bleiben.

## TAB einrichten

Prüfe in `config/tab/config.yml`, ob die Namensformatierung aktiviert ist:

```yaml
tablist-name-formatting:
  enabled: true
```

ServerTags ergänzt Präfix und Suffix über die TAB-API. Header, Footer, Layout und Sortierung stellst du weiter in TAB ein. Bestehende Präfixe und Suffixe werden übernommen. Auch ein `/tab reload` wird berücksichtigt.

Normalerweise musst du keine Platzhalter eintragen. Für eine eigene Platzierung stehen `%servertags_prefix%` und `%rel_servertags_suffix%` zur Verfügung. Trage dieselben Tags nicht zusätzlich als festen Text ein, sonst erscheinen sie doppelt.

## Konfiguration

Unter `config/servertags/` liegen vier Dateien:

| Datei | Inhalt |
|---|---|
| `config.json` | Anzeige in Chat, TAB und Namensschildern |
| `tags.json` | Deine Tags mit Text, Farbe und Vergabesperre |
| `players.json` | Ausgewählte Tags und Suffixe, gespeichert über die UUID |
| `icons.json` | Item-IDs, Schriftzeichen, Texturen und Ersatztexte |

Die [Beispielkonfiguration](examples/config.json) enthält die Standardwerte. Mit `chatEnabled` und `tabEnabled` schaltest du die jeweilige Anzeige an oder aus. `nameTagsEnabled` ergänzt die Anzeige über den Köpfen; diese Option ist standardmäßig aus und noch nicht im Spiel visuell geprüft.

In `tags.json` kannst du auch `bold` und `italic` setzen. Ohne `adminOnly` oder mit `adminOnly: false` ist ein Tag frei wählbar. Textsuffixe unterstützen etwa `&6`, `&l`, `&o` und `&r`. Tags sind auf 48 Zeichen, Suffixe auf 96 Zeichen begrenzt.

Dateien bei gestopptem Server bearbeiten oder danach `/tag admin reload` ausführen. Bei einem fehlerhaften Reload bleibt der bisherige Stand aktiv. Die vorige Dateiversion liegt als `.bak` daneben.

## Vanilla-Entity-Tags

Der ursprüngliche Vanilla-Befehl ist unter `/entitytag` erreichbar:

```text
/entitytag @s add projekt_builder
/entitytag @s list
/entitytag @s remove projekt_builder
```

Falls Commandblöcke oder Datapacks bisher Vanilla-`/tag` nutzen, musst du sie auf `/entitytag` umstellen. Ist dieser Name schon durch eine andere Mod belegt, meldet ServerTags den Konflikt beim Start.

## Wenn etwas nicht funktioniert

- **Text statt Icon:** Download-URL, SHA-1 und Pack-ID prüfen, dann neu verbinden.
- **Kästchen statt Icon:** Aktuelles ZIP verwenden und den Hash aktualisieren. Besonders bei einem Update von 1.0.x ist das alte Pack die Ursache.
- **Tag im Chat, aber nicht in TAB:** `tablist-name-formatting.enabled` und die TAB-Version prüfen, danach `/tab reload` ausführen.
- **Doppelte Tags:** Prüfen, ob derselbe Text schon in TABs Präfixen oder Suffixen steht.
- **Gesperrter Tag nicht auswählbar:** Ein OP muss ihn mit `/tag admin set` vergeben. `/tag clear` bleibt für den Träger möglich.
- **Adminbefehl verweigert:** OP-Level in `ops.json` prüfen. Benötigt wird mindestens Level 2.
- **Fehler in einer JSON-Datei:** Den Eintrag anhand der Meldung im Serverlog korrigieren oder die `.bak` wiederherstellen.

Andere Mods, die dieselben TAB-Präfixe oder Suffixe setzen, können die Anzeige überschreiben. Eine andere Chat-Mod kannst du weiter nutzen, indem du `chatEnabled` für ServerTags ausschaltest.

## Selbst bauen

Du brauchst ein Java-25-JDK. Im Projektordner:

```powershell
.\gradlew.bat build resourcePack distribution
```

Die fertigen Dateien landen in `dist/`. Alternativ gibt es `tools/Build.ps1`.

Tests startest du mit `gradlew.bat test`. Für den lokalen Servertest gibt es `tools/Test-Server.ps1 -AcceptMinecraftEula`. Das Skript nutzt eine eigene Testwelt und beendet den Server anschließend selbst. Die Test-Harness-JAR gehört deshalb nicht auf deinen Spielserver. Ergebnisse und offene Prüfungen stehen im [Testbericht](docs/TESTBERICHT.md).

Der Code steht unter der [MIT-Lizenz](LICENSE). Minecraft-Texturen unterliegen den Rechten von Mojang/Microsoft. ServerTags ist kein offizielles Minecraft-Produkt.
