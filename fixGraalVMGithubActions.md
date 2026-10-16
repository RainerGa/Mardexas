# Fix-Plan: GitHub Actions Binaries (jpackage + GraalVM Native Image)

> Stand: 15.10.2026 – Ergebnis der Problemanalyse zu `release-packages.yml` (601 Zeilen).
> Ziel: Aus „grün, aber schwarzes Fenster / ungetestet“ wird „grün = startet + rendert auf Linux/Windows/macOS“.
> Prinzip: **Kein Artefakt ohne Smoke-Test. Kein Native ohne verifizierte Metadaten. Kein Merge ohne `mvn test`.**

Datei-Ablage: `/fixGraalVMGithubActions.md` (dieses Dokument).
Betroffene Dateien:
- `.github/workflows/release-packages.yml`
- `packaging/build-app-image.sh` (85 Zeilen)
- `packaging/build-native-image.sh` (125 Zeilen)
- `packaging/trace-reachability.sh` (297 Zeilen)
- `packaging/smoke-test-native-linux.sh` (64 Zeilen)
- `packaging/merge-reachability-metadata.py`, `packaging/trace-diff.py`
- `packaging/native-image/{linux,linux-sw,win,mac}/reachability-metadata.json`
- `packaging/flatpak/{io.github.rainerga.Mardexas.yml,mardexas,...}`
- `editor/pom.xml`, `editor/src/main/java/de/richmarkdown/...` (`RichMarkDownEditorApp`, `EditorWindow`, `MarkdownEditor`, `I18n`)
- `editor/src/main/resources/de/richmarkdown/ui/app.css`, `.../i18n/messages*.properties`

---

## 0. Übersicht – Phasen und Priorität

| Phase | Titel | Prio | Aufwand | Entblockt |
|---|---|---|---|---|
| P0 | Diagnose sichern & reproduzierbar machen | P0 – sofort | 0,5 Tage | Alle anderen (Ground Truth) |
| P1 | Smoke-Tests für ALLE Artefakte (grün=lauffähig) | P0 – kritisch | 1–2 Tage | Vertrauen in Linux/Win/Mac jpackage + Native-Win/Mac |
| P2 | JavaFX-Classifier / `JAVAFX_PLATFORM` wirklich nutzen + mac-arch Matrix | P0 – kritisch | 1 Tag | Win/Mac falsche Natives, mac x86_64 fehlt |
| P3 | Black-Screen Fix: Prism-Fallback + Linux-Deps + CSS/Bundle-Härtung | P0 – kritisch | 1 Tag | Linux jpackage schwarz + Fehlermeldungen |
| P4 | Native-Image reparieren: Win/Mac-Metadaten + Flags + GraalVM-Pin | P1 – hoch | 2–3 Tage | Win/Mac-Native + Linux-Native-Stabilität |
| P5 | Packaging-Lücken: MSI, macOS-Gatekeeper, Flatpak, Publish | P1 – hoch | 1 Tag | Installierbarkeit beim Nutzer |
| P6 | CI-Härtung: Tests an, Versionen pinnen, Checksums/Signaturen | P2 – mittel | 0,5–1 Tag | Regressionsschutz |

Empfohlene Reihenfolge: P0 → P1 → P2 → P3 → P4 → P5 → P6. P1+P2+P3 zusammen beheben „Linux schwarz + Win/Mac blind“.

Erfolgskriterien (Release gilt erst dann als fertig):
- [ ] Alle 6 Jobs bauen + alle Artefakte bestehen Smoke-Test mit Screenshot/Fenster-Nachweis.
- [ ] Linux portable startet unter `xvfb` ohne `QuantumRenderer/Graphics Device failed`-Error, zeigt Menü+Editor (Screenshot).
- [ ] Win MSI + portable, macOS ZIP (beide Archs) starten (CI-Log + Screenshot).
- [ ] Natives Linux/Win/Mac starten, öffnen File-Menü / zeigen Editor (Linux: xdotool wie bisher, Win/Mac: Prozess+Screenshots, kein `|| true`).
- [ ] `mvn test` läuft in CI (kein globales `-DskipTests` mehr für jpackage-Jobs).
- [ ] Win/Mac-Metadaten >10 kB, enthalten Reflection+Resources+Bundles für FileChooser/Print/RTA-Skin (nicht mehr `53 Bytes` Stub).

---

## P0 – Diagnose sichern (0,5 Tage)

**Warum:** Aktuell ist `dist/linux/Mardexas/lib/` leer, `editor/target/*.jar` fehlt → letzter lokaler Build unvollständig. Ohne Logs raten wir bei Prism vs. Bundle vs. CSS.

### P0.1 Frischen Befund sichern
1. `packaging/build-app-image.sh` einmal lokal mit `JAVAFX_PLATFORM=linux APP_VERSION=1.0.0` laufen lassen (nicht abbrechen).
2. Danach sichern:
   ```bash
   ls -R dist/linux/Mardexas | head -n 100
   ls -lh editor/target/jpackage-input/ | head -n 50
   unzip -l dist/Mardexas-*-linux-x86_64-portable.tar.gz | head -n 80
   ldd dist/linux/Mardexas/lib/runtime/lib/libglassgtk3.so 2>&1 | head -n 30
   ./dist/linux/Mardexas/bin/Mardexas > /tmp/mardexas-jpackage.log 2>&1 &
   # Fenster prüfen, dann Log sichern
   cat /tmp/mardexas-jpackage.log
   ./dist/native/linux/Mardexas > /tmp/mardexas-native.log 2>&1 &
   cat /tmp/mardexas-native.log
   ```
3. Erwartete Signaturen notieren und in dieses Dokument Anhang A eintragen:
   - `Prism-ES2 Error / QuantumRenderer / Unable to open DISPLAY` → P3.
   - `MissingResourceException: de.richmarkdown.i18n.messages` / `app.css is null` → P3+P4.
   - `UnsatisfiedLinkError: libglassgtk3.so / libprism_*.so` → P3 (Deps).
   - `Error: java.home property not set` → `RichMarkDownEditorApp.ensureJavaHomeForNativeImage()` prüfen (P4).

### P0.2 CI-Logs inventarisieren
- Letzten `release-packages.yml`-Run öffnen, pro Job Laufzeit + Artefakt-Größe notieren (fällt `macos` nur 1 Arch? Ja → P2).
- `svm_err_*.md` aus `dist/native/linux/` aufheben (enthält `-H:AddExports=...viewModel`-Typo → P4.3).

**Done wenn:** Anhang A mit 2 Logs + `ldd` + `unzip -l` ausgefüllt ist.

---
## P1 – Smoke-Tests für ALLE Artefakte (1–2 Tage, P0-kritisch)

**Problem heute:** Nur Linux-Native hat echten Test (`smoke-test-native-linux.sh`). Win/Mac-Native: `timeout 5 ... || true` (immer grün). Alle drei jpackage-Jobs: gar kein Test. Genau deshalb „grün + schwarz“.

### P1.1 Neue Skripte anlegen (je OS eines für jpackage + Wiederverwendung für Native)

1. `packaging/smoke-test-app-linux.sh` (neu):
   - `xvfb-run` + App aus `dist/linux/Mardexas/bin/Mardexas` starten, Log in Datei.
   - Wie `smoke-test-native-linux.sh`: `xdotool search --onlyvisible --name "^Mardexas|RichMarkDownEditor"` max 20s, dann File-Menü-Klick (Koordinaten aus bestehendem Skript übernehmen), Screenshot via `import -window root`.
   - Fail-Kriterien: Fenster fehlt / `NullPointerException|Exception in thread|Graphics Device initialization failed|Error initializing QuantumRenderer|Unable to open DISPLAY` im Log / Exit-Code !=0 nach Menü-Test.
   - Bei Erfolg Screenshot als Artefakt hochladen (`screenshot-jpackage-linux.png`).
2. `packaging/smoke-test-app-win.ps1` (neu):
   - `Start-Process dist/win/Mardexas/Mardexas.exe -PassThru`, 20s warten, `Get-Process Mardexas` prüfen, Screenshot via `Add-Type System.Windows.Forms.SendKeys` / Screenshot-C# snippet, Prozess sauber beenden. Fail wenn Prozess nach 5s weg ist (Crash) oder Log `Exception` enthält.
   - Gleiches Skript mit Parameter für `dist/native/win/Mardexas.exe` wiederverwenden.
3. `packaging/smoke-test-app-mac.sh` (neu):
   - `open dist/mac/Mardexas.app` bzw. `dist/mac-aarch64/...`, `pgrep -f Mardexas`, `screencapture -x /tmp/shot-mac.png`, Fail wenn kein Prozess / Crash-Report in `~/Library/Logs/DiagnosticReports/`.
   - Gleiches Skript für `dist/native/mac/Mardexas`.
4. Bestehendes `smoke-test-native-linux.sh` unverändert lassen (Referenz), zusätzlich Screenshot-Artefakt hochladen.

### P1.2 Workflow einhängen (`release-packages.yml`)

- Job `linux`: nach `Build Linux application image` einfügen:
  ```yaml
  - name: Smoke-test Linux application image
    run: |
      sudo apt-get update && sudo apt-get install --yes --no-install-recommends xvfb xdotool imagemagick x11-utils
      bash packaging/smoke-test-app-linux.sh dist/linux/Mardexas/bin/Mardexas
  - name: Upload Linux smoke screenshot
    if: always()
    uses: actions/upload-artifact@v4
    with: { name: smoke-linux-jpackage, path: /tmp/shot-*.png, if-no-files-found: warn }
  ```
- Job `windows`: nach portable-ZIP + nach MSI (entpackt testen) `pwsh packaging/smoke-test-app-win.ps1 -AppPath dist/win/Mardexas/Mardexas.exe`.
- Job `macos`: nach `Build macOS application image` `bash packaging/smoke-test-app-mac.sh`.
- Jobs `native-image-win` / `native-image-macos`: `timeout 5 ... || true` **ersetzen** durch echte Smoke-Skripte + `fail` bei Crash (kein `|| true` mehr). Linux-Native behält `smoke-test-native-linux.sh`, zusätzlich Screenshot-Upload.
- Kein Job darf Artefakte hochladen, wenn sein Smoke-Test rot ist (Reihenfolge: build → smoke → archive → upload).

**Done wenn:** Ein absichtlich kaputtes Binary (z. B. `exit 1` Stub) macht den Run rot. Screenshots liegen als Artefakte.

---

## P2 – JavaFX-Classifier + Arch-Matrix korrigieren (1 Tag, P0-kritisch)

**Problem:** `JAVAFX_PLATFORM` steuert nur `OUTPUT_DIR`, nicht Maven-Natives. Host-OS entscheidet via `${javafx.platform}`. Basis-JAR nur Manifest, Natives in Classifier-JARs. `build-app-image.sh` kennt `mac-aarch64`, `build-native-image.sh` nicht. jpackage-macOS nur 1 Arch, Native-macOS Matrix.

### P2.1 Skripte angleichen
1. Beide Skripte: Eingabe `linux | win | mac | mac-aarch64`, `mac`=x86_64, `mac-aarch64`=arm64. Guard in native-Skript erweitern. Config-Dir fuer mac-aarch64 auf `packaging/native-image/mac` zeigen (dokumentieren).
2. Nach `dependency:copy-dependencies` validieren:
```bash
case "$JAVAFX_PLATFORM" in
  linux) need=javafx-graphics-25.0.4-linux.jar ;;
  win) need=javafx-graphics-25.0.4-win.jar ;;
  mac) need=javafx-graphics-25.0.4-mac.jar ;;
  mac-aarch64) need=javafx-graphics-25.0.4-mac-aarch64.jar ;;
esac
[[ -f "$INPUT_DIR/$need" ]] || { echo "Falscher Classifier: $need fehlt" >&2; ls "$INPUT_DIR" >&2; exit 1; }
```
3. Optional `-Djavafx.platform`-Mapping an Maven uebergeben.

### P2.2 macOS jpackage Matrix
- Job `macos` auf Matrix `[x86_64, arm64]` wie Native-Job. Je Eintrag `JAVAFX_PLATFORM=mac` bzw. `mac-aarch64`. Runner-Thema loesen: `macos-15-intel` fuer x86_64 oder dokumentierter Rosetta-Cross-Build.

**Done wenn:** Falscher Classifier bricht ab; beide mac-Archs als Artefakte da.

---
## P3 – Black-Screen Fix Linux (1 Tag, P0-kritisch)

**Problem:** `prism.order=sw` nur in Surefire, nicht im Image. Keine Deps im Linux-Job. `EditorWindow:131-138` still schwarz bei Screen/CSS/Bundle-Fehler.

### P3.1 Prism-Fallback ins Image
1. `build-app-image.sh` fuer linux ergaenzen: `--java-options "-Dprism.order=sw" --java-options "-Dprism.text=t2k"`. Alternativ in `RichMarkDownEditorApp.main()` vor `launch()`: Default `es2,sw` setzen wenn unset.
2. Beide Varianten im Smoke-Test pruefen (mit/ohne GPU).

### P3.2 System-Deps
1. Job `linux`: installieren wie Native-Job: `libgtk-3-0t64 libxtst6 libasound2t64 libfontconfig1 libgl1`.
2. Nach jpackage: `ldd .../libglassgtk3.so | grep "not found"` -> Fail.
3. README: `sudo apt install libgtk-3-0 libgl1 libfontconfig1` + Fedora/Arch-Aequivalente.

### P3.3 EditorWindow-Haertung
1. `Screen.getPrimary()` null-sicher (Fallback 1000x700). `getResource(app.css)` null-Check mit Warnung. `warmUpPrintSystem` in try/catch.
2. `I18n`: MissingResource mit lesbarer Meldung (Bundle+Locale).

**Done wenn:** Portable startet unter xvfb ohne GPU, Screenshot zeigt Menue+Editor, kein QuantumRenderer-Error.

---


## P4 – Native-Image reparieren (2-3 Tage, P1-hoch)

**Problem:** Win/Mac-Stubs je 53 Bytes, Linux 98k+97k. Win/Mac-Trace nur 3s Startup, Linux 40s GUI-Flow. Flags falsch, Typo `viewModel`, GraalVM driftet (25.0 vs 25.0.2).

### P4.1 Echte Win/Mac-Metadaten (einmalig, dann committen)
1. Auf Win11-VM + Mac (arm64+x86_64): Trace mit GUI-Flow (P4.2), merge nach `packaging/native-image/{win,mac}/reachability-metadata.json` committen. Ziel >50kB, enthaelt FileChooser, PrinterJob, RichTextAreaSkin, QuantumMessagesBundle, i18n.messages.
2. CI danach wie Linux: Trace best-effort + Diff loggen, verifizierte Datei **nie** im Runner ueberschreiben (Zeilen 432-440, 524-532 umstellen).

### P4.2 Trace auf GUI-Niveau
- win/mac-Zweig statt nur `exit-after-start`: Menues, Open/Save, Print-Dialog, Tippen (PowerShell SendKeys / AppleScript cliclick). Sonst keine Dialog-/Print-/Skin-Reflection.

### P4.3 Flags korrigieren
1. Typo: `--add-exports=...viewmodel` klein (wie pom.xml:104), nicht `viewModel`.
2. Erweitern: `--enable-url-protocols=http,https` (wegen https-Links) und `--enable-native-access=javafx.graphics,java.desktop` (wegen AWT.Desktop + PrinterJob).
3. Metadata-Check: muss i18n.messages + app.css + QuantumMessagesBundle enthalten. GraalVM-25 `bundles`-Top-Level pruefen.
4. Gluon-Substrate evaluieren oder Native-Desktop als experimentell markieren (plain native-image fuer JavaFX offiziell unsupportet).

### P4.4 GraalVM pinnen
- Ueberall `25.0.2`, `native-image --version` ins Log.

**Done wenn:** Win/Mac-Metadaten verifiziert, CI ueberschreibt nicht, Natives bestehen Smoke-Tests.

---

## P5 – Packaging-Luecken (1 Tag, P1-hoch)

1. MSI: Lizenzen auch fuers MSI mitgeben, ZIP-Struktur im Smoke-Test validieren.
2. macOS Gatekeeper: adhoc `codesign --deep --sign -`, README „Rechtsklick -> Oeffnen“, Notarisierung als Follow-up.
3. Flatpak: Smoke-Test in Sandbox, `finish-args` um `--socket=x11` ergaenzen (JavaFX/GTK3 braucht X11), Runtime 25.08 pruefen.
4. Publish: `sha256sums.txt` erzeugen + hochladen, Release-Notes mit OS/Archs.

**Done wenn:** MSI installiert+startet, mac-ZIP adhoc-signiert+Doku, Flatpak startet, Checksums im Release.

---

## P6 – CI-Haertung (0,5-1 Tag, P2-mittel)

1. Tests an: `-DskipTests` fuer jpackage-Jobs entfernen (mind. `mvn -pl editor -am test` pro OS).
2. Versionen pinnen: Actions auf SHAs/Minor pinnen, Dependabot an.
3. Trace-Warnung `--enable-native-access` auch im Trace-Lauf uebergeben.
4. Artefakt-Namen stabil, `if-no-files-found: error` (Screenshots `warn`).

**Done wenn:** `mvn test` gruen auf allen OS, Versionen gepinnt, Checksums da.

---

## Anhang A – Befund-Template (P0)

```text
Datum:
JAVAFX_PLATFORM / APP_VERSION:
unzip -l portable (Top-Level):
ldd libglassgtk3.so (not found?):
./bin/Mardexas stderr (50 Zeilen):
./dist/native/linux/Mardexas stderr (50 Zeilen):
Screenshot jpackage:
Screenshot native:
```

## Anhang B – Risiken

| # | Risiko | Jobs | Schwere | Phase |
|---|---|---|---|---|
| 1 | Kein Smoke-Test, gruen trotz Crash | linux/win/mac jpackage | kritisch | P1 |
| 2 | `timeout ... \|\| true` schluckt Crash | win/mac native | kritisch | P1 |
| 3 | Falscher JavaFX-Classifier | alle | hoch | P2 |
| 4 | mac x86_64 fehlt | macos jpackage | hoch | P2 |
| 5 | Kein Prism-Fallback -> schwarz | linux | kritisch | P3 |
| 6 | GTK/GL-Libs fehlen | linux | hoch | P3 |
| 7 | Win/Mac-Metadaten leer | win/mac native | kritisch | P4 |
| 8 | CI ueberschreibt Metadaten | win/mac native | hoch | P4 |
| 9 | viewModel-Typo, http-only | alle native | hoch | P4 |
| 10 | MSI/Flatpak/Gatekeeper ungetestet | win/mac/flatpak | mittel-hoch | P5 |
| 11 | -DskipTests ueberall | alle | mittel | P6 |
| 12 | GraalVM driftet | native | mittel | P4/P6 |
```


