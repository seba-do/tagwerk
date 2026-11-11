# Tagwerk (Prototyp)

Kostenloser Tagesplaner für Android. Kein Konto, keine Werbung, kein Tracking. Die KI-Funktionen laufen mit deinem eigenen Claude-API-Key direkt vom Gerät zu Anthropic.

## Funktionen

- **Plan:** Wochenleiste, Tagesabschnitte, feste Uhrzeit oder grobe Tageszeit; Karten lassen sich per Long-Press in jeden Abschnitt ziehen, auch in leere, oder auf „Erledigt“, die Liste scrollt dabei mit; Tippen öffnet ein Menü zum Kopieren, Verschieben, Neuplanen, Zerlegen, Starten, Bearbeiten und Löschen; Herunterziehen lädt die Kalendertermine neu
- **Kalender:** Termine aus den Kalendern des Geräts erscheinen im Plan, nur lesend und je Kalender abschaltbar; sie werden nicht gespeichert und nicht an die KI geschickt
- **Listen:** mehrere Listen, gruppieren nach Priorität, Aufgabengröße oder Eisenhower, sortieren von Hand, alphabetisch oder nach Dauer, Erledigte und Vorschläge ausblendbar; Ziehen in eine Gruppe übernimmt deren Priorität oder Eisenhower-Feld; Tippen öffnet das Menü, unter anderem zum Einplanen
- **Fokus:** Timer aus Zeitstempeln, Pause, `+5`, eigener Timer pro Schritt, Schnellstart ohne Aufgabe, Alarm am Ende und bei jedem Schrittwechsel, auch bei geschlossener App und nach einem Neustart des Geräts
- **Erinnerungen:** Aufgaben mit fester Uhrzeit melden sich 10 Minuten vorher und zum Start, auch bei geschlossener App und nach einem Neustart des Geräts; in der Meldung lassen sie sich öffnen, als Fokus starten oder abhaken; beide Zeitpunkte sind in den Einstellungen abschaltbar
- **Widgets:** zwei für den Homescreen, klein und breit, mit der nächsten Aufgabe von heute oder dem laufenden Timer samt Ring, Countdown und den Knöpfen +5, Pause und Erledigt; im breiten lassen sich die Schritte abhaken
- **Assistent:** Chat mit Vorschlägen, die du erst bestätigst, dazu Spracheingabe über das System
- **Formular:** Schnell-Anlegen mit Chips, über die drei Punkte das ganze Formular mit Datum, Dauer und Notizen; Emoji und freie Farbe für Aufgaben und Schritte
- **Schritte:** Emoji folgt live dem Titel, per Griff umsortieren, Timer pro Schritt mit 10 Minuten als Vorgabe; ihre Summe wird zur Dauer
- **Wiederholungen:** täglich, werktags, am Wochenende, wöchentlich, alle zwei Wochen, monatlich, jährlich oder eigener Rhythmus, optional mit letztem Tag; Änderungen und Löschen wahlweise für einen Termin, ab diesem Termin oder für alle
- **Zerlegen und Ideen:** KI-Vorschläge für Schritte und Listeneinträge
- **Bewegung:** Feuerwerk, eigene Töne und Haptik beim Abhaken, Fokus-Ring zum Drehen, Tag und Woche per Wischen, Karten per Long-Press verschieben, in Listen nach links wischen zum Löschen mit Rückgängig; in den Einstellungen abschaltbar, bei im System abgeschalteten Animationen entfällt das Feuerwerk
- **Einstellungen:** API-Key im Android Keystore verschlüsselt, Modellwahl, Schlüsseltest

## Bauen und testen

```bash
./gradlew testDebugUnitTest assembleDebug
```

Nur auf ein bestimmtes Gerät installieren, weil `installDebug` alle verbundenen Geräte trifft:

```bash
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
```

## Noch nicht enthalten

Countdown in der Benachrichtigung, eigene Anmeldung bei Google oder iCloud (der Kalender kommt über Android), Sync und Backup. Chats werden nicht gespeichert. Der Speicher ist für den Prototyp eine JSON-Datei; das App-Symbol ist ein Platzhalter.

## Abgrenzung

Die Oberfläche ist bewusst eng an Tiimo angelehnt: Aufbau, Maße und Farben wurden an Screenshots gemessen und nachgebaut. Das ist für den privaten Gebrauch gedacht. Vor einer Veröffentlichung sollte die Gestaltung eigenständiger werden und rechtlich geprüft sein.

Dateien wurden nicht übernommen: Code, Texte, Töne und Grafiken sind eigen, das Maskottchen und Tiimos Schrift Recoleta sind nicht enthalten. Für Überschriften dient Lora unter der SIL Open Font License (`app/src/main/assets/licenses/Lora-OFL.txt`). Das dunkle Farbschema ist gemessen, das helle ist eine eigene Ableitung. Der Name ist ein Arbeitstitel und nicht markenrechtlich geprüft.
