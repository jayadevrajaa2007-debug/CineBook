#!/bin/bash
set -e

echo "==================================================="
echo "Starting CineBook Application"
echo "==================================================="

cd "$(dirname "$0")"

if [ ! -f ".env" ]; then
    echo "[ERROR] .env file not found!"
    echo "Please copy .env.example to .env and configure your MySQL credentials:"
    echo "  cp .env.example .env"
    exit 1
fi

set -a
# shellcheck disable=SC1091
source <(grep -v '^\s*#' .env | grep -v '^\s*$')
set +a

if [ -z "$MYSQL_PASSWORD" ]; then
    echo "[ERROR] MYSQL_PASSWORD is empty or not set in .env!"
    echo "Please edit .env and set your MySQL root password."
    exit 1
fi

export MYSQL_HOST="${MYSQL_HOST:-localhost}"
export MYSQL_PORT="${MYSQL_PORT:-3306}"
export MYSQL_DB="${MYSQL_DB:-cinebook}"
export MYSQL_USER="${MYSQL_USER:-root}"

echo "Database target: ${MYSQL_USER}@${MYSQL_HOST}:${MYSQL_PORT}/${MYSQL_DB}"

if [ -f "target/cinebook-1.0.0.jar" ]; then
    echo "Launching CineBook on http://localhost:8080 ..."
    java -jar target/cinebook-1.0.0.jar
else
    echo "Launching CineBook via Maven on http://localhost:8080 ..."
    mvn spring-boot:run
fi
