# Sicherheitsrichtlinie

## Unterstützte Versionen

| Version | Unterstützt |
| ------- | ----------- |
| 1.0.0   | :white_check_mark: |

## Sicherheitslücken melden

Sicherheitslücken werden nicht über öffentliche Issues gemeldet. Bitte senden Sie einen Bericht an den Projektverantwortlichen (siehe `CONTRIBUTING.md` / Repository-Kontakt) oder nutzen Sie das private Vulnerability-Reporting der Plattform.

Geben Sie möglichst folgende Informationen an:
- Betroffene Version und Build
- Beschreibung der Schwachstelle und Schritte zur Reproduktion
- Potenzielle Auswirkungen und Schweregrad
- Optional: einen Fix-Entwurf

Wir bestätigen den Eingang und informieren über den weiteren Umgang und etwaige Patches.

## Sicheres Verwenden

- Der Editor speichert Einstellungen und Dokumente im plattformüblichen Benutzerkonfigurations- bzw. Dokumentverzeichnis. Öffnen und speichern Sie nur Dateien aus vertrauenswürdigen Quellen.
- Die Einstellungen werden UTF-8-kodiert abgelegt (`ApplicationSettings`).
- Der Ausdruck erfolgt lokal; es werden keine Druckdaten an externe Dienste übermittelt.

## Abhängigkeiten

Das Projekt nutzt JavaFX 25, die RichTextArea-Bibliothek (`3.0-RG`, Git-Submodule) und JUnit/TestFX für Tests. Abhängigkeiten werden über Maven mit festgelegten Versionen bezogen (`https://nexus.gluonhq.com` für Gluon-Artefakte). Halten Sie die JDK und Build-Toolchain aktuell.
