# mc-launcher

A small PyQt6 desktop launcher for Minecraft. It reads the official version
manifest, downloads vanilla, installs Forge or Fabric, and starts the game.

> This file is deliberately *not* called `README`: names beginning with
> `readme` or `licen[cs]e` are protected in this repository, and any pull
> request that adds or modifies one is closed automatically.

## Requirements

- Python 3.10 or newer (this copy was verified on 3.14)
- A Java runtime matching the version you want to launch
- Network access to the mirrors listed at the bottom

## Install and run

```
pip install -r requirements.txt
python main.py
```

## Layout

```
main.py                  entry point: python main.py
requirements.txt         PyQt6, requests
mc_launcher/
    config.py            default paths, memory, player name, log noise list
    util.py              rule matching, natives, maven paths, log filter
    manifest.py          version manifest fetcher + vanilla downloader
    forge.py             Forge downloader
    fabric.py            Fabric installer
    workers.py           download / install QThread workers
    launch.py            launch thread: inheritsFrom merge, classpath, JVM args
    dialogs.py           Forge and Fabric version pickers
    ui.py                MainWindow
    ui_actions.py        main-window actions: launch, stop, download
    ui_install.py        main-window actions: install Forge / Fabric
```

## Configuration

Everything can be edited in the window: MC directory, Java path, player name
and memory. The defaults live in `mc_launcher/config.py`:

| Constant | Meaning |
|---|---|
| `DEFAULT_MC_DIR` | the `.minecraft` directory to install into |
| `DEFAULT_JAVA` | full path to `java.exe`; only checked for existence |
| `DEFAULT_USERNAME` | player name passed to the game |
| `DEFAULT_MEMORY` | `-Xmx` value in MB |
| `LOG_NOISE_PATTERNS` | substrings hidden from the log pane |

## Features

- **Version list**: official manifest from BMCLAPI plus versions already
  installed locally, grouped into Releases / Snapshots / Old versions /
  Local versions, with an `[installed]` marker.
- **Download vanilla**: version JSON, libraries, client jar and assets, then
  extracts natives.
- **Install Forge**: downloads the installer and runs it.
- **Install Fabric**: writes the loader version JSON and optionally drops
  Fabric API into `mods/`; supports version isolation.
- **Launch**: merges the `inheritsFrom` chain, builds the classpath and JVM
  arguments, then streams the game's output into the log pane.
- **Log filter**: lines matching `LOG_NOISE_PATTERNS` are hidden, so
  "Saving chunks" and friends do not drown the interesting output.

## How launching works

The version JSON's `${...}` placeholders are substituted by hand. The game
directory is set to the version folder, which is what makes version isolation
work: a `mods/` directory next to `versions/<name>/` belongs to that version
only.

The launcher runs in **offline mode**: the player name is whatever you typed,
the access token is `0` and the UUID is a fixed placeholder. There is no
Microsoft / OAuth login, so online-mode servers will reject it.

## Known limitations

- No modpack import and no CurseForge handling; Fabric API is the only mod it
  fetches on its own.
- Downloads go over plain HTTPS; SHA1 is verified where the manifest supplies
  a hash.
- The modular split is covered by a headless smoke test (window construction
  and signal wiring). Run one full download-and-launch cycle locally before
  pointing it at a real instance.

## Third-party services

| Service | Used for |
|---|---|
| `bmclapi2.bangbang93.com` | version manifest, library and client mirrors, Forge |
| `meta.fabricmc.net` | Fabric loader and API versions |
| `api.modrinth.com` | Fabric API downloads |
| `maven.fabricmc.net` | Fabric libraries |
| `resources.download.minecraft.net` | vanilla asset objects |

All of them are third-party services with their own terms; this launcher only
calls their public endpoints and stores nothing about you.
