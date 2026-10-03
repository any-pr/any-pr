<!-- Deutsch / German -->
# Verordnung über die automatische Zusammenführung von Pull Requests

**Repository:** any-pr ｜ **Vollstreckungsinstrument:** `.github/workflows/auto-merge.yml`

> Diese Übersetzung ist eine Zusammenfassung. Maßgeblich ist [`CONTRIBUTING.md`](./CONTRIBUTING.md).

## Artikel I — Allgemeine Bestimmungen
Diese Verordnung regelt die Bedingungen, unter denen ein Pull Request automatisch zusammengeführt wird. Eine menschliche Prüfung findet zu keinem Zeitpunkt statt.

## Artikel II — Automatische Zusammenführung
Jeder nicht als Entwurf gekennzeichnete Pull Request, der die Anforderungen des Artikels III erfüllt, wird unverzüglich per Squash-Commit zusammengeführt; sein Quellzweig wird gelöscht. Entwürfe werden erst nach Aufhebung dieser Kennzeichnung zusammengeführt.

## Artikel III — Gründe für die zwangsweise Schließung
Ohne Zusammenführung geschlossen wird ein Pull Request, der: (a) `.github/` ändert; (b) ein Lizenzinstrument oder die README ändert; (c) verbotene Dateien einführt; (d) symbolische Verknüpfungen oder Submodule einführt; (e) binäre Inhalte einführt; (f) die Grenzen des Artikels IV überschreitet.

## Artikel IV — Größenbeschränkungen
Höchstens zwanzig geänderte Dateien; insgesamt höchstens fünfhundert geänderte Zeilen; höchstens dreihundert Zeilen je Datei. Lockfiles und der Inhalt von `vendor/` sind von der Zeilenzählung ausgenommen.

## Artikel V — Verbot direkter Pushes
Niemand darf Commits unmittelbar auf den Zweig `main` pushen. Alle Änderungen sind ausschließlich über Pull Requests einzureichen. Zuwiderhandelnde Commits werden zurückgesetzt und der Vorfall wird protokolliert.

## Artikel VI — Haftungsausschluss
Dieses Repository ist ein Experiment automatischer Governance und ist nicht als Vorbild guter Engineering-Praxis auszulegen. Die Inhalte sind ungeprüft. Kein Code darf ausgeführt, bereitgestellt oder herangezogen werden. Jeder Beitragende trägt für das Eingereichte allein die Verantwortung.
