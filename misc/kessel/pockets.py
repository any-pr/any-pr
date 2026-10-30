"""pockets -- the three cases, and nothing else.

Men and area from the encirclement orders; record_days is when the pocket
stopped existing as an organised force. planes is what was available, not what
was promised.
"""

SORTIES_DEFAULT = 0.55

# Men and area from the encirclement orders; record_days is when the pocket
# stopped existing as an organised force. planes is what was available, not
# what was promised.
POCKETS = {
    "Demyansk": dict(
        men=100_000, area=3000, ratio=1.15, planes=170, stock_days=40,
        record_days=105, sorties=0.62, relief=True,
        note="held for 105 days and relieved. The airlift worked because the "
             "front was short and the Luftwaffe still had the aircraft."),
    "Stalingrad": dict(
        men=250_000, area=1500, ratio=1.85, planes=110, stock_days=18,
        record_days=72,
        note="the airlift was promised at 500 aircraft a day and delivered a "
             "fifth of the tonnage. The pocket was cut in two on day 24."),
    "Korsun": dict(
        men=56_000, area=1600, ratio=2.10, planes=45, stock_days=10,
        record_days=16,
        note="the shortest of the three, and the one with the most men per "
             "kilometre. Density did not save it; the ratio was too high."),
}


