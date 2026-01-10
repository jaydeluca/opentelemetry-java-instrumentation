#!/bin/bash -e

# GitHub Actions runners have only provide 14 GB of disk space which we have been exceeding regularly
# https://docs.github.com/en/actions/using-github-hosted-runners/about-github-hosted-runners#supported-runners-and-hardware-resources

echo "Disk space before cleanup:"
df -h

echo "Removing unnecessary software..."
sudo rm -rf /usr/local/lib/android
sudo rm -rf /usr/share/dotnet
sudo rm -rf /usr/local/julia*
sudo rm -rf /usr/share/swift
sudo rm -rf /opt/hostedtoolcache/CodeQL
sudo rm -rf /usr/local/.ghcup
sudo rm -rf /usr/share/az_*
sudo rm -rf /opt/az
sudo rm -rf /usr/local/share/boost

echo "Cleaning Docker artifacts..."
docker system prune -af --volumes || true

echo "Cleaning apt cache..."
sudo apt-get clean || true

echo "Disk space after cleanup:"
df -h
