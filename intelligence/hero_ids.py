"""Canonical hero-id helpers shared by Web data and configuration lookups."""


def normalize_hero_id(hero_id):
    """Map season hero ids (13xxxx/14xxxx) to their 10xxxx base id."""
    try:
        value = int(hero_id)
    except (TypeError, ValueError):
        return 0
    if 130000 <= value <= 149999:
        return value - 30000 if value < 140000 else value - 40000
    return value
