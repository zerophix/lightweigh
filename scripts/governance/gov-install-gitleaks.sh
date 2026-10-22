#!/usr/bin/env bash
# scripts/governance/gov-install-gitleaks.sh
# 下载并校验 gitleaks 到 scripts/tools/gitleaks（DEC-049）
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
VERSION="8.30.1"
TOOLS="$ROOT/scripts/tools"
DEST="$TOOLS/gitleaks"
SUMS="$TOOLS/gitleaks.sha256"
BASE_URL="https://github.com/gitleaks/gitleaks/releases/download/v${VERSION}"

OS="$(uname -s | tr '[:upper:]' '[:lower:]')"
ARCH="$(uname -m)"
case "$OS-$ARCH" in
  darwin-arm64|darwin-aarch64) ARTIFACT="gitleaks_${VERSION}_darwin_arm64.tar.gz" ;;
  darwin-x86_64) ARTIFACT="gitleaks_${VERSION}_darwin_x64.tar.gz" ;;
  linux-x86_64|linux-amd64) ARTIFACT="gitleaks_${VERSION}_linux_x64.tar.gz" ;;
  linux-arm64|linux-aarch64) ARTIFACT="gitleaks_${VERSION}_linux_arm64.tar.gz" ;;
  *)
    echo "不支持的平台: $OS $ARCH" >&2
    echo "请手动安装: brew install gitleaks  或见 https://github.com/gitleaks/gitleaks/releases" >&2
    exit 1
    ;;
esac

if [ ! -f "$SUMS" ]; then
  echo "缺少校验和文件: $SUMS" >&2
  exit 1
fi

EXPECTED="$(awk -v a="$ARTIFACT" '$2==a {print $1; exit}' "$SUMS")"
if [ -z "$EXPECTED" ]; then
  echo "校验和文件中无 $ARTIFACT" >&2
  exit 1
fi

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
ARCHIVE="$TMP/$ARTIFACT"
echo "下载 $BASE_URL/$ARTIFACT ..."
curl -fsSL -o "$ARCHIVE" "$BASE_URL/$ARTIFACT"

ACTUAL="$(shasum -a 256 "$ARCHIVE" | awk '{print $1}')"
if [ "$ACTUAL" != "$EXPECTED" ]; then
  echo "SHA256 不匹配:" >&2
  echo "  expected: $EXPECTED" >&2
  echo "  actual:   $ACTUAL" >&2
  exit 1
fi

tar -xzf "$ARCHIVE" -C "$TMP"
if [ ! -f "$TMP/gitleaks" ]; then
  echo "解压后未找到 gitleaks 二进制" >&2
  exit 1
fi
mkdir -p "$TOOLS"
cp "$TMP/gitleaks" "$DEST"
chmod +x "$DEST"
echo "已安装: $DEST ($("$DEST" version 2>/dev/null || echo "v$VERSION"))"
