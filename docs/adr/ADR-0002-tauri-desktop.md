# ADR-0002: Tauri v2 for Desktop Client

## Status
Accepted

## Context
Desktop was originally planned on Compose Multiplatform Desktop (JVM). However, JVM desktop binaries introduce substantial memory footprints (~150MB+ idle), lack deep Windows system tray panel integrations, and require distributing heavy JRE runtimes.

## Decision
Adopt **Tauri v2** (React + TypeScript frontend, Rust backend) for Windows, macOS, and Linux desktop clients.

## Benefits
- Native Windows system tray mini-panel support.
- Ultra-low memory usage (<25MB idle).
- Fast Webview2 rendering matching modern web standards.
- High performance Rust crypto & socket operations shared with daemon agent.
