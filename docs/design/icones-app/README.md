# Icônes d'application

| Fichier | Usage | Export PNG à produire |
|---|---|---|
| favicon.svg | `<link rel="icon" type="image/svg+xml">` | favicon-16.png, favicon-32.png, favicon-48.png, favicon.ico (16+32+48) |
| pwa-maskable.svg | manifest.webmanifest, `purpose: "maskable any"` | icon-192.png, icon-512.png |
| apple-touch-icon.svg | `<link rel="apple-touch-icon">` | apple-touch-icon-180.png |
| android/* | Icône adaptative 108 dp (Capacitor : `android/app/src/main/res`) | mipmap mdpi→xxxhdpi via Android Studio Image Asset |

Zone sûre Android : symbole contenu dans le cercle central de 66 dp. Fond : latérite `#B4532A`. Monochrome : Android 13+.
Génération PNG suggérée : `npx @capacitor/assets generate` ou `sharp` à partir des SVG.
