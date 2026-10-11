#!/bin/sh
# builds the notifier helper as a binary into the mod's resources

set -e
cd "$(dirname "$0")"

out=../src/main/resources/mochaccino/notifier/MochaccinoNotifier
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

for arch in arm64 x86_64; do
	xcrun swiftc -O -swift-version 5 -target "$arch-apple-macos13.0" MochaccinoNotifier.swift -o "$tmp/$arch"
done
lipo -create "$tmp/arm64" "$tmp/x86_64" -output "$out"
chmod +x "$out"
lipo -info "$out"
