# Icônes d'application

| Fichier | Usage | Export PNG à produire |
|---|---|---|
| favicon.svg | `<link rel="icon" type="image/svg+xml">` | favicon-16.png, favicon-32.png, favicon-48.png, favicon.ico (16+32+48) |
| pwa-maskable.svg | manifest.webmanifest, `purpose: "maskable any"` | icon-192.png, icon-512.png |
| apple-touch-icon.svg | `<link rel="apple-touch-icon">` | apple-touch-icon-180.png |
| android/* | Icône adaptative 108 dp (Capacitor : `android/app/src/main/res`) | mipmap mdpi→xxxhdpi via Android Studio Image Asset |

Zone sûre Android : symbole contenu dans le cercle central de 66 dp. Fond : latérite `#B4532A`. Monochrome : Android 13+.
## PNG générés

Les PNG sont produits par `infra/scripts/generer-icones.mjs` (sharp) et rangés dans `png/` :

```
png/web/              favicon-16.png, favicon-32.png, favicon-48.png, favicon.ico (16+32+48),
                      icon-192.png, icon-512.png, apple-touch-icon-180.png
png/android/res/      mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/
                        ic_launcher.png, ic_launcher_round.png         (48 dp, Android < 8)
                        ic_launcher_foreground.png, ic_launcher_background.png,
                        ic_launcher_monochrome.png                     (calques 108 dp)
                      mipmap-anydpi-v26/ic_launcher.xml, ic_launcher_round.xml
                      values/ic_launcher_background.xml                (#B4532A)
```

Régénération après modification d'un SVG : `cd infra/scripts && npm ci && npm run icones`. Pour écrire directement dans un autre dossier : `node generer-icones.mjs --sortie <dossier>`.
Au LOT 3, copier `png/web/` dans `frontend/public/` et `png/android/res/` dans `frontend/android/app/src/main/res/`. Le XML de `png/android/` remplace alors `android/ic_launcher.xml` (qui suppose des VectorDrawable).
