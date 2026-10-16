# Vidéo de lancement Ambawbio Suite

Vidéo de présentation de 3 min 22 s en 1920 × 1080, à 30 images par seconde. Elle présente la plateforme complète, avec les couleurs et les polices de `docs/design`.

| Fichier | Rôle |
|---|---|
| `scenes.js` | Texte de la voix off par scène. C'est la source unique du minutage : la durée de chaque scène découle de la longueur de son texte. |
| `video.html` | Les 15 scènes animées. La fonction `rendre(t)` calcule l'image affichée à l'instant `t`. |
| `rendre.mjs` | Produit la vidéo MP4, `voix-off.md`, `voix-off.txt` et `sous-titres.srt`. |

```bash
# Chromium : celui de Playwright (frontend/node_modules) ou un autre, indiqué par la variable CHROMIUM
node outils/video-lancement/rendre.mjs sortie/ --apercu   # une image PNG par scène, pour contrôler la mise en page
node outils/video-lancement/rendre.mjs sortie/            # vidéo complète (ffmpeg requis)
```

Pour modifier le texte, éditez `scenes.js` puis relancez le rendu. La vidéo, le script et les sous-titres restent synchronisés.

Partenaires officiels (administration fiscale, opérateurs Mobile Money, banques) : ils sont mentionnés de façon générique, sans nom ni logo, et sans mention de certification ou de partenariat.
