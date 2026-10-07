"""Select providers without pulling an unrelated sampled subject in for a bundled API module."""


def select_provider(mod_id, source, candidates, manifest, already_required):
    candidates = set(candidates) - {source}
    if not candidates:
        return None
    family = manifest[source]['loader']

    def rank(name):
        row = manifest[name]
        return (name not in already_required,
                row['loader'] != family,
                mod_id.startswith('fabric-') and row['slug'] != 'fabric-api',
                row['kind'] in ('popular', 'random'),
                name)

    return min(candidates, key=rank)
