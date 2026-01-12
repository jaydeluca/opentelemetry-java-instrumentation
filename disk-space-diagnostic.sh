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
    if [[ ! "$bytes" =~ ^[0-9]+$ ]]; then
        echo "0 bytes"
        return
    fi
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

# Function to measure directory size (returns bytes only, no output)
measure_dir() {
    local dir=$1
    local size=0

    # Handle glob patterns
    if [[ "$dir" == *"*"* ]]; then
        if ls $dir 2>/dev/null | head -1 >/dev/null; then
            size=$(du -sb $dir 2>/dev/null | awk '{sum+=$1} END {print sum+0}')
        fi
    elif [[ -d "$dir" ]]; then
        size=$(du -sb "$dir" 2>/dev/null | awk '{print $1}')
    fi

    echo "${size:-0}"
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

log_line "=== Currently Removed by gha-free-disk-space.sh ==="
log_line ""

# Android SDK
android_size=$(measure_dir "/usr/local/lib/android")
PREINSTALLED_SIZES["android"]=$android_size
log_line "Android SDK (/usr/local/lib/android)"
log_line "  Size: $(format_bytes $android_size)"
log_line ""

# .NET
dotnet_size=$(measure_dir "/usr/share/dotnet")
PREINSTALLED_SIZES["dotnet"]=$dotnet_size
log_line ".NET/dotnet (/usr/share/dotnet)"
log_line "  Size: $(format_bytes $dotnet_size)"
log_line ""

# Julia
julia_size=$(measure_dir "/usr/local/julia*")
PREINSTALLED_SIZES["julia"]=$julia_size
log_line "Julia (/usr/local/julia*)"
log_line "  Size: $(format_bytes $julia_size)"
log_line ""

# Swift
swift_size=$(measure_dir "/usr/share/swift")
PREINSTALLED_SIZES["swift"]=$swift_size
log_line "Swift (/usr/share/swift)"
log_line "  Size: $(format_bytes $swift_size)"
log_line ""

# CodeQL
codeql_size=$(measure_dir "/opt/hostedtoolcache/CodeQL")
PREINSTALLED_SIZES["codeql"]=$codeql_size
log_line "CodeQL (/opt/hostedtoolcache/CodeQL)"
log_line "  Size: $(format_bytes $codeql_size)"
log_line ""

# GHCup
ghcup_size=$(measure_dir "/usr/local/.ghcup")
PREINSTALLED_SIZES["ghcup"]=$ghcup_size
log_line "GHCup - Haskell (/usr/local/.ghcup)"
log_line "  Size: $(format_bytes $ghcup_size)"
log_line ""

# Azure CLI
az_size=$(measure_dir "/opt/az")
az_modules_size=$(measure_dir "/usr/share/az_*")
az_total=$((az_size + az_modules_size))
PREINSTALLED_SIZES["az"]=$az_total
log_line "Azure CLI (/opt/az + /usr/share/az_*)"
log_line "  /opt/az: $(format_bytes $az_size)"
log_line "  /usr/share/az_*: $(format_bytes $az_modules_size)"
log_line "  Total: $(format_bytes $az_total)"
log_line ""

# Boost
boost_size=$(measure_dir "/usr/local/share/boost")
PREINSTALLED_SIZES["boost"]=$boost_size
log_line "Boost C++ libraries (/usr/local/share/boost)"
log_line "  Size: $(format_bytes $boost_size)"
log_line ""

log_line "=== Additional Pre-installed Tools (Not Currently Removed) ==="
log_line ""

# Ruby
ruby_size=$(measure_dir "/opt/hostedtoolcache/Ruby")
PREINSTALLED_SIZES["ruby"]=$ruby_size
log_line "Ruby toolchain (/opt/hostedtoolcache/Ruby)"
log_line "  Size: $(format_bytes $ruby_size)"
log_line ""

# Python
python_size=$(measure_dir "/opt/hostedtoolcache/Python")
PREINSTALLED_SIZES["python"]=$python_size
log_line "Python toolchain (/opt/hostedtoolcache/Python)"
log_line "  Size: $(format_bytes $python_size)"
log_line ""

# Go
go_size=$(measure_dir "/opt/hostedtoolcache/go")
PREINSTALLED_SIZES["go"]=$go_size
log_line "Go toolchain (/opt/hostedtoolcache/go)"
log_line "  Size: $(format_bytes $go_size)"
log_line ""

# Node.js
node_size=$(measure_dir "/opt/hostedtoolcache/node")
PREINSTALLED_SIZES["node"]=$node_size
log_line "Node.js toolchain (/opt/hostedtoolcache/node)"
log_line "  Size: $(format_bytes $node_size)"
log_line ""

# PowerShell
powershell_size=$(measure_dir "/usr/local/share/powershell")
PREINSTALLED_SIZES["powershell"]=$powershell_size
log_line "PowerShell (/usr/local/share/powershell)"
log_line "  Size: $(format_bytes $powershell_size)"
log_line ""

# Calculate totals
total_removable=0
currently_removed=0
additional_tools=0

for key in android dotnet julia swift codeql ghcup az boost; do
    size=${PREINSTALLED_SIZES[$key]}
    if [[ "$size" =~ ^[0-9]+$ ]]; then
        currently_removed=$((currently_removed + size))
    fi
done

for key in ruby python go node powershell; do
    size=${PREINSTALLED_SIZES[$key]}
    if [[ "$size" =~ ^[0-9]+$ ]]; then
        additional_tools=$((additional_tools + size))
    fi
done

total_removable=$((currently_removed + additional_tools))

log_line "============================================"
log_line "CURRENTLY REMOVED: $(format_bytes $currently_removed)"
log_line "ADDITIONAL AVAILABLE: $(format_bytes $additional_tools)"
log_line "TOTAL POTENTIALLY REMOVABLE: $(format_bytes $total_removable)"
log_line "============================================"

# ============================================================================
# 3. PROJECT BUILD ARTIFACTS ANALYSIS
# ============================================================================
log_section "3. PROJECT BUILD ARTIFACTS"

log_line "Analyzing project build directories and artifacts:"
log_line ""

# Find all build directories
build_size=0
if [[ -d "build" ]]; then
    build_size=$(measure_dir "build")
    log_line "  Root build/ directory: $(format_bytes $build_size)"
fi

# Instrumentation build directories
instrumentation_builds=$(find instrumentation -type d -name "build" 2>/dev/null | wc -l | tr -d ' ')
instrumentation_builds_size=0
if [[ $instrumentation_builds -gt 0 ]]; then
    instrumentation_builds_size=$(find instrumentation -type d -name "build" -exec du -sb {} + 2>/dev/null | awk '{sum+=$1} END {print sum+0}')
    log_line "  Instrumentation build/ dirs ($instrumentation_builds dirs): $(format_bytes $instrumentation_builds_size)"
fi

# Test results and reports
test_results_size=0
if [[ -n "$(find . -type d -name "test-results" 2>/dev/null | head -1)" ]]; then
    test_results_size=$(find . -type d -name "test-results" -exec du -sb {} + 2>/dev/null | awk '{sum+=$1} END {print sum+0}')
    log_line "  Test results: $(format_bytes $test_results_size)"
fi

reports_size=0
if [[ -n "$(find . -type d -name "reports" 2>/dev/null | head -1)" ]]; then
    reports_size=$(find . -type d -name "reports" -exec du -sb {} + 2>/dev/null | awk '{sum+=$1} END {print sum+0}')
    log_line "  Test reports: $(format_bytes $reports_size)"
fi

tmp_size=0
if [[ -n "$(find . -type d -path "*/build/tmp" 2>/dev/null | head -1)" ]]; then
    tmp_size=$(find . -type d -path "*/build/tmp" -exec du -sb {} + 2>/dev/null | awk '{sum+=$1} END {print sum+0}')
    log_line "  Build tmp directories: $(format_bytes $tmp_size)"
fi

# ============================================================================
# 4. GRADLE CACHE ANALYSIS
# ============================================================================
log_section "4. GRADLE CACHE ANALYSIS"

log_line "Analyzing Gradle cache directories:"
log_line ""

if [[ -d ~/.gradle ]]; then
    gradle_total=$(measure_dir ~/.gradle)
    log_line "Total ~/.gradle: $(format_bytes $gradle_total)"
    log_line ""

    # Break down by subdirectory
    if [[ -d ~/.gradle/caches ]]; then
        caches_size=$(measure_dir ~/.gradle/caches)
        log_line "  Caches: $(format_bytes $caches_size)"

        # More detailed cache breakdown
        if ls ~/.gradle/caches/transforms-* 2>/dev/null | head -1 >/dev/null; then
            transforms_size=$(measure_dir "~/.gradle/caches/transforms-*")
            log_line "    Transforms: $(format_bytes $transforms_size)"
        fi

        if ls ~/.gradle/caches/modules-* 2>/dev/null | head -1 >/dev/null; then
            modules_size=$(measure_dir "~/.gradle/caches/modules-*")
            log_line "    Modules: $(format_bytes $modules_size)"
        fi

        if ls ~/.gradle/caches/jars-* 2>/dev/null | head -1 >/dev/null; then
            jars_size=$(measure_dir "~/.gradle/caches/jars-*")
            log_line "    JARs: $(format_bytes ${jars_size})"
        fi
    fi

    if [[ -d ~/.gradle/daemon ]]; then
        daemon_size=$(measure_dir ~/.gradle/daemon)
        log_line "  Daemon logs: $(format_bytes $daemon_size)"
    fi

    if [[ -d ~/.gradle/wrapper ]]; then
        wrapper_size=$(measure_dir ~/.gradle/wrapper)
        log_line "  Wrapper distributions: $(format_bytes $wrapper_size)"
    fi
else
    log_line "No Gradle cache found at ~/.gradle"
fi

# ============================================================================
# 5. DOCKER ANALYSIS
# ============================================================================
log_section "5. DOCKER ANALYSIS"

if command -v docker &> /dev/null; then
    log_line "Docker disk usage:"
    log_line ""

    # Docker system df
    if docker info &>/dev/null 2>&1; then
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
        build_cache=$(docker system df 2>/dev/null | grep "Build Cache" | awk '{print $3 " " $4}' || echo "0B")
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
    apt_cache_size=$(measure_dir /var/cache/apt)
    log_line "  APT cache: $(format_bytes $apt_cache_size)"
fi

# Homebrew cache (macOS)
if command -v brew &> /dev/null; then
    brew_cache_path=$(brew --cache 2>/dev/null || echo "")
    if [[ -n "$brew_cache_path" && -d "$brew_cache_path" ]]; then
        brew_cache_size=$(measure_dir "$brew_cache_path")
        log_line "  Homebrew cache: $(format_bytes $brew_cache_size)"
    fi
fi

# ============================================================================
# 7. TOP DISK CONSUMERS IN PROJECT
# ============================================================================
log_section "7. TOP DISK CONSUMERS IN PROJECT"

log_line "Analyzing largest directories in the project (this may take a moment)..."
log_line ""
log_line "Top 20 largest directories:"
log_line ""

du -h . 2>/dev/null | sort -rh 2>/dev/null | head -n 20 | while IFS= read -r line; do
    log_line "$line"
done

# ============================================================================
# 8. CLEANUP SIMULATION (What would be freed?)
# ============================================================================
log_section "8. CLEANUP SIMULATION"

log_line "Simulating cleanup operations to estimate space savings:"
log_line ""

# Calculate what would be freed by common operations
declare -A cleanup_operations

# System software cleanup (currently in gha-free-disk-space.sh)
cleanup_operations["System software removal (gha-free-disk-space.sh)"]=$currently_removed

# Gradle cache cleanup
gradle_cleanup=0
if ls ~/.gradle/caches/transforms-* 2>/dev/null | head -1 >/dev/null; then
    transforms_clean=$(measure_dir "~/.gradle/caches/transforms-*")
    gradle_cleanup=$((gradle_cleanup + transforms_clean))
fi
if ls ~/.gradle/caches/modules-*/files-* 2>/dev/null | head -1 >/dev/null; then
    modules_files_clean=$(measure_dir "~/.gradle/caches/modules-*/files-*")
    gradle_cleanup=$((gradle_cleanup + modules_files_clean))
fi
cleanup_operations["Gradle cache cleanup (transforms + module files)"]=$gradle_cleanup

# Build artifacts cleanup
build_cleanup=$((test_results_size + reports_size + tmp_size))
cleanup_operations["Build artifacts (test-results, reports, tmp)"]=$build_cleanup

# Docker cleanup estimate (if available)
docker_cleanup=0
if docker info &>/dev/null 2>&1; then
    # Try to parse reclaimable space from docker system df
    docker_reclaimable=$(docker system df 2>/dev/null | grep -E "Images|Containers|Local Volumes" | awk '{print $4}' | grep -v "RECLAIMABLE" | head -3)
    log_line "Docker reclaimable space estimate:"
    while IFS= read -r line; do
        if [[ -n "$line" ]]; then
            log_line "  $line"
        fi
    done <<< "$docker_reclaimable"
    cleanup_operations["Docker system prune"]=0  # Can't easily parse mixed units
fi

# Sort and display cleanup operations by size
log_line ""
log_line "Estimated space savings by operation:"
log_line ""

# Create a sorted list
for op in "${!cleanup_operations[@]}"; do
    size=${cleanup_operations[$op]}
    if [[ "$size" =~ ^[0-9]+$ ]] && [[ $size -gt 0 ]]; then
        printf "%020d|%s\n" "$size" "$op"
    fi
done | sort -t'|' -k1 -rn | while IFS='|' read size_padded op; do
    size=$((10#$size_padded))  # Remove leading zeros
    log_line "  $op: $(format_bytes $size)"
done

total_cleanup=0
for size in "${cleanup_operations[@]}"; do
    if [[ "$size" =~ ^[0-9]+$ ]]; then
        total_cleanup=$((total_cleanup + size))
    fi
done

log_line ""
log_line "TOTAL ESTIMATED CLEANUP POTENTIAL: $(format_bytes $total_cleanup)"
log_line "(Note: Docker cleanup not included in total as it varies)"

# ============================================================================
# 9. RECOMMENDATIONS
# ============================================================================
log_section "9. RECOMMENDATIONS"

log_line "Based on the analysis, here are the top recommendations:"
log_line ""

# Generate recommendations based on findings
recommendations=()

if [[ $currently_removed -gt 5368709120 ]]; then  # > 5GB
    recommendations+=("HIGH IMPACT: System software removal saves $(format_bytes $currently_removed)")
    recommendations+=("  → Already implemented in gha-free-disk-space.sh")
elif [[ $currently_removed -gt 0 ]]; then
    recommendations+=("VERIFIED: System software removal saves $(format_bytes $currently_removed)")
    recommendations+=("  → Already implemented in gha-free-disk-space.sh")
fi

if [[ $gradle_cleanup -gt 2147483648 ]]; then  # > 2GB
    recommendations+=("HIGH IMPACT: Clean Gradle transform cache between test batches")
    recommendations+=("  → Potential savings: $(format_bytes $gradle_cleanup)")
    recommendations+=("  → Add to ci-collect-batched.sh cleanup_resources()")
fi

if [[ $build_cleanup -gt 536870912 ]]; then  # > 512MB
    recommendations+=("MEDIUM IMPACT: Clean test reports and tmp dirs between batches")
    recommendations+=("  → Potential savings: $(format_bytes $build_cleanup)")
    recommendations+=("  → Add to ci-collect-batched.sh cleanup_resources()")
fi

if [[ $additional_tools -gt 1073741824 ]]; then  # > 1GB
    recommendations+=("MEDIUM IMPACT: Remove additional pre-installed tools")
    recommendations+=("  → Potential savings: $(format_bytes $additional_tools)")
    recommendations+=("  → Consider adding Ruby, Python, Go, Node.js to gha-free-disk-space.sh")
fi

recommendations+=("ONGOING: Run 'docker system prune -af --volumes' between test batches")
recommendations+=("  → Already implemented in ci-collect-batched.sh")

if [[ ${#recommendations[@]} -eq 0 ]]; then
    recommendations+=("System appears to be relatively clean. Consider smaller optimizations.")
fi

for i in "${!recommendations[@]}"; do
    log_line "${recommendations[$i]}"
done

# ============================================================================
# 10. SUMMARY
# ============================================================================
log_section "10. SUMMARY"

current_usage=$(df -h / 2>/dev/null | tail -n 1 | awk '{print $3}')
current_available=$(df -h / 2>/dev/null | tail -n 1 | awk '{print $4}')
current_percent=$(df -h / 2>/dev/null | tail -n 1 | awk '{print $5}')

log_line "Current disk usage: $current_usage used, $current_available available ($current_percent full)"
log_line ""
log_line "Cleanup already applied:"
log_line "  System software removal: $(format_bytes $currently_removed)"
log_line ""
log_line "Additional cleanup potential:"
log_line "  Gradle caches: $(format_bytes $gradle_cleanup)"
log_line "  Build artifacts: $(format_bytes $build_cleanup)"
log_line "  Additional pre-installed tools: $(format_bytes $additional_tools)"
log_line ""
log_line "Total measurable cleanup potential: $(format_bytes $total_cleanup)"
log_line ""
log_line "Report saved to: ${REPORT_FILE}"

echo ""
echo "============================================================================="
echo "Diagnostic complete! Report saved to: ${REPORT_FILE}"
echo "============================================================================="