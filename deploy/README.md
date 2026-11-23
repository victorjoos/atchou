# Déploiement d'Atchou

Docker + Caddy (HTTPS automatique) sur un VPS Debian/Ubuntu, provisionné avec Ansible.
Sauvegardes quotidiennes hors site (restic vers Backblaze B2).

## Avant de commencer
1. VPS Debian/Ubuntu avec un utilisateur SSH ayant sudo.
2. Enregistrement DNS `A` : `atchou.lasarriette.be` → IP du VPS (avant le premier run, sinon le certificat échoue).
3. Token GitHub (classic) avec `read:packages`, pour que le VPS tire l'image privée.
4. Bucket Backblaze B2 privé + clé applicative (endpoint S3 affiché sur la page du bucket).

## Configuration (fichiers ignorés par git)
```sh
cd deploy/ansible
cp inventory.example.yml inventory.yml   # IP/utilisateur du VPS
cp vars.example.yml vars.yml             # domaine, tokens, B2, mot de passe restic
cp ../../server/config.example.toml config.toml   # enfants, emails autorisés, google_client_id
ansible-galaxy collection install -r requirements.yml
```
Gardez le mot de passe restic en lieu sûr : sans lui, les sauvegardes sont perdues.

## Construire et publier l'image
```sh
echo $GHCR_TOKEN | docker login ghcr.io -u victorjoos --password-stdin   # token avec write:packages
deploy/build.sh        # tags : SHA git court + latest (PLATFORM=linux/amd64 par défaut)
```

## Déployer
```sh
cd deploy/ansible
ansible-playbook site.yml -e @vars.yml                       # tout (base + app + backup)
ansible-playbook site.yml -e @vars.yml -e atchou_image_tag=<sha>   # version précise
ansible-playbook site.yml -e @vars.yml --tags app            # un seul rôle
```
Le playbook est idempotent : relancez-le pour mettre à jour l'image ou la config.

## Après le premier déploiement
- Enregistrer un cadre (le token n'est affiché qu'une fois) :
  `ssh vps 'cd /opt/atchou && sudo docker compose exec atchou python -m atchou_server.cli add-device salon'`
- Dans l'app, définir l'URL du serveur : `https://atchou.lasarriette.be`.
- Le client Google Android doit contenir le SHA-1 de la clé de release.

## Sauvegardes et restauration
Timer systemd `atchou-backup.timer` (03:30) : snapshot SQLite cohérent + `posts/`, rétention 14 j / 8 sem. / 12 mois.
```sh
sudo systemctl start atchou-backup.service      # lancer à la main
sudo bash -c 'set -a; . /etc/atchou-backup.env; restic snapshots'
# Restauration : arrêter la stack, puis
#   restic restore latest --target /tmp/restore ; copier vers /opt/atchou/data ; redémarrer
```
