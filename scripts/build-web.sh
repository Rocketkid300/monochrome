#!/usr/bin/env bash
# Build the upstream Monochrome web UI (PWA) into upstream/dist.
# Serve dist/ over HTTPS (Tailscale serve, Cloudflare/GitHub Pages, nginx...),
# open it in iOS Safari, then Share -> Add to Home Screen.
set -euo pipefail
cd "$(dirname "$0")/../upstream"
[ -d node_modules ] || npm install --no-audit --no-fund
npx vite build
echo "Built: $(pwd)/dist"
echo "Quick test:  (cd upstream && npx vite preview --host)   # http only; iOS needs HTTPS to install"
echo "Tailscale:   tailscale serve --bg --https=443 http://localhost:4173   # after vite preview"
