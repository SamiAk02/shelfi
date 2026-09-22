# Shelfi

Android-App zum Finden von Produkten und Planen einer Einkaufsroute.

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

Die APK ist danach im GitHub-Release unter **Assets** verfügbar. Diese erste Version wird als Debug-APK gebaut und kann auf Android-Geräten installiert werden.
