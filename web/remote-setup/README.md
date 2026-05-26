# iMAX Player Remote Setup

Static companion site for TV playlist entry. It supports:

- Xtream / Portal credentials
- M3U / M3U8 URL
- Local M3U file payload creation
- Optional EPG URL and startup/EPG preferences
- Pairing-code based submission contract

Run locally:

```bash
cd web/remote-setup
python3 -m http.server 4173
```

Open:

```text
http://127.0.0.1:4173/?code=A7K9Q2
```

When an API base is provided with `?api=https://setup.example.com`, the site posts:

```text
POST /api/pairings/{pairingCode}/playlist
```

Without `api`, it keeps the generated setup payload locally and allows copying it for integration testing.
