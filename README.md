# Shelfi

Android-App zum Finden von Produkten und Planen einer Einkaufsroute.

## APK herunterladen und installieren

1. Öffne die [GitHub Actions Builds](https://github.com/SamiAk02/shelfi/actions/workflows/android.yml).
2. Wähle den neuesten erfolgreichen Lauf **Build Android app** aus.
3. Lade unter **Artifacts** die Datei **shelfi-debug-apk** herunter und entpacke sie.
4. Öffne `app-debug.apk` auf deinem Android-Gerät und tippe auf **Installieren**. Falls nötig, erlaube deinem Browser oder der Dateien-App zuerst die Installation unbekannter Apps.

Alternativ findest du die APK dauerhaft unter [Releases](https://github.com/SamiAk02/shelfi/releases), sobald ein Release-Tag veröffentlicht wurde.

## APK über GitHub bauen

Der Workflow unter `.github/workflows/android.yml` baut die App automatisch:

- Bei einem Push oder Pull Request wird ein APK-Artefakt erzeugt.
- Bei einem Tag wie `v1.0.0` wird zusätzlich ein GitHub Release mit der APK erstellt.

### Release veröffentlichen

Nach dem Hochladen des Projekts auf GitHub:

```text
git add .
git commit -m "Initial Shelfi release"
git push origin main
git tag v1.0.0
git push origin v1.0.0
```

Die APK ist danach im GitHub-Release unter **Assets** verfügbar. Diese erste Version wird als Debug-APK gebaut.
