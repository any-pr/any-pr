import sys
import json
import subprocess
import shlex
from pathlib import Path
from datetime import datetime
from PyQt6.QtCore import QObject, pyqtSignal
from .util import is_noise, maven_to_path, rules_allow


# ============================================================
# Launch thread
# ============================================================
class Worker(QObject):
    log = pyqtSignal(str)
    finished = pyqtSignal()

    def __init__(self, config):
        super().__init__()
        self.config = config
        self.process = None
        self._stopped = False

    def launch(self):
        try:
            cmd = self._build_command()
            self.log.emit("=" * 60)
            self.log.emit("Full command:")
            self.log.emit("-" * 60)
            for a in cmd:
                self.log.emit(f"  {a}" if len(a) < 120 else f"  {a[:117]}...")
            self.log.emit("-" * 60)
            self.log.emit(f"[{self._ts()}] Starting process...")
            self.process = subprocess.Popen(
                cmd,
                cwd=str(self.config["version_dir"]),
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
                encoding="utf-8",
                errors="replace",
                creationflags=subprocess.CREATE_NO_WINDOW if sys.platform == "win32" else 0,
            )
            self.log.emit(f"[{self._ts()}] ✓ Process started, PID={self.process.pid}")
            for line in self.process.stdout:
                if self._stopped:
                    break
                line = line.rstrip()
                # Filter noise
                if is_noise(line):
                    continue
                self.log.emit(line)
            rc = self.process.wait()
            self.log.emit(f"[{self._ts()}] Process exited, code={rc}")
        except FileNotFoundError as e:
            self.log.emit(f"❌ File not found: {e}")
        except Exception as e:
            self.log.emit(f"❌ Launch failed: {type(e).__name__}: {e}")
            import traceback
            self.log.emit(traceback.format_exc())
        finally:
            self.finished.emit()

    def stop(self):
        self._stopped = True
        if self.process and self.process.poll() is None:
            self.process.terminate()
            self.log.emit(f"[{self._ts()}] Termination signal sent")

    def _load_version_with_inherits(self, mc_dir, version_id):
        cache = {}

        def _load(vid, depth=0):
            if vid in cache:
                return cache[vid]
            if depth > 5:
                raise RuntimeError(f"Version inheritance chain too deep: {vid}")
            version_dir = mc_dir / "versions" / vid
            json_path = version_dir / f"{vid}.json"
            if not json_path.exists():
                raise FileNotFoundError(f"Version JSON not found: {json_path}")
            with open(json_path, "r", encoding="utf-8") as f:
                vj = json.load(f)
            parent_id = vj.get("inheritsFrom")
            if parent_id:
                self.log.emit(f"  Loading: {vid}  (inherits from {parent_id})")
                parent = _load(parent_id, depth + 1)
                merged = self._merge_version(parent, vj)
            else:
                self.log.emit(f"  Loading: {vid}")
                merged = vj
            cache[vid] = merged
            return merged

        return _load(version_id)

    def _merge_version(self, parent, child):
        libs_by_name = {}
        for lib in parent.get("libraries", []):
            libs_by_name[lib.get("name", "")] = lib
        for lib in child.get("libraries", []):
            libs_by_name[lib.get("name", "")] = lib

        main_class = child.get("mainClass") or parent.get("mainClass")

        child_args = child.get("arguments")
        parent_args = parent.get("arguments")
        if child_args and child_args.get("jvm") and child_args.get("game"):
            arguments = child_args
        elif parent_args and child_args:
            arguments = {
                "jvm": parent_args.get("jvm", []) + child_args.get("jvm", []),
                "game": parent_args.get("game", []) + child_args.get("game", []),
            }
        elif parent_args:
            arguments = parent_args
        elif child_args:
            arguments = child_args
        else:
            arguments = {"jvm": [], "game": []}

        mc_args = child.get("minecraftArguments") or parent.get("minecraftArguments")
        asset_index = child.get("assetIndex") or parent.get("assetIndex")
        downloads = dict(parent.get("downloads", {}))
        downloads.update(child.get("downloads", {}))
        java_version = child.get("javaVersion") or parent.get("javaVersion", {})
        logging_cfg = child.get("logging") or parent.get("logging")

        return {
            "id": child.get("id", ""),
            "mainClass": main_class,
            "javaVersion": java_version,
            "arguments": arguments,
            "minecraftArguments": mc_args,
            "libraries": list(libs_by_name.values()),
            "assetIndex": asset_index,
            "downloads": downloads,
            "logging": logging_cfg,
            "type": child.get("type") or parent.get("type", "release"),
            "assets": child.get("assets") or parent.get("assets"),
        }

    def _get_root_parent(self, mc_dir, version_id):
        current = version_id
        for _ in range(10):
            json_path = mc_dir / "versions" / current / f"{current}.json"
            if not json_path.exists():
                break
            with open(json_path, "r", encoding="utf-8") as f:
                vj = json.load(f)
            parent = vj.get("inheritsFrom")
            if not parent:
                return current
            current = parent
        return version_id

    def _resolve_library_path(self, mc_dir, lib):
        downloads = lib.get("downloads", {})
        artifact = downloads.get("artifact")
        if artifact:
            return mc_dir / "libraries" / artifact["path"]
        name = lib.get("name", "")
        rel_path, _ = maven_to_path(name)
        if rel_path:
            return mc_dir / "libraries" / rel_path
        return None

    def _build_command(self):
        mc_dir = Path(self.config["mc_dir"])
        version = self.config["version"]
        username = self.config["username"]
        memory = self.config["memory"]
        java = self.config["java"]
        version_dir = self.config["version_dir"]

        self.log.emit(f"[launch] Loading version {version}")
        vj = self._load_version_with_inherits(mc_dir, version)
        self.log.emit(f"[launch] {len(vj['libraries'])} libraries after merge")
        self.log.emit(f"[launch] Main class: {vj['mainClass']}")

        classpath = []
        for lib in vj["libraries"]:
            if not rules_allow(lib.get("rules", [])):
                continue
            jar_path = self._resolve_library_path(mc_dir, lib)
            if jar_path:
                classpath.append(str(jar_path))

        client_jar = version_dir / f"{version}.jar"
        if client_jar.exists():
            classpath.append(str(client_jar))
        else:
            parent_id = self._get_root_parent(mc_dir, version)
            parent_jar = mc_dir / "versions" / parent_id / f"{parent_id}.jar"
            if parent_jar.exists():
                classpath.append(str(parent_jar))
                self.log.emit(f"[launch] Using parent version jar: {parent_id}.jar")

        sep = ";" if sys.platform == "win32" else ":"
        classpath_str = sep.join(classpath)
        natives_dir = version_dir / f"{version}-natives"
        library_dir = mc_dir / "libraries"

        replacements = {
            "${natives_directory}": str(natives_dir),
            "${classpath}": classpath_str,
            "${classpath_separator}": sep,
            "${library_directory}": str(library_dir),
            "${launcher_name}": "DHML",
            "${launcher_version}": "1.0.0",
            "${auth_player_name}": username,
            "${version_name}": version,
            "${game_directory}": str(version_dir),
            "${assets_root}": str(mc_dir / "assets"),
            "${assets_index_name}": (vj.get("assetIndex") or {}).get("id", ""),
            "${auth_uuid}": "00000000000000000000000000000001",
            "${auth_access_token}": "0",
            "${clientid}": "",
            "${auth_xuid}": "",
            "${user_type}": "legacy",
            "${version_type}": vj.get("type", "release"),
            "${resolution_width}": "854",
            "${resolution_height}": "480",
            "${user_properties}": "{}",
        }

        jvm_args = [java, f"-Xmx{memory}m"]

        if vj.get("arguments") and vj["arguments"].get("jvm"):
            for arg in vj["arguments"]["jvm"]:
                jvm_args.extend(self._resolve_arg(arg, replacements))
        else:
            jvm_args.extend([
                f"-Djava.library.path={natives_dir}",
                f"-Djna.tmpdir={natives_dir}",
                f"-Dorg.lwjgl.system.SharedLibraryExtractPath={natives_dir}",
                f"-Dio.netty.native.workdir={natives_dir}",
                "-Dminecraft.launcher.brand=DHML",
                "-Dminecraft.launcher.version=1.0.0",
                "-cp", classpath_str,
            ])

        game_args = [vj["mainClass"]]

        if vj.get("arguments") and vj["arguments"].get("game"):
            for arg in vj["arguments"]["game"]:
                game_args.extend(self._resolve_arg(arg, replacements))
        elif vj.get("minecraftArguments"):
            raw = vj["minecraftArguments"]
            for k, val in replacements.items():
                raw = raw.replace(k, val)
            try:
                game_args.extend(shlex.split(raw, posix=False))
            except:
                game_args.extend(raw.split())

        cmd = jvm_args + game_args
        cmd.insert(1, "-Dstdout.encoding=utf-8")
        cmd.insert(2, "-Dstderr.encoding=utf-8")
        return cmd

    def _resolve_arg(self, arg, replacements):
        if isinstance(arg, str):
            for k, v in replacements.items():
                arg = arg.replace(k, v)
            return [arg] if arg else []
        if isinstance(arg, dict):
            if "rules" in arg and not rules_allow(arg["rules"]):
                return []
            value = arg.get("value", [])
            if isinstance(value, str):
                value = [value]
            result = []
            for v in value:
                for k, val in replacements.items():
                    v = v.replace(k, val)
                result.append(v)
            return result
        return []

    @staticmethod
    def _ts():
        return datetime.now().strftime("%H:%M:%S")
