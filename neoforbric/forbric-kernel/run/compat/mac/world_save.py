"""Verify fresh world writes in both legacy and current Minecraft save layouts."""
from pathlib import Path


def saved_since(world: Path, started: float) -> bool:
    level = world / 'level.dat'
    regions = [path for path in world.rglob('*.mca') if path.parent.name == 'region']
    players = list((world / 'playerdata').glob('*.dat')) + list((world / 'players' / 'data').glob('*.dat'))
    # An unchanged region need not be rewritten on normal save; fresh player data is also a save witness.
    return (level.is_file() and level.stat().st_mtime >= started and bool(regions)
            and any(path.stat().st_mtime >= started for path in regions + players))
