# Deploying Atchou

Docker + Caddy (automatic HTTPS) on a Debian/Ubuntu VPS, provisioned with Ansible.
Daily off-site backups (restic to Backblaze B2).

## Prerequisites
1. A Debian/Ubuntu VPS with root SSH access. If it has no sudo user yet, run the bootstrap step below.
2. A DNS `A` record: `atchou.lasarriette.be` → the VPS IP (before the first run, otherwise certificate issuance fails).
3. The `ghcr.io/victorjoos/atchou-server` package set to public (the VPS pulls it without logging in).
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

## First-time setup (fresh VPS, root only)
Creates `admin_user` (from `vars.yml`) with sudo, reusing root's `authorized_keys`:
```sh
cd deploy/ansible
ansible-playbook bootstrap.yml -e @vars.yml -e ansible_user=root
ssh victor@<vps-ip> sudo whoami   # prints "root"
```
Then set `ansible_user` in `inventory.yml` to that user and use the deploy step below.

## Build and push the image
GitHub Actions (`.github/workflows/server-image.yml`) runs the server tests, then builds and pushes
`ghcr.io/victorjoos/atchou-server` on every push to `main` that touches `server/` (tags: short git SHA + `latest`).
It can also be started manually from the Actions tab. The tag to deploy is shown in the package page.

Manual fallback from a machine with Docker:
```sh
echo $GHCR_TOKEN | docker login ghcr.io -u victorjoos --password-stdin   # classic token with write:packages
deploy/build.sh
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
- To keep a test token, save it in `deploy/ansible/test-device.token` (git-ignored) and check the frame path with:
  `cd server && uv run python scripts/frame_sim.py --token "$(cat ../deploy/ansible/test-device.token)" --url https://atchou.lasarriette.be --once`
  (`HTTP 204` = no post to show yet, `HTTP 200` = image received). Use a separate token for the real frame.
  New token: `ssh <user>@<vps> 'cd /opt/atchou && sudo docker compose exec atchou python -m atchou_server.cli add-device <name>'` (shown only once).
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
