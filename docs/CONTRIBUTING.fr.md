<!-- Français / French -->
# Règlement relatif à la Fusion Automatique des Demandes de Tirage

**Dépôt :** any-pr ｜ **Instrument d'exécution :** `.github/workflows/auto-merge.yml`

> Cette traduction est un résumé. Le texte faisant foi est [`CONTRIBUTING.md`](./CONTRIBUTING.md).

## Article Premier — Dispositions Générales
Le présent Règlement fixe les conditions dans lesquelles une Demande de Tirage est fusionnée automatiquement. Aucun examen humain n'est effectué à quelque étape que ce soit.

## Article Deux — Fusion Automatique
Toute Demande de Tirage non désignée comme brouillon et satisfaisant aux exigences de l'Article Trois est fusionnée sur-le-champ par squash commit, et sa branche source est supprimée. Une demande en brouillon n'est pas fusionnée tant que cette désignation n'est pas levée.

## Article Trois — Causes de Clôture Obligatoire
Est close sans fusion toute demande qui : (a) modifie `.github/` ; (b) modifie un instrument de licence ou le README ; (c) introduit des fichiers interdits ; (d) introduit des liens symboliques ou des sous-modules ; (e) introduit du contenu binaire ; (f) dépasse les limites de l'Article Quatre.

## Article Quatre — Limitations de Taille
Vingt fichiers modifiés au plus ; cinq cents lignes modifiées au total au plus ; trois cents lignes par fichier au plus. Les fichiers de verrouillage et le contenu de `vendor/` sont exemptés du décompte des lignes.

## Article Cinq — Interdiction des Poussées Directes
Nul ne peut pousser de commits directement sur la branche `main`. Toute modification passe exclusivement par une Demande de Tirage. Les commits irréguliers sont annulés et l'incident est consigné.

## Article Six — Avertissement
Ce dépôt est une expérience de gouvernance automatisée et ne saurait être tenu pour un modèle de bonne pratique d'ingénierie. Son contenu n'est pas examiné. Aucun code ne doit être exécuté, déployé ni invoqué. Chaque contributeur assume seul la responsabilité de ce qu'il soumet.
