# Entwicklung unter Linux

Dieses Dokument beschreibt einen vollständigen Entwicklungs-Checkout von
**Mardexas / RichMarkDownEditor**, auch auf einem neuen Rechner. Das öffentliche
Hauptrepository ist <https://github.com/RainerGa/Mardexas>. Zum Projekt gehört
der in `.gitmodules` eingetragene RichTextArea-Fork als eigenes Git-Repository.
Ein GitHub-Download als ZIP ersetzt keinen rekursiven Git-Checkout.

## Voraussetzungen

- Linux mit Bash und Git; Internetzugriff auf GitHub.
- Für Build, Tests und IDE: **JDK 25**, inklusive `javac`, sowie Maven 3.8+.
  `JAVA_HOME` auf das JDK setzen und `$JAVA_HOME/bin` im `PATH` voranstellen.
  Neuere Java-Hauptversionen sind für diesen Ablauf nicht freigegeben: Sie
  können insbesondere mit der verwendeten JaCoCo-Version inkompatibel sein.
- Für den erstmaligen Build: Zugriff auf die in den POMs konfigurierten
  Maven-Repositories. Maven lädt JavaFX und weitere Bibliotheken selbst.
- Zum Start der Oberfläche: grafische Linux-Sitzung mit den für JavaFX nötigen
  GTK-Bibliotheken und installierten Schriftarten. Headless-Tests ersetzen
  keinen Test der Oberfläche auf dem jeweiligen Rechner.
- Optional: VS Code mit Java-Unterstützung oder Eclipse/IntelliJ mit Maven-Import.
  Python 3 wird nur für die zusätzlichen Skript-Tests benötigt.

GraalVM, native-image, Flatpak und Installer-Werkzeuge sind für die normale
Weiterentwicklung nicht erforderlich; siehe [PACKAGING.md](PACKAGING.md).

## Ein Skript vorab herunterladen und alles auschecken

Nach Veröffentlichung des Skripts im Standardbranch:

```bash
curl --fail --location --output checkout-linux.sh \
  https://raw.githubusercontent.com/RainerGa/Mardexas/main/scripts/checkout-linux.sh
# Heruntergeladenes Skript vor dem Ausführen ansehen.
bash checkout-linux.sh --help
bash checkout-linux.sh --verify "$HOME/Projekte/Mardexas"
cd "$HOME/Projekte/Mardexas"
```

`--verify` führt nach dem Checkout `mvn verify` einschließlich Tests aus.
Die Build-Ausgaben liegen pro Modul unter `target/checkout-verification`,
getrennt von den üblichen IDE-Ausgaben. Ohne `--verify` reichen Bash und Git;
JDK und Maven lassen sich anschließend einrichten. Das Skript installiert keine
Systempakete und überschreibt keine vorhandenen Verzeichnisse. Bei einem Fehler
bleibt ein eventuell angelegter Checkout zur Untersuchung erhalten.

**Veröffentlichung:** Ein Checkout enthält ausschließlich bereits gepushte
Commits. Solange dieses Skript nur lokal vorliegt, kann es als einzelne Datei
auf den neuen Rechner kopiert und dort mit `bash checkout-linux.sh ...`
ausgeführt werden. Der Download oben funktioniert erst, wenn die Datei auf
GitHub im angegebenen Branch veröffentlicht ist. Ein lokaler Entwicklungsbranch
muss ebenfalls zuerst gepusht werden, bevor er auf einem anderen Rechner
ausgecheckt werden kann.

Branch oder Tag sowie eigenes Fork-Repository auswählen:

```bash
bash checkout-linux.sh --ref main "$HOME/Projekte/Mardexas-main"
bash checkout-linux.sh --repository https://github.com/DEIN-NAME/Mardexas.git \
  --ref feature/meine-aenderung --verify "$HOME/Projekte/MeinMardexas"
```

`--ref` akzeptiert einen vorhandenen Branch oder Tag; ein Tag ergibt einen
detached HEAD. Zum Weiterentwickeln anschließend `git switch -c feature/name`
verwenden. Ohne `--ref` gilt der Standardbranch des gewählten Remote-Repositorys.
Für private Repositories oder SSH müssen die üblichen Git-Zugangsdaten bzw.
SSH-Schlüssel vorher eingerichtet sein. `MVN=/pfad/zu/mvn` wählt bei Bedarf ein
anderes Maven-Executable. Der Repository-Parameter ändert die in `.gitmodules`
festgelegte Quelle der Submodule nicht.

## Alternative mit Git und Maven

```bash
git clone --recurse-submodules https://github.com/RainerGa/Mardexas.git
cd Mardexas
mvn --batch-mode --no-transfer-progress \
  '-Dmardexas.build.directory=${project.basedir}/target/checkout-verification' verify
```

Die einfachen Anführungszeichen sind erforderlich: Maven soll
`${project.basedir}` für jedes Modul getrennt auflösen. Die Root-POM aktiviert
das RichTextArea-Modul automatisch, wenn `rich-text-area/rta/pom.xml` vorhanden
ist. Kein separates JAR und keine Kopie aus einem `Downloads`-Ordner sind nötig.

Bei einem bereits ohne Submodule angelegten Checkout:

```bash
git submodule sync --recursive
git submodule update --init --recursive
```

Submodule werden auf genau die im Hauptrepository festgelegten Commits gesetzt.
`git submodule update --remote` gehört nicht zum reproduzierbaren Einrichten.

## Projektstruktur

```text
Mardexas/
├── pom.xml                         Maven-Reaktor und gemeinsame Konfiguration
├── .gitmodules                     Quellen der Git-Submodule
├── RichMarkDownEditor.code-workspace  Portabler VS-Code-Workspace
├── scripts/
│   ├── checkout-linux.sh           Eigenständig ausführbares Checkout-Skript
│   └── test_checkout_linux.py      Offline-Tests mit lokalen Git-Repositories
├── editor/
│   ├── pom.xml
│   ├── start.sh                    Editor unter Linux starten
│   └── src/{main,test}/            Anwendung, öffentliche API, Ressourcen, Tests
├── emoji-noto/                     Emoji-Daten als Maven-Modul
├── rich-text-area/                 Eigenes Git-Repository (Submodul)
│   └── rta/                        Verwendetes RichTextArea-Maven-Modul
├── integration-demos/              JavaFX-Integrationsbeispiele mit Tests
├── packaging/                      Bibliotheks-/Release-Builds und Prüfwerkzeuge
├── docs/                           Entwicklung, API, Packaging und Validierung
└── .github/workflows/              CI und Release-Workflows
```

`target/`, `dist/`, lokale Caches und IDE-Metadaten sind erzeugte bzw. lokale
Dateien. Sie werden beim Einrichten nicht von einem anderen Rechner übernommen.
Die Java-Quellen liegen in den Maven-Modulen; `scripts/` enthält die
Entwicklungswerkzeuge, `packaging/` die Werkzeuge zur Auslieferung.

## IDE öffnen, entwickeln und prüfen

```bash
code RichMarkDownEditor.code-workspace
# Alternativ den Projektordner in der IDE öffnen und die Root-pom.xml importieren.
./editor/start.sh
```

Der Workspace verwendet ausschließlich relative Projektpfade. In der IDE JDK 25
für das Projekt einstellen und den Maven-Import vollständig abwarten. Die
Java-Erweiterung selbst kann eine eigene Laufzeit verwenden. Startskript und
`--verify` sind getrennte Schritte: `editor/start.sh` überspringt beim Bauen die
Tests und ersetzt deshalb keinen geprüften Build.

Bei jeder Änderung gehören Dokumentation, passende Regressionstests und zwei
Prüfungen zum Abschluss:

1. Maven-Build einschließlich relevanter Tests erfolgreich ausführen; für eine
   vollständige Prüfung den oben angegebenen `verify`-Befehl verwenden.
2. IDE-Diagnosen für **Haupt- und Testquellen aller Module** prüfen. Fehler und
   Compilerwarnungen im Quellcode beheben, statt Warnungen abzuschalten.
   Maven/javac und der Eclipse-Compiler der VS-Code-Java-Erweiterung prüfen
   unterschiedlich; ein erfolgreicher Maven-Build belegt keine leere Problemliste.
   TODO-Aufgaben von Compilerwarnungen unterscheiden.

Bleiben nach einer Korrektur veraltete Meldungen, in VS Code
`Java: Clean Java Language Server Workspace` ausführen und neu importieren.
Den tatsächlichen IDE-Zustand anschließend erneut kontrollieren.

Tests für das Checkout-Werkzeug (ohne Netzwerk oder Java-Build):

```bash
bash -n scripts/checkout-linux.sh
python3 -B -m unittest discover -s scripts -p 'test_*.py'
```

Siehe auch [API.md](API.md), [VALIDATION.md](VALIDATION.md) und
[CONTRIBUTING.md](../CONTRIBUTING.md).

## Änderungen an RichTextArea veröffentlichen

Das Submodul hat seine eigene Git-Historie. Dort zuerst einen Arbeitsbranch
anlegen, Änderungen prüfen, committen und in ein erreichbares Fork-Repository
pushen. Erst danach im Hauptrepository den neuen Submodul-Commit übernehmen,
gezielt stagen, committen und pushen. Bei einem anderen Fork auch `.gitmodules`
aktualisieren. Sonst können Dritte den referenzierten Commit nicht auschecken.

Vor jedem Commit den Status von Hauptrepository und Submodul getrennt prüfen;
generierte Dateien und lokale Einstellungen nicht pauschal mit aufnehmen.
