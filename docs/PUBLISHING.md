# Auf GitHub veröffentlichen

`tools/Export-Source.ps1` erstellt `dist/ServerTags-<version>-GitHub.zip`. Eine vorhandene ZIP-Datei lässt sich mit `-Force` ersetzen.

Entpacke das ZIP und verwende den Inhalt des Ordners `ServerTags` für dein Repository. Auch `.gitignore`, `.gitattributes` und `.editorconfig` gehören dazu. Die Datei `gradle/wrapper/gradle-wrapper.jar` ist Teil des Build-Wrappers und muss mit hochgeladen werden.

Lokale JDKs, Gradle-Caches, Testwelten, Logs und Build-Ergebnisse sind nicht im Bundle enthalten. Dafür gibt es entsprechende Einträge in `.gitignore`.

Wenn du unter Windows mit Git arbeitest, markiere den Shell-Wrapper nach `git add .` als ausführbar:

```sh
git update-index --chmod=+x gradlew
```

Die fertige Mod-JAR, `ServerTags-ResourcePack.zip` und dessen SHA-1-Datei kannst du getrennt als Release-Dateien veröffentlichen. Sie werden mit `gradlew.bat build resourcePack distribution` erzeugt und gehören nicht in den Quellcodeordner.

Der Quellcode-Build benötigt ein Java-25-JDK und beim ersten Lauf Internetzugriff für die öffentlichen Abhängigkeiten. Build- und Testbefehle stehen in der README und unter [Tests](TESTING.md).
