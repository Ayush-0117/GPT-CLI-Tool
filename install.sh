#!/usr/bin/env bash
# ─────────────────────────────────────────────────────
# GPT CLI Tool — Cross-Platform Installer
# ─────────────────────────────────────────────────────
# Usage:  ./install.sh
#
# What it does:
#   1. Checks for Java 21+ and Maven
#   2. Builds the shaded JAR
#   3. Creates a symlink so you can type "gpt" anywhere
#
# Supports: Linux, macOS
# For Windows: see install.ps1
# ─────────────────────────────────────────────────────

set -euo pipefail

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m' # No Color

info()    { echo -e "${CYAN}ℹ ${NC}$1"; }
success() { echo -e "${GREEN}✔ ${NC}$1"; }
error()   { echo -e "${RED}✖ ${NC}$1"; }

echo ""
echo -e "${BOLD}${CYAN}   G P T   C L I   Installer${NC}"
echo -e "${BOLD}   ─────────────────────────${NC}"
echo ""

# ── Resolve project directory ──
SOURCE="${BASH_SOURCE[0]}"
while [ -h "$SOURCE" ]; do
  DIR="$( cd -P "$( dirname "$SOURCE" )" > /dev/null 2>&1 && pwd )"
  SOURCE="$(readlink "$SOURCE")"
  [[ $SOURCE != /* ]] && SOURCE="$DIR/$SOURCE"
done
PROJECT_DIR="$( cd -P "$( dirname "$SOURCE" )" > /dev/null 2>&1 && pwd )"

# ── 1. Check Java ──
info "Checking for Java..."
if ! command -v java &> /dev/null; then
  error "Java is not installed."
  echo "  Please install Java 21+: https://adoptium.net"
  exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | head -1 | awk -F '"' '{print $2}' | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 17 ] 2>/dev/null; then
  error "Java $JAVA_VERSION detected. Java 17+ is required (21+ recommended)."
  exit 1
fi
success "Java $JAVA_VERSION found."

# ── 2. Check Maven ──
info "Checking for Maven..."
if ! command -v mvn &> /dev/null; then
  error "Maven is not installed."
  echo "  Install: https://maven.apache.org/install.html"
  echo "  Or:  sudo dnf install maven  /  brew install maven  /  sudo apt install maven"
  exit 1
fi
success "Maven found: $(mvn --version | head -1)"

# ── 3. Build ──
info "Building GPT CLI Tool..."
(cd "$PROJECT_DIR" && mvn clean package -DskipTests -q)
success "Build complete."

# ── 4. Make launcher executable ──
chmod +x "$PROJECT_DIR/gpt"

# ── 5. Create symlink ──
INSTALL_DIR="$HOME/.local/bin"

# Detect best install dir
if [ -d "$HOME/.local/bin" ]; then
  INSTALL_DIR="$HOME/.local/bin"
elif [ -d "/usr/local/bin" ] && [ -w "/usr/local/bin" ]; then
  INSTALL_DIR="/usr/local/bin"
else
  mkdir -p "$HOME/.local/bin"
  INSTALL_DIR="$HOME/.local/bin"
fi

info "Installing 'gpt' command to $INSTALL_DIR ..."

# Remove existing symlink/file if present
if [ -L "$INSTALL_DIR/gpt" ] || [ -f "$INSTALL_DIR/gpt" ]; then
  rm -f "$INSTALL_DIR/gpt"
fi

ln -s "$PROJECT_DIR/gpt" "$INSTALL_DIR/gpt"
success "Symlink created: $INSTALL_DIR/gpt → $PROJECT_DIR/gpt"

# ── 6. Check PATH ──
if [[ ":$PATH:" != *":$INSTALL_DIR:"* ]]; then
  echo ""
  echo -e "${BOLD}${RED}⚠  $INSTALL_DIR is not in your PATH!${NC}"
  echo ""
  echo "  Add it by appending this line to your shell config file:"
  echo ""

  SHELL_NAME=$(basename "$SHELL")
  case "$SHELL_NAME" in
    zsh)   CONFIG="~/.zshrc" ;;
    bash)  CONFIG="~/.bashrc" ;;
    fish)  CONFIG="~/.config/fish/config.fish" ;;
    *)     CONFIG="~/.profile" ;;
  esac

  if [ "$SHELL_NAME" = "fish" ]; then
    echo -e "  ${CYAN}set -gx PATH $INSTALL_DIR \$PATH${NC}"
  else
    echo -e "  ${CYAN}export PATH=\"$INSTALL_DIR:\$PATH\"${NC}"
  fi
  echo ""
  echo "  Then restart your terminal or run:  source $CONFIG"
fi

echo ""
echo -e "${BOLD}${GREEN}✔ Installation complete!${NC}"
echo ""
echo "  Usage:  gpt"
echo "  Config: Copy .env.example → .env and add your API keys."
echo ""
