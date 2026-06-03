#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOCAL_PROPERTIES="$ROOT_DIR/local.properties"
KEYSTORE_DIR="$ROOT_DIR/keystores"
KEYSTORE_FILE="$KEYSTORE_DIR/imax-release.jks"
KEY_ALIAS="${IMAX_RELEASE_KEY_ALIAS:-imax-player-release}"

mkdir -p "$KEYSTORE_DIR"
touch "$LOCAL_PROPERTIES"

upsert_property() {
  local key="$1"
  local value="$2"
  local tmp
  tmp="$(mktemp)"

  if grep -q "^${key}=" "$LOCAL_PROPERTIES"; then
    awk -F= -v key="$key" -v value="$value" '
      $1 == key { print key "=" value; next }
      { print }
    ' "$LOCAL_PROPERTIES" > "$tmp"
  else
    cp "$LOCAL_PROPERTIES" "$tmp"
    printf '%s=%s\n' "$key" "$value" >> "$tmp"
  fi

  mv "$tmp" "$LOCAL_PROPERTIES"
}

if [[ -f "$KEYSTORE_FILE" ]]; then
  echo "Release keystore already exists: $KEYSTORE_FILE"
else
  STORE_PASSWORD="$(openssl rand -base64 32 | tr -d '\n')"
  KEY_PASSWORD="$(openssl rand -base64 32 | tr -d '\n')"

  keytool -genkeypair \
    -v \
    -storetype JKS \
    -keystore "$KEYSTORE_FILE" \
    -storepass "$STORE_PASSWORD" \
    -keypass "$KEY_PASSWORD" \
    -alias "$KEY_ALIAS" \
    -keyalg RSA \
    -keysize 4096 \
    -validity 10000 \
    -dname "CN=iMAX Player, OU=iMAX Player, O=iMAX Player, L=Istanbul, ST=Istanbul, C=TR"

  upsert_property "IMAX_RELEASE_STORE_PASSWORD" "$STORE_PASSWORD"
  upsert_property "IMAX_RELEASE_KEY_PASSWORD" "$KEY_PASSWORD"
fi

upsert_property "IMAX_RELEASE_STORE_FILE" "keystores/imax-release.jks"
upsert_property "IMAX_RELEASE_KEY_ALIAS" "$KEY_ALIAS"
upsert_property "UPDATE_MANIFEST_URL" "https://github.com/meliherenn/iMAX-Player/releases/latest/download/latest.json"

echo "Release signing is configured in local.properties."
echo "Back up keystores/imax-release.jks securely. Future updates must use this same keystore."
