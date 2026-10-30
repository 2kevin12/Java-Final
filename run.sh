#!/usr/bin/env bash
# ==============================================================================
# Restaurant Kitchen Display System (KDS) & Order Pipeline
# AI-Assisted Hospitality and Restaurant Kitchen Automation System
# Universal Startup & Runner Script (Linux, macOS, Git Bash / WSL on Windows)
# ==============================================================================

set -e

# ANSI Color Codes
CYAN='\033[0;36m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
BOLD='\033[1m'
NC='\033[0m' # No Color

# Determine project root directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

JAR_PATH="target/restaurant-kds-1.0.0.jar"
APP_NAME="Restaurant KDS & Order Pipeline"
DEFAULT_PORT=8080
PROFILE="default"
PORT=$DEFAULT_PORT
FORCE_BUILD=false
RUN_TESTS_ONLY=false

# ------------------------------------------------------------------------------
# Usage & Help Screen
# ------------------------------------------------------------------------------
show_help() {
    echo -e "${CYAN}${BOLD}===================================================================${NC}"
    echo -e "${CYAN}${BOLD}       🍽️  RESTAURANT KITCHEN DISPLAY SYSTEM (KDS) & PIPELINE      ${NC}"
    echo -e "${CYAN}${BOLD}===================================================================${NC}"
    echo -e "Usage: ./run.sh [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  --build, -b         Force a clean rebuild with Maven before running"
    echo "  --test, -t          Run unit & integration test suite (20 tests) and exit"
    echo "  --mysql             Run with native MySQL 8 profile (default uses H2 MySQL mode)"
    echo "  --port <PORT>       Specify custom server port (default: 8080)"
    echo "  --skip-tests        Package without executing tests (faster build)"
    echo "  --help, -h          Show this help information"
    echo ""
    echo "Examples:"
    echo "  ./run.sh                     # Run application (auto-builds jar if missing)"
    echo "  ./run.sh --build             # Clean rebuild and start server"
    echo "  ./run.sh --mysql             # Run against local MySQL 8 database"
    echo "  ./run.sh --port 9090         # Run on port 9090"
    echo "  ./run.sh --test              # Run full test suite"
    echo -e "${CYAN}-------------------------------------------------------------------${NC}"
    exit 0
}

# ------------------------------------------------------------------------------
# Parse Command Line Arguments
# ------------------------------------------------------------------------------
SKIP_TESTS_FLAG=""
while [[ $# -gt 0 ]]; do
    case "$1" in
        --build|-b)
            FORCE_BUILD=true
            shift
            ;;
        --test|-t)
            RUN_TESTS_ONLY=true
            shift
            ;;
        --mysql)
            PROFILE="mysql"
            shift
            ;;
        --port)
            PORT="$2"
            shift 2
            ;;
        --skip-tests)
            SKIP_TESTS_FLAG="-DskipTests"
            shift
            ;;
        --help|-h)
            show_help
            ;;
        *)
            echo -e "${YELLOW}Unknown option: $1${NC}"
            show_help
            ;;
    esac
done

# ------------------------------------------------------------------------------
# Detect Java Runtime
# ------------------------------------------------------------------------------
echo -e "${CYAN}🔍 Detecting Java runtime...${NC}"
JAVA_CMD=""

if command -v java >/dev/null 2>&1; then
    JAVA_CMD="java"
elif [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    JAVA_CMD="$JAVA_HOME/bin/java"
elif [ -x "/c/Program Files/JetBrains/IntelliJ IDEA 2026.2.0.1/jbr/bin/java" ]; then
    JAVA_CMD="/c/Program Files/JetBrains/IntelliJ IDEA 2026.2.0.1/jbr/bin/java"
elif [ -x "C:/Program Files/JetBrains/IntelliJ IDEA 2026.2.0.1/jbr/bin/java.exe" ]; then
    JAVA_CMD="C:/Program Files/JetBrains/IntelliJ IDEA 2026.2.0.1/jbr/bin/java.exe"
fi

if [ -z "$JAVA_CMD" ]; then
    echo -e "${RED}❌ Error: Java runtime (Java 21+) not found!${NC}"
    echo "Please install Java 21+ LTS or set JAVA_HOME in your environment."
    exit 1
fi

JAVA_VERSION_OUTPUT=$("$JAVA_CMD" -version 2>&1 | head -n 1)
echo -e "${GREEN}✓ Java detected:${NC} $JAVA_VERSION_OUTPUT"

# ------------------------------------------------------------------------------
# Detect Maven
# ------------------------------------------------------------------------------
detect_maven() {
    if command -v mvn >/dev/null 2>&1; then
        echo "mvn"
    elif [ -x "./mvnw" ]; then
        echo "./mvnw"
    elif [ -n "$M2_HOME" ] && [ -x "$M2_HOME/bin/mvn" ]; then
        echo "$M2_HOME/bin/mvn"
    elif [ -n "$MAVEN_HOME" ] && [ -x "$MAVEN_HOME/bin/mvn" ]; then
        echo "$MAVEN_HOME/bin/mvn"
    elif [ -x "/c/Program Files/JetBrains/IntelliJ IDEA 2026.2.0.1/plugins/maven-plugin/lib/maven3/bin/mvn" ]; then
        echo "/c/Program Files/JetBrains/IntelliJ IDEA 2026.2.0.1/plugins/maven-plugin/lib/maven3/bin/mvn"
    elif [ -x "C:/Program Files/JetBrains/IntelliJ IDEA 2026.2.0.1/plugins/maven-plugin/lib/maven3/bin/mvn.cmd" ]; then
        echo "C:/Program Files/JetBrains/IntelliJ IDEA 2026.2.0.1/plugins/maven-plugin/lib/maven3/bin/mvn.cmd"
    else
        echo ""
    fi
}

# ------------------------------------------------------------------------------
# Handle Test Suite Execution
# ------------------------------------------------------------------------------
if [ "$RUN_TESTS_ONLY" = true ]; then
    MVN_CMD=$(detect_maven)
    if [ -z "$MVN_CMD" ]; then
        echo -e "${RED}❌ Error: Maven not found to execute tests.${NC}"
        exit 1
    fi
    echo -e "${CYAN}🧪 Running 20 automated unit and integration tests...${NC}"
    "$MVN_CMD" test
    echo -e "${GREEN}✓ All automated tests completed successfully!${NC}"
    exit 0
fi

# ------------------------------------------------------------------------------
# Build JAR if requested or missing
# ------------------------------------------------------------------------------
if [ "$FORCE_BUILD" = true ] || [ ! -f "$JAR_PATH" ]; then
    MVN_CMD=$(detect_maven)
    if [ -z "$MVN_CMD" ]; then
        echo -e "${RED}❌ Error: Maven not found to build $JAR_PATH.${NC}"
        echo "Please install Apache Maven or configure MAVEN_HOME."
        exit 1
    fi

    echo -e "${CYAN}📦 Packaging application (${JAR_PATH})...${NC}"
    if [ -n "$SKIP_TESTS_FLAG" ]; then
        "$MVN_CMD" clean package "$SKIP_TESTS_FLAG"
    else
        "$MVN_CMD" clean package
    fi

    if [ ! -f "$JAR_PATH" ]; then
        echo -e "${RED}❌ Build failed: $JAR_PATH was not produced.${NC}"
        exit 1
    fi
    echo -e "${GREEN}✓ Build succeeded:${NC} $JAR_PATH"
fi

# ------------------------------------------------------------------------------
# Launch Application Banner
# ------------------------------------------------------------------------------
echo ""
echo -e "${CYAN}${BOLD}===================================================================${NC}"
echo -e "${GREEN}${BOLD}     🚀 RESTAURANT KITCHEN DISPLAY SYSTEM & ORDER PIPELINE        ${NC}"
echo -e "${CYAN}${BOLD}===================================================================${NC}"
echo -e "${BOLD}Profile:${NC}      ${YELLOW}${PROFILE}${NC}"
echo -e "${BOLD}Port:${NC}         ${GREEN}${PORT}${NC}"
echo -e "${BOLD}Database:${NC}     $([ "$PROFILE" = "mysql" ] && echo "MySQL 8 (kdsdb)" || echo "MySQL-Mode Persistent H2 (./data/kdsdb)")"
echo -e "${BOLD}Live Updates:${NC} Server-Sent Events (SSE) + Web Audio Chime"
echo -e "${CYAN}-------------------------------------------------------------------${NC}"
echo -e "${BOLD}Application URLs:${NC}"
echo -e "  • ${BOLD}Login:${NC}            ${CYAN}http://localhost:${PORT}/login${NC}"
echo -e "  • ${BOLD}Kitchen Display:${NC}  ${CYAN}http://localhost:${PORT}/kds${NC}"
echo -e "  • ${BOLD}Dashboard:${NC}        ${CYAN}http://localhost:${PORT}/dashboard${NC}"
echo -e "  • ${BOLD}Order Entry:${NC}      ${CYAN}http://localhost:${PORT}/orders/new${NC}"
echo -e "  • ${BOLD}Billing & POS:${NC}    ${CYAN}http://localhost:${PORT}/billing${NC}"
echo -e "  • ${BOLD}Reports & CSV:${NC}    ${CYAN}http://localhost:${PORT}/reports${NC}"
echo -e "${CYAN}-------------------------------------------------------------------${NC}"
echo -e "${BOLD}Default Seeded Credentials:${NC}"
echo -e "  • Admin:     ${YELLOW}admin / admin123${NC}"
echo -e "  • Kitchen:   ${YELLOW}kitchen / kitchen123${NC}"
echo -e "  • Cashier:   ${YELLOW}cashier / cashier123${NC}"
echo -e "  • Manager:   ${YELLOW}manager / manager123${NC}"
echo -e "${CYAN}===================================================================${NC}"
echo -e "${YELLOW}Starting Spring Boot application... Press [Ctrl+C] to stop.${NC}"
echo ""

# ------------------------------------------------------------------------------
# Run Application
# ------------------------------------------------------------------------------
JAVA_OPTS="-Dserver.port=${PORT}"
if [ "$PROFILE" = "mysql" ]; then
    JAVA_OPTS="$JAVA_OPTS -Dspring.profiles.active=mysql"
fi

exec "$JAVA_CMD" $JAVA_OPTS -jar "$JAR_PATH"
