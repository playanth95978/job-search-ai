# Portfolio — déploiement

Site portfolio statique (un seul fichier HTML autonome, `index.html`), servi sur le
sous-domaine **`cv.<DOMAIN>`** via le Traefik + Let's Encrypt déjà en place pour l'app.

- **Aucune base, aucun build, aucune clé** : juste un fichier HTML + un conteneur `nginx:alpine`.
- **Indépendant de l'application** : le redéployer / le casser n'a aucun impact sur `jsa-app`.
- **HTTPS automatique** : Traefik génère le certificat au premier accès.

---

## Contenu du dossier

| Fichier        | Rôle                                                                    |
| -------------- | ----------------------------------------------------------------------- |
| `index.html`   | Le site complet (HTML + CSS + JS inline). Tout le contenu est piloté par l'objet `CV` en haut du `<script>`. |
| `README.md`    | Ce guide.                                                               |

Le service Docker `portfolio` est déjà déclaré dans [`../docker-compose.prod.yml`](../docker-compose.prod.yml).

---

## Prérequis (une seule fois)

1. **DNS** — crée un enregistrement **A** `cv.<DOMAIN>` pointant vers **l'IP de ton VPS**
   (la même IP que `<DOMAIN>`). Exemple si `DOMAIN=jobsearch.example.com` → `cv.jobsearch.example.com`.

2. Vérifie que la variable `DOMAIN` est bien définie dans `deploy/.env` (elle l'est déjà,
   c'est celle utilisée par l'app).

> Le certificat TLS est provisionné automatiquement par Traefik au premier chargement de la page
> (compter ~10–30 s la toute première fois).

---

## Déploiement

Sur le VPS, depuis le dossier du projet :

```bash
# 1. Récupérer les fichiers (index.html + compose mis à jour)
git pull                     # ou : scp -r deploy/portfolio user@vps:/chemin/deploy/

# 2. Démarrer uniquement le service portfolio (n'affecte pas l'app)
docker compose -f deploy/docker-compose.prod.yml up -d portfolio
```

→ En ligne sur **https://cv.<DOMAIN>**

---

## Mettre à jour le contenu

Tout le texte du site vit dans l'objet `CV` en haut du `<script>` de `index.html` :

```js
var CV = {
  name: "Anthony Attia",
  experiences: [
    { year: "2022 — Present", title: "…", org: "…", bullets: ["…"], tags: ["…"] },
    // ajoute / retire / réordonne librement
  ],
  skills:   [ { label: "AI / GenAI", items: ["…", "…"] }, … ],
  projects: [ { kicker: "…", title: "…", desc: "…", link: { label:"…", href:"…" } }, … ],
  // education, languages, contacts, availability …
};
```

Après édition :

```bash
# renvoyer le fichier sur le VPS (git pull ou scp), puis :
docker restart jsa-portfolio
```

Pas de rebuild : le fichier est monté en lecture seule dans le conteneur (`./portfolio:/usr/share/nginx/html:ro`).

### À personnaliser avant publication
- **LinkedIn / GitHub** : dans `CV.contacts`, remplace les `href` `https://www.linkedin.com/` et
  `https://github.com/` (marqués `TODO`) par tes vraies URLs de profil.
- **Téléphone** : non affiché (il était masqué dans le CV). Pour l'ajouter, mets une entrée dans
  `CV.contacts` ou `CV.availability`.

---

## Vérifier / diagnostiquer

```bash
docker ps --filter name=jsa-portfolio            # doit être "Up"
docker logs jsa-portfolio                         # accès nginx
docker logs <conteneur-traefik> | grep -i cv.     # provisioning du certificat
curl -I https://cv.<DOMAIN>                        # doit répondre 200
```

Problèmes courants :

| Symptôme                              | Cause probable / correctif                                              |
| ------------------------------------- | ---------------------------------------------------------------------- |
| `404` de Traefik                      | DNS `cv.<DOMAIN>` pas encore propagé, ou service pas démarré.           |
| Certificat invalide / `NET::ERR`      | Cert pas encore émis — recharge après ~30 s ; vérifie que le port 80 est ouvert (challenge ACME). |
| Accents cassés (`Ã©`)                 | Fichier remplacé par une version sans `<meta charset="utf-8">` — garde `index.html` complet. |
| Page blanche                          | Ouvre la console navigateur ; vérifie que l'objet `CV` est bien formé (virgules JSON). |

---

## Alternative sans Docker (nginx sur l'hôte)

Si tu préfères servir le fichier via un nginx installé directement sur le VPS :

```bash
sudo mkdir -p /var/www/cv
sudo cp deploy/portfolio/index.html /var/www/cv/
```

Bloc serveur `/etc/nginx/sites-available/cv` :

```nginx
server {
    server_name cv.<DOMAIN>;
    root /var/www/cv;
    index index.html;
}
```

```bash
sudo ln -s /etc/nginx/sites-available/cv /etc/nginx/sites-enabled/
sudo certbot --nginx -d cv.<DOMAIN>      # HTTPS
sudo nginx -t && sudo systemctl reload nginx
```

---

## Alternative sans VPS (le plus rapide)

Glisse-dépose le seul `index.html` sur **Netlify Drop** (app.netlify.com/drop),
**Cloudflare Pages** ou **GitHub Pages** → en ligne en HTTPS en ~30 s, sans toucher au serveur.
