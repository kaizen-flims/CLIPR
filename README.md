# CLIPR

**From Long to Legendary.**

CLIPR is an Android-first, AI-powered video clipping and vertical short-form editing application written in Kotlin with Jetpack Compose and AndroidX Media3. It transforms long-form videos, gaming sessions, and streams into 1080×1920 (9:16) vertical Shorts using a Bring-Your-Own-Key (BYOK) Google Gemini API workflow and on-device hardware rendering.

## Core Architecture

- **Language & UI:** 100% Kotlin, Jetpack Compose, Material 3 with locked CLIPR flat industrial design system.
- **Architecture:** Unidirectional MVVM (`ui`, `domain`, `data`, `media`) with Kotlin Coroutines and `StateFlow`.
- **AI Intelligence (BYOK):** User-supplied Gemini API key encrypted at rest via Android Keystore (`AES/GCM/NoPadding`), communicating directly with Google's Gemini API (`v1beta/files` and `generateContent`) with structured JSON outputs.
- **Video Processing & Rendering:** AndroidX Media3 Transformer (`1.5.0`), custom OpenGL ES shaders (`GlEffect`) for single-pass Facecam + Gameplay vertical reframing, ML Kit Face Detection, and `OverlayEffect` word-level animated subtitles.
- **Minimum SDK:** Android 8.0 (API 26) | **Target SDK:** Android 15 (API 35).

## Brand Palette

| Token | Hex | Role |
| :--- | :--- | :--- |
| Obsidian Black | `#080808` | Primary background and Studio canvas |
| Graphite | `#171717` | Flat elevated surfaces, navigation bar, and timeline tracks |
| Porcelain White | `#F5F5F5` | Primary typography and active icons |
| Signal Red | `#E53935` | Geometric frame brackets, playhead, and primary actions |
| Secondary Grey | `#A3A3A3` | Metadata, timecodes, and secondary labels |

## Developer

Developed by **Prem Das** ([@kaizen-flims](https://github.com/kaizen-flims)).
