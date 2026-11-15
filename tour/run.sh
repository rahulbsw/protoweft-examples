#!/usr/bin/env bash
# Starts the Protoweft Tour: builds the page if needed, then runs the app.
# Open http://localhost:8080 when it says so; Ctrl+C stops it.
#
#   ./tour/run.sh                    # scripted model, no API key needed
#   OPENAI_API_KEY=... ./tour/run.sh # a real LLM (any OpenAI-compatible server: also set
#                                    # SPRING_AI_OPENAI_BASE_URL, ending in /v1, and OPENAI_MODEL)
#   PORT=9090 ./tour/run.sh          # another port
set -euo pipefail
cd "$(dirname "$0")/.."

# The page's inputs: its own sources, and the Java and YAML the code snippets are cut from.
page=tour/src/main/resources/static/index.html
if [[ ! -f $page || -n $(find tour/frontend/src tour/frontend/chapters tour/frontend/index.html \
      tour/frontend/vite.config.ts tour/frontend/package.json tour/src/main/java tour/src/main/resources/application.yml \
      -newer "$page" -type f 2>/dev/null | head -1) ]]; then
  command -v npm >/dev/null || { echo "The tour page needs Node.js 20 or newer (npm) to build: https://nodejs.org" >&2; exit 1; }
  echo "Building the tour page…"
  (cd tour/frontend
   if [[ ! -d node_modules || package-lock.json -nt node_modules ]]; then npm ci --ignore-scripts --silent; fi
   npm run build --silent)
fi

echo "Starting the tour app (the first run downloads dependencies, which takes a minute)…"
# Plain console: Gradle's progress bar would sit at "EXECUTING" while the app runs, which looks stuck.
exec ./gradlew --quiet --console=plain :tour:bootRun
