#!/usr/bin/env python3
"""Patch Dynamic Torches 5.4's old entity-type predicate for Minecraft 26.2 into a separate jar."""
import argparse
import json
from pathlib import Path
import zipfile

RESOURCE = 'data/dt/predicate/luminousitems.json'


def patched_predicate(raw):
    data = json.loads(raw)
    if data.get('condition') != 'minecraft:entity_properties' or data.get('entity') != 'this':
        raise ValueError('unexpected luminousitems condition')
    predicate = data.get('predicate', {})
    if predicate.get('entity_type') == 'minecraft:item' and 'type' not in predicate:
        return raw
    if predicate.get('type') != 'minecraft:item' or 'entity_type' in predicate:
        raise ValueError('unexpected entity type: refusing to change its meaning')
    predicate['entity_type'] = predicate.pop('type')
    return (json.dumps(data, ensure_ascii=False, indent=2) + '\n').encode('utf-8')


def patch(source, destination):
    source, destination = Path(source), Path(destination)
    if source.resolve() == destination.resolve():
        raise ValueError('output must differ from the original jar; keep the original as a backup')
    with zipfile.ZipFile(source) as archive:
        metadata = json.loads(archive.read('fabric.mod.json'))
        if metadata.get('id') != 'mr_dynamic_torches' or metadata.get('version') != '5.4':
            raise ValueError('this repair is for Dynamic Torches 5.4 only')
        replacement = patched_predicate(archive.read(RESOURCE))
        destination.parent.mkdir(parents=True, exist_ok=True)
        temporary = destination.with_suffix(destination.suffix + '.tmp')
        try:
            with zipfile.ZipFile(temporary, 'w') as output:
                output.comment = archive.comment
                for entry in archive.infolist():
                    output.writestr(entry, replacement if entry.filename == RESOURCE else archive.read(entry))
            with zipfile.ZipFile(temporary) as output:
                if output.testzip() is not None:
                    raise ValueError('output archive failed its CRC check')
            temporary.replace(destination)
        finally:
            if temporary.exists(): temporary.unlink()
    return destination


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source', type=Path)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    print(patch(args.source, args.output))


if __name__ == '__main__':
    main()
