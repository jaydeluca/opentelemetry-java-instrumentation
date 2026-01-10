#!/usr/bin/env bash

# Runs selected Gradle test tasks to regenerate *.telemetry output for
# individual OpenTelemetry Java agent instrumentations.
# This version processes tests in batches with cleanup between batches.

set -euo pipefail

source "$(dirname "$0")/instrumentations.sh"

# Configure Gradle flags based on DEBUG_LOGGING environment variable
GRADLE_FLAGS=()
if [[ "${DEBUG_LOGGING:-false}" != "true" ]]; then
  GRADLE_FLAGS+=(--no-daemon --quiet)
  echo "Running with reduced logging (set DEBUG_LOGGING=true to enable full output)"
else
  echo "Running with full debug logging enabled"
fi

# Function to cleanup Docker and Gradle resources
cleanup_resources() {
  echo "Cleaning up Docker resources..."
  docker system prune -af --volumes || true
  echo "Cleaning up Gradle cache (preserving telemetry data)..."
  # Clean Gradle cache but preserve .telemetry directories
  rm -rf ~/.gradle/caches/transforms-* || true
  rm -rf ~/.gradle/caches/modules-*/files-* || true
  # Remove test reports and build outputs (but preserve .telemetry)
  find . -type d -name "build" -not -path "*/.telemetry/*" -exec rm -rf {}/reports {}/test-results {}/tmp \; 2>/dev/null || true
  echo "Disk space after cleanup:"
  df -h
}

# Process tasks in batches
BATCH_SIZE=50

# Collect standard and colima tasks (without testLatestDeps)
ALL_TASKS=()
for task in "${INSTRUMENTATIONS[@]}"; do
  ALL_TASKS+=(":instrumentation:${task}")
done
for task in "${COLIMA_INSTRUMENTATIONS[@]}"; do
  ALL_TASKS+=(":instrumentation:${task}")
done

echo "Disk space before tests:"
df -h
echo "Total tasks to process: ${#ALL_TASKS[@]}"

# Process standard instrumentations in batches
for ((i=0; i<${#ALL_TASKS[@]}; i+=BATCH_SIZE)); do
  BATCH_NUM=$((i/BATCH_SIZE + 1))
  BATCH=("${ALL_TASKS[@]:i:BATCH_SIZE}")
  echo "Processing batch ${BATCH_NUM} (tasks $((i+1))-$((i+${#BATCH[@]})) of ${#ALL_TASKS[@]})..."

  ./gradlew "${BATCH[@]}" \
    -PcollectMetadata=true \
    "${GRADLE_FLAGS[@]}" \
    --rerun-tasks --continue

  # Cleanup after each batch
  cleanup_resources
done

# Collect and run tasks that need testLatestDeps
LATEST_DEPS_TASKS=()
for task in "${TEST_LATEST_DEPS_INSTRUMENTATIONS[@]}"; do
  LATEST_DEPS_TASKS+=(":instrumentation:${task}")
done

if [[ ${#LATEST_DEPS_TASKS[@]} -gt 0 ]]; then
  echo "Processing instrumentations with -PtestLatestDeps=true..."
  ./gradlew "${LATEST_DEPS_TASKS[@]}" \
    -PcollectMetadata=true \
    -PtestLatestDeps=true \
    "${GRADLE_FLAGS[@]}" \
    --rerun-tasks --continue

  # Final cleanup
  cleanup_resources
fi

echo "Telemetry file regeneration complete."