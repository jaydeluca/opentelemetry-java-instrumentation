#!/usr/bin/env bash

# Disk Space Diagnostic Tool for OpenTelemetry Java Instrumentation
# This script analyzes disk usage patterns during builds and tests to identify
# areas where disk space can be optimized.

set -euo pipefail

REPORT_FILE="${DISK_SPACE_REPORT:-disk-space-report.txt}"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)

# Initialize report
cat > "${REPORT_FILE}" << EOF
=============================================================================
Disk Space Diagnostic Report
Generated: $(date)
=============================================================================

EOF

# Function to format bytes to human-readable format
format_bytes() {
    local bytes=$1
    if [[ $bytes -ge 1073741824 ]]; then
        echo "$(awk "BEGIN {printf \"%.2f GB\", $bytes/1073741824}")"
    elif [[ $bytes -ge 1048576 ]]; then
        echo "$(awk "BEGIN {printf \"%.2f MB\", $bytes/1048576}")"
    elif [[ $bytes -ge 1024 ]]; then
        echo "$(awk "BEGIN {printf \"%.2f KB\", $bytes/1024}")"
    else
        echo "${bytes} bytes"
    fi
}

# Function to measure directory size
measure_dir() {
    local dir=$1
    if [[ -d "$dir" ]]; then
        du -sb "$dir" 2>/dev/null | awk '{print $1}' || echo "0"
    else
        echo "0"
    fi
}

# Function to log and append to report
log_section() {
    echo ""
    echo "============================================================================="
    echo "$1"
    echo "============================================================================="
    echo ""

    {
        echo ""
        echo "============================================================================="
        echo "$1"
        echo "============================================================================="
        echo ""
    } >> "${REPORT_FILE}"
}

log_line() {
    echo "$1"
    echo "$1" >> "${REPORT_FILE}"
}

# Function to measure and report with before/after
measure_cleanup_impact() {
    local name=$1
    local dir=$2
    local before=$(measure_dir "$dir")
    local before_human=$(format_bytes "$before")

    log_line "  $name: $before_human"
    echo "$before"
}

# ============================================================================
# 1. INITIAL DISK SPACE OVERVIEW
# ============================================================================
log_section "1. INITIAL DISK SPACE OVERVIEW"

log_line "Filesystem usage:"
df -h | tee -a "${REPORT_FILE}"

log_line ""
log_line "Disk usage by mount point:"
df -h / 2>/dev/null | tail -n +2 | awk '{print "  Root: " $3 " used, " $4 " available (" $5 " full)"}' | tee -a "${REPORT_FILE}"

# ============================================================================
# 2. SYSTEM PRE-INSTALLED SOFTWARE ANALYSIS
# ============================================================================
log_section "2. SYSTEM PRE-INSTALLED SOFTWARE (Potential for cleanup)"

log_line "These are commonly pre-installed tools that may be safe to remove in CI:"
log_line ""

# Measure various pre-installed software
declare -A PREINSTALLED_SIZES
PREINSTALLED_SIZES["/usr/local/lib/android"]=$(measure_cleanup_impact "Android SDK" "/usr/local/lib/android")
PREINSTALLED_SIZES["/usr/share/dotnet"]=$(measure_cleanup_impact ".NET/dotnet" "/usr/share/dotnet")
PREINSTALLED_SIZES["/usr/local/julia"]=$(measure_cleanup_impact "Julia" "/usr/local/julia*")
PREINSTALLED_SIZES["/usr/share/swift"]=$(measure_cleanup_impact "Swift" "/usr/share/swift")
PREINSTALLED_SIZES["/opt/hostedtoolcache/CodeQL"]=$(measure_cleanup_impact "CodeQL" "/opt/hostedtoolcache/CodeQL")
PREINSTALLED_SIZES["/usr/local/.ghcup"]=$(measure_cleanup_impact "GHCup (Haskell)" "/usr/local/.ghcup")
PREINSTALLED_SIZES["/opt/az"]=$(measure_cleanup_impact "Azure CLI (/opt/az)" "/opt/az")
PREINSTALLED_SIZES["/usr/share/az_*"]=$(measure_cleanup_impact "Azure modules (/usr/share/az_*)" "/usr/share/az_*")
PREINSTALLED_SIZES["/usr/local/share/boost"]=$(measure_cleanup_impact "Boost C++ libraries" "/usr/local/share/boost")
PREINSTALLED_SIZES["/opt/hostedtoolcache/Ruby"]=$(measure_cleanup_impact "Ruby toolchain" "/opt/hostedtoolcache/Ruby")
PREINSTALLED_SIZES["/opt/hostedtoolcache/Python"]=$(measure_cleanup_impact "Python toolchain" "/opt/hostedtoolcache/Python")
PREINSTALLED_SIZES["/opt/hostedtoolcache/go"]=$(measure_cleanup_impact "Go toolchain" "/opt/hostedtoolcache/go")
PREINSTALLED_SIZES["/opt/hostedtoolcache/node"]=$(measure_cleanup_impact "Node.js toolchain" "/opt/hostedtoolcache/node")
PREINSTALLED_SIZES["/usr/local/share/powershell"]=$(measure_cleanup_impact "PowerShell" "/usr/local/share/powershell")

# Calculate total removable
total_removable=0
for size in "${PREINSTALLED_SIZES[@]}"; do
    total_removable=$((total_removable + size))
done

log_line ""
log_line "TOTAL POTENTIALLY REMOVABLE: $(format_bytes $total_removable)"

# ============================================================================
# 3. PROJECT BUILD ARTIFACTS ANALYSIS
# ============================================================================
log_section "3. PROJECT BUILD ARTIFACTS"

log_line "Analyzing project build directories and artifacts:"
log_line ""

# Find all build directories
if [[ -d "build" ]]; then
    build_size=$(measure_cleanup_impact "Root build/ directory" "build")
fi

# Instrumentation build directories
instrumentation_builds=$(find instrumentation -type d -name "build" 2>/dev/null | wc -l | tr -d ' ')
instrumentation_builds_size=0
if [[ $instrumentation_builds -gt 0 ]]; then
    instrumentation_builds_size=$(find instrumentation -type d -name "build" -exec du -sb {} + 2>/dev/null | awk '{sum+=$1} END {print sum}')
    log_line "  Instrumentation build/ dirs ($instrumentation_builds dirs): $(format_bytes $instrumentation_builds_size)"
fi

# Test results and reports
test_results_size=0
if [[ -n "$(find . -type d -name "test-results" 2>/dev/null)" ]]; then
    test_results_size=$(find . -type d -name "test-results" -exec du -sb {} + 2>/dev/null | awk '{sum+=$1} END {print sum}')
    log_line "  Test results: $(format_bytes $test_results_size)"
fi

reports_size=0
if [[ -n "$(find . -type d -name "reports" 2>/dev/null)" ]]; then
    reports_size=$(find . -type d -name "reports" -exec du -sb {} + 2>/dev/null | awk '{sum+=$1} END {print sum}')
    log_line "  Test reports: $(format_bytes $reports_size)"
fi

tmp_size=0
if [[ -n "$(find . -type d -path "*/build/tmp" 2>/dev/null)" ]]; then
    tmp_size=$(find . -type d -path "*/build/tmp" -exec du -sb {} + 2>/dev/null | awk '{sum+=$1} END {print sum}')
    log_line "  Build tmp directories: $(format_bytes $tmp_size)"
fi

# ============================================================================
# 4. GRADLE CACHE ANALYSIS
# ============================================================================
log_section "4. GRADLE CACHE ANALYSIS"

log_line "Analyzing Gradle cache directories:"
log_line ""

if [[ -d ~/.gradle ]]; then
    gradle_total=$(measure_cleanup_impact "Total ~/.gradle" ~/.gradle)

    # Break down by subdirectory
    if [[ -d ~/.gradle/caches ]]; then
        caches_size=$(measure_cleanup_impact "  Caches" ~/.gradle/caches)

        # More detailed cache breakdown
        if [[ -d ~/.gradle/caches/transforms-3 ]]; then
            transforms_size=$(measure_cleanup_impact "    Transforms" ~/.gradle/caches/transforms-*)
        fi

        if [[ -d ~/.gradle/caches/modules-2 ]]; then
            modules_size=$(measure_cleanup_impact "    Modules" ~/.gradle/caches/modules-*)
        fi

        if [[ -d ~/.gradle/caches/jars-* ]]; then
            jars_size=$(du -sb ~/.gradle/caches/jars-* 2>/dev/null | awk '{sum+=$1} END {print sum}')
            log_line "    JARs: $(format_bytes ${jars_size:-0})"
        fi
    fi

    if [[ -d ~/.gradle/daemon ]]; then
        daemon_size=$(measure_cleanup_impact "  Daemon logs" ~/.gradle/daemon)
    fi

    if [[ -d ~/.gradle/wrapper ]]; then
        wrapper_size=$(measure_cleanup_impact "  Wrapper distributions" ~/.gradle/wrapper)
    fi
fi

# ============================================================================
# 5. DOCKER ANALYSIS
# ============================================================================
log_section "5. DOCKER ANALYSIS"

if command -v docker &> /dev/null; then
    log_line "Docker disk usage:"
    log_line ""

    # Docker system df
    if docker info &>/dev/null; then
        docker system df 2>/dev/null | tee -a "${REPORT_FILE}" || log_line "  Unable to get Docker disk usage"

        log_line ""
        log_line "Docker details:"

        # Count and size of images
        image_count=$(docker images -q 2>/dev/null | wc -l | tr -d ' ')
        log_line "  Images: $image_count"

        # Count and size of containers
        container_count=$(docker ps -aq 2>/dev/null | wc -l | tr -d ' ')
        log_line "  Containers: $container_count"

        # Count of volumes
        volume_count=$(docker volume ls -q 2>/dev/null | wc -l | tr -d ' ')
        log_line "  Volumes: $volume_count"

        # Build cache
        build_cache=$(docker system df 2>/dev/null | grep "Build Cache" | awk '{print $3, $4}' || echo "unknown")
        log_line "  Build Cache: $build_cache"
    else
        log_line "  Docker daemon not running or not accessible"
    fi
else
    log_line "Docker not installed"
fi

# ============================================================================
# 6. PACKAGE MANAGER CACHES
# ============================================================================
log_section "6. PACKAGE MANAGER CACHES"

log_line "Analyzing package manager caches:"
log_line ""

# APT cache (Debian/Ubuntu)
if command -v apt-cache &> /dev/null; then
    apt_cache_size=$(measure_cleanup_impact "APT cache" /var/cache/apt)
fi

# Homebrew cache (macOS)
if command -v brew &> /dev/null; then
    brew_cache_size=$(measure_cleanup_impact "Homebrew cache" "$(brew --cache 2>/dev/null)")
fi

# ============================================================================
# 7. TOP DISK CONSUMERS IN PROJECT
# ============================================================================
log_section "7. TOP DISK CONSUMERS IN PROJECT"

log_line "Analyzing largest directories in the project (this may take a moment)..."
log_line ""
log_line "Top 20 largest directories:"

du -h . 2>/dev/null | sort -rh | head -n 20 | tee -a "${REPORT_FILE}"

# ============================================================================
# 8. CLEANUP SIMULATION (What would be freed?)
# ============================================================================
log_section "8. CLEANUP SIMULATION"

log_line "Simulating cleanup operations to estimate space savings:"
log_line ""

# Calculate what would be freed by common operations
declare -A cleanup_operations

# System software cleanup (from gha-free-disk-space.sh)
system_cleanup=0
for dir in "/usr/local/lib/android" "/usr/share/dotnet" "/usr/local/julia*" "/usr/share/swift" \
           "/opt/hostedtoolcache/CodeQL" "/usr/local/.ghcup" "/opt/az" "/usr/local/share/boost"; do
    if [[ -d "$dir" ]] || ls $dir &>/dev/null; then
        size=$(du -sb $dir 2>/dev/null | awk '{sum+=$1} END {print sum}')
        system_cleanup=$((system_cleanup + size))
    fi
done
cleanup_operations["System software removal"]=$system_cleanup

# Gradle cache cleanup
gradle_cleanup=0
for pattern in ~/.gradle/caches/transforms-* ~/.gradle/caches/modules-*/files-*; do
    if ls $pattern &>/dev/null; then
        size=$(du -sb $pattern 2>/dev/null | awk '{sum+=$1} END {print sum}')
        gradle_cleanup=$((gradle_cleanup + size))
    fi
done
cleanup_operations["Gradle cache cleanup (transforms + module files)"]=$gradle_cleanup

# Build artifacts cleanup
build_cleanup=$((test_results_size + reports_size + tmp_size))
cleanup_operations["Build artifacts (test-results, reports, tmp)"]=$build_cleanup

# Docker cleanup estimate (if available)
if docker info &>/dev/null; then
    docker_cleanup=$(docker system df 2>/dev/null | grep -E "Images|Containers|Local Volumes" | awk '{sum+=$4} END {print sum*1024*1024*1024}')
    cleanup_operations["Docker system prune"]=${docker_cleanup:-0}
fi

# Sort and display cleanup operations by size
log_line "Estimated space savings by operation:"
log_line ""
for op in "${!cleanup_operations[@]}"; do
    size=${cleanup_operations[$op]}
    log_line "  $op: $(format_bytes $size)"
done | sort -t':' -k2 -rh | tee -a "${REPORT_FILE}"

total_cleanup=0
for size in "${cleanup_operations[@]}"; do
    total_cleanup=$((total_cleanup + size))
done

log_line ""
log_line "TOTAL ESTIMATED CLEANUP POTENTIAL: $(format_bytes $total_cleanup)"

# ============================================================================
# 9. RECOMMENDATIONS
# ============================================================================
log_section "9. RECOMMENDATIONS"

log_line "Based on the analysis, here are the top recommendations:"
log_line ""

# Generate recommendations based on findings
recommendations=()

if [[ $total_removable -gt 5368709120 ]]; then  # > 5GB
    recommendations+=("HIGH IMPACT: Remove unused system software (saves $(format_bytes $total_removable))")
fi

if [[ $gradle_cleanup -gt 2147483648 ]]; then  # > 2GB
    recommendations+=("HIGH IMPACT: Clean Gradle transform cache between test batches (saves $(format_bytes $gradle_cleanup))")
fi

if [[ ${docker_cleanup:-0} -gt 1073741824 ]]; then  # > 1GB
    recommendations+=("MEDIUM IMPACT: Run 'docker system prune -af --volumes' between test batches (saves ~$(format_bytes ${docker_cleanup:-0}))")
fi

if [[ $build_cleanup -gt 536870912 ]]; then  # > 512MB
    recommendations+=("MEDIUM IMPACT: Clean test reports and tmp dirs between batches (saves $(format_bytes $build_cleanup))")
fi

if [[ ${#recommendations[@]} -eq 0 ]]; then
    recommendations+=("System appears to be relatively clean. Consider smaller optimizations.")
fi

for i in "${!recommendations[@]}"; do
    log_line "$((i+1)). ${recommendations[$i]}"
done

# ============================================================================
# 10. SUMMARY
# ============================================================================
log_section "10. SUMMARY"

current_usage=$(df -h / 2>/dev/null | tail -n 1 | awk '{print $3}')
current_available=$(df -h / 2>/dev/null | tail -n 1 | awk '{print $4}')

log_line "Current disk usage: $current_usage used, $current_available available"
log_line "Total cleanup potential: $(format_bytes $total_cleanup)"
log_line ""
log_line "Report saved to: ${REPORT_FILE}"

# ============================================================================
# OPTIONAL: Run actual cleanup verification
# ============================================================================
if [[ "${RUN_CLEANUP_TEST:-false}" == "true" ]]; then
    log_section "11. CLEANUP VERIFICATION (LIVE TEST)"

    log_line "WARNING: This will perform actual cleanup operations!"
    log_line "Set RUN_CLEANUP_TEST=false to skip this section."
    log_line ""

    # Test system software removal (commented out for safety)
    # log_line "Testing system software removal..."
    # before=$(df -k / | tail -n 1 | awk '{print $3}')
    # sudo rm -rf /usr/local/lib/android /usr/share/dotnet 2>/dev/null || true
    # after=$(df -k / | tail -n 1 | awk '{print $3}')
    # freed=$((before - after))
    # log_line "  Freed: $(format_bytes $((freed * 1024)))"

    # Test Docker cleanup
    if docker info &>/dev/null; then
        log_line "Testing Docker cleanup..."
        before=$(df -k / | tail -n 1 | awk '{print $3}')
        docker system prune -af --volumes 2>&1 | grep "Total reclaimed space" | tee -a "${REPORT_FILE}"
        after=$(df -k / | tail -n 1 | awk '{print $3}')
        freed=$((before - after))
        log_line "  Freed: $(format_bytes $((freed * 1024)))"
    fi
fi

echo ""
echo "============================================================================="
echo "Diagnostic complete! Report saved to: ${REPORT_FILE}"
echo "============================================================================="