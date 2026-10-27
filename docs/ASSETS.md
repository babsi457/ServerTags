# Grafiken und Drittbestandteile

Der Java-Code des Projekts steht unter der MIT-Lizenz in `LICENSE`. Diese Lizenz gilt nicht automatisch für Grafiken oder Dateien anderer Projekte.

- `src/main/resources/pack/pack.png` ist das ServerTags-Packlogo.
- Die meisten Icon-Einträge referenzieren die Vanilla-Texturen auf dem Minecraft-Client. Diese Texturen werden nicht mit dem Quellcode kopiert.
- Die PNGs unter `src/main/resources/pack/textures/item/` sind Projektionen von Minecraft-Blocktexturen und -Modellen. Die verwendeten Spielgrafiken unterliegen den Rechten von Mojang/Microsoft.
- `docs/icons-preview.png` zeigt die für das Resourcepack verwendeten Icons.
- Gradle-Wrapper-Skripte und die Wrapper-JAR stammen von Gradle. Die ursprünglichen Apache-2.0-Lizenzhinweise bleiben in den Wrapper-Dateien erhalten.

TAB, Fabric, Minecraft und das JDK werden nicht im Quellcode-Bundle mitgeliefert. Gradle lädt die Build-Abhängigkeiten über ihre jeweiligen Repositories. TAB wird nur zum Kompilieren und Testen verwendet und nicht in die Mod-JAR eingebettet.
