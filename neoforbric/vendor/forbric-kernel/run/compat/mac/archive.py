"""Declared mod identities and transitive requirements, including nested runtime bundles."""
import io
import json
import tomllib
import zipfile


def jar_ids(data, depth=0):
    if depth > 8:
        raise ValueError("nested dependency depth exceeded")
    provided, required = set(), set()
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        names = set(archive.namelist())
        children = set()
        if 'fabric.mod.json' in names:
            # Fabric Loader's own reader accepts a raw control character inside a string (a pasted description).
            metadata = json.loads(archive.read('fabric.mod.json'), strict=False)
            provided.add(metadata.get('id'))
            provided.update(metadata.get('provides') or [])
            required.update((metadata.get('depends') or {}).keys())
            children.update(row['file'] for row in metadata.get('jars', []))
        for filename in ('META-INF/mods.toml', 'META-INF/neoforge.mods.toml'):
            if filename not in names:
                continue
            metadata = tomllib.loads(archive.read(filename).decode('utf-8'))
            provided.update(row.get('modId') for row in metadata.get('mods', []))
            for dependencies in metadata.get('dependencies', {}).values():
                for row in dependencies if isinstance(dependencies, list) else []:
                    kind = str(row.get('type', 'required' if row.get('mandatory') else 'optional')).lower()
                    if kind == 'required' or row.get('mandatory') is True:
                        required.add(row.get('modId'))
        if 'META-INF/jarjar/metadata.json' in names:
            metadata = json.loads(archive.read('META-INF/jarjar/metadata.json'))
            children.update(row['path'] for row in metadata.get('jars', []))
        for filename in sorted(children):
            if filename not in names:
                raise ValueError('declared nested jar missing: ' + filename)
            nested_provided, nested_required = jar_ids(archive.read(filename), depth + 1)
            provided.update(nested_provided)
            required.update(nested_required)
    provided.discard(None)
    required.discard(None)
    return provided, required - provided


def environment(data):
    """The side a Fabric jar declares it runs on ('*', 'client' or 'server'); '*' when it declares none.

    Fabric Loader's default is both sides, and a jar without fabric.mod.json declares nothing either way.
    """
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        if 'fabric.mod.json' not in archive.namelist():
            return '*'
        return json.loads(archive.read('fabric.mod.json'), strict=False).get('environment') or '*'
