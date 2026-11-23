# Deploying Atchou

Docker + Caddy (automatic HTTPS) on a Debian/Ubuntu VPS, provisioned with Ansible.
Daily off-site backups (restic to Backblaze B2).

## Prerequisites
1. A Debian/Ubuntu VPS with an SSH user that has sudo.
2. A DNS `A` record: `atchou.lasarriette.be` → the VPS IP (before the first run, otherwise certificate issuance fails).
3. A GitHub token (classic) with `read:packages`, so the VPS can pull the private image.
4. A private Backblaze B2 bucket and an application key (the S3 endpoint is shown on the bucket page).

## Configuration (git-ignored files)
```sh
cd deploy/ansible
cp inventory.example.yml inventory.yml   # VPS IP / user
cp vars.example.yml vars.yml             # domain, tokens, B2, restic password
cp ../../server/config.example.toml config.toml   # children, allowed emails, google_client_id
ansible-galaxy collection install -r requirements.yml
```
Keep the restic password somewhere safe: without it the backups are unrecoverable.

## Build and push the image
```sh
echo $GHCR_TOKEN | docker login ghcr.io -u victorjoos --password-stdin   # token with write:packages
deploy/build.sh        # tags: short git SHA + latest (PLATFORM=linux/amd64 by default)
```

## Deploy
```sh
cd deploy/ansible
ansible-playbook site.yml -e @vars.yml                              # everything (base + app + backup)
ansible-playbook site.yml -e @vars.yml -e atchou_image_tag=<sha>    # a specific version
ansible-playbook site.yml -e @vars.yml --tags app                   # a single role
```
The playbook is idempotent: re-run it to update the image or the config.

## After the first deployment
- Register a frame (the token is shown only once):
  `ssh vps 'cd /opt/atchou && sudo docker compose exec atchou python -m atchou_server.cli add-device living-room'`
- In the app, set the server URL to `https://atchou.lasarriette.be`.
- The Google Android client must include the SHA-1 of the release signing key.

## Backups and restore
Systemd timer `atchou-backup.timer` (03:30): consistent SQLite snapshot + `posts/`, retention 14 daily / 8 weekly / 12 monthly.
```sh
sudo systemctl start atchou-backup.service      # run manually
sudo bash -c 'set -a; . /etc/atchou-backup.env; restic snapshots'
# Restore: stop the stack, then
#   restic restore latest --target /tmp/restore ; copy into /opt/atchou/data ; restart
```
