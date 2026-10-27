# Item-Icons

ServerTags enthält 32 Itemgrafiken mit transparentem Hintergrund. Im Spiel zeigt `/tag admin icons` die Vorschau. [Hier ist die Übersicht als Bild.](icons-preview.png)

Mit `/tag admin suffix set Steve netherite_ingot` vergibst du zum Beispiel einen Netherite-Barren. `minecraft:netherite_ingot` funktioniert ebenfalls. Der bisherige Präfix bleibt erhalten.

| Item-ID | Codepoint | Text ohne geladenes Pack |
|---|---|---|
| `netherite_ingot` | U+E000 | [Netherite-Barren] |
| `diamond` | U+E001 | [Diamant] |
| `gold_ingot` | U+E002 | [Goldbarren] |
| `iron_ingot` | U+E003 | [Eisenbarren] |
| `emerald` | U+E004 | [Smaragd] |
| `ancient_debris` | U+E005 | [Antiker Schrott] |
| `netherite_scrap` | U+E006 | [Netherite-Schrott] |
| `redstone` | U+E007 | [Redstone] |
| `lapis_lazuli` | U+E008 | [Lapislazuli] |
| `coal` | U+E009 | [Kohle] |
| `copper_ingot` | U+E00A | [Kupferbarren] |
| `oak_log` | U+E100 | Eichenholz |
| `oak_sapling` | U+E101 | Setzling |
| `wheat` | U+E102 | Weizen |
| `carrot` | U+E103 | Karotte |
| `potato` | U+E104 | Kartoffel |
| `beetroot` | U+E105 | Rote Bete |
| `sugar_cane` | U+E106 | Zuckerrohr |
| `melon_slice` | U+E107 | Melone |
| `apple` | U+E108 | Apfel |
| `bread` | U+E109 | Brot |
| `golden_carrot` | U+E10A | Goldene Karotte |
| `honey_bottle` | U+E10B | Honig |
| `ender_pearl` | U+E10C | Enderperle |
| `ender_eye` | U+E10D | Enderauge |
| `dragon_egg` | U+E10E | Drachenei |
| `amethyst_shard` | U+E10F | Amethyst |
| `experience_bottle` | U+E110 | Erfahrungsfläschchen |
| `fishing_rod` | U+E111 | Angel |
| `totem_of_undying` | U+E112 | Totem |
| `golden_apple` | U+E113 | Goldener Apfel |
| `blaze_rod` | U+E114 | Lohenrute |

## Eigene Icons ergänzen

Die Zuordnung steht in `config/servertags/icons.json`. Jeder Eintrag braucht eine ID, einen freien Codepoint aus U+E000–U+F8FF, einen Texturpfad und einen Ersatztext. Die [mitgelieferte Datei](../examples/icons.json) zeigt das Format.

Übernimm deine Ergänzung auch nach `examples/icons.json` im Projekt und baue das Pack mit `gradlew.bat resourcePack` neu. Bei einer eigenen Textur muss das PNG im Pack enthalten sein. Pack und Serverkonfiguration müssen dieselbe Zuordnung verwenden. Anschließend das neue ZIP hochladen, den SHA-1 ändern und neu verbinden. Insgesamt sind bis zu 512 Icons möglich.

Updates ergänzen fehlende Standard-Icons in einer bestehenden `icons.json`. Eigene Einträge bleiben erhalten. Ist ein Standard-Codepoint bereits durch ein eigenes Icon belegt, wird der betreffende Standard-Eintrag übersprungen und eine Warnung ins Serverlog geschrieben.

## Packs zusammenführen

Die Dateien unter `assets/servertags/` vollständig in das gemeinsame Pack übernehmen. Hat es bereits eine `assets/minecraft/font/default.json`, füge diesen Eintrag zu deren `providers`-Liste hinzu:

```json
{"type": "reference", "id": "servertags:icons"}
```

Im ZIP darf es nur eine Datei mit diesem Pfad geben. Danach das gemeinsame Pack neu hosten und dessen SHA-1 in `server.properties` eintragen.

## Aufbau des Packs

Die Icon-Schrift heißt `servertags:icons`. Die Bitmap-Provider stehen in `assets/servertags/font/icons.json`. Ihre Typen heißen `bitmap` und `reference`, ohne `minecraft:` davor. Chat und TAB erhalten für die Itemzeichen ausdrücklich diesen Font. Ein Verweis im Default-Font ergänzt dieselben Zeichen für andere Verwendungen.

29 Icons verwenden die Vanilla-Texturen des Clients. Für Antiken Schrott, Eichenholz und Drachenei liegen gerenderte Blockgrafiken im ZIP. Die Schriftzeichen sind neun Pixel hoch, der Ascent beträgt acht. Das Packformat für Minecraft 26.3 ist 97.1.

Die Renderer liegen unter `tools/GenerateBlockIcon.java` und `tools/GenerateItemIcons.java`. Die erzeugten PNGs sind bereits unter `src/main/resources/pack/textures/item/` enthalten. Für einen normalen Build müssen sie nicht erneut gerendert werden.

Das Vorschaubild liegt als `pack.png` mit 128×128 Pixeln im ZIP-Hauptverzeichnis. Seine Quelldatei ist `src/main/resources/pack/pack.png`.

Die Minecraft-Grafiken unterliegen den Rechten von Mojang/Microsoft. Die MIT-Lizenz des Java-Codes gilt nicht für diese Texturen.
