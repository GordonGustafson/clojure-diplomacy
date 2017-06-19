import itertools

TRANSLATE_UNIT_TYPE = {"A": "army", "F": "fleet"}

TRANSLATE_LOCATION = {
    "Adriatic Sea": "adr",
    "Aegean Sea": "aeg",
    "Albania": "alb",
    "Ankara": "ank",
    "Apulia": "apu",
    "Armenia": "arm",
    "Baltic Sea": "bal",
    "Barents Sea": "bar",
    "Belgium": "bel",
    "Berlin": "ber",
    "Black Sea": "bla",
    "Bohemia": "boh",
    "Brest": "bre",
    "Budapest": "bud",
    "Bulgaria": "bul",
    "Burgundy": "bur",
    "Clyde": "cly",
    "Constantinople": "con",
    "Denmark": "den",
    "Eastern Mediterranean": "eas",
    "Edinburgh": "edi",
    "English Channel": "eng",
    "Finland": "fin",
    "Galicia": "gal",
    "Gascony": "gas",
    "Greece": "gre",
    "Gulf of Lyon": "gol",
    "Gulf of Bothnia": "bot",
    "Helgoland Bight": "hel",
    "Holland": "hol",
    "Ionian Sea": "ion",
    "Irish Sea": "iri",
    "Kiel": "kie",
    "Liverpool": "lvp",
    "Livonia": "lvn",
    "London": "lon",
    "Marseilles": "mar",
    "Mid-Atlantic Ocean": "mid",
    "Moscow": "mos",
    "Munich": "mun",
    "Naples": "nap",
    "North Atlantic Ocean": "nat",
    "North Africa": "naf",
    "North Sea": "nth",
    "Norway": "nwy",
    "Norwegian Sea": "nrg",
    "Paris": "par",
    "Picardy": "pic",
    "Piedmont": "pie",
    "Portugal": "por",
    "Prussia": "pru",
    "Rome": "rom",
    "Ruhr": "ruh",
    "Rumania": "rum",
    "Serbia": "ser",
    "Sevastopol": "sev",
    "Silesia": "sil",
    "Skagerrak": "ska",
    "Smyrna": "smy",
    "Spain": "spa",
    "St Petersburg": "stp",
    "Sweden": "swe",
    "Syria": "syr",
    "Trieste": "tri",
    "Tunis": "tun",
    "Tuscany": "tus",
    "Tyrolia": "tyr",
    "Tyrrhenian Sea": "tyn",
    "Ukraine": "ukr",
    "Venice": "ven",
    "Vienna": "vie",
    "Wales": "wal",
    "Warsaw": "war",
    "Western Mediterranean": "wes",
    "Yorkshire": "yor"
}

TRANSLATE_ORDER_TYPE = {
    "Hold": "hold",
    "-": "attack",
    "Supports": "support",
    "Convoys": "convoy"
}

def line_is_country(line):
    return line in {"Austria:", "England:", "France:", "Germany:", "Italy:",
                    "Russia:", "Turkey:"}

def line_to_order_vector(line_list, country):
    raw_unit_type = line_list[0]
    # location is everything between the unit type and first order type.
    raw_location = (
        " ".join(itertools.takewhile(lambda x: x not in TRANSLATE_ORDER_TYPE,
                 line_list[1:])))
    raw_order_type = next(x for x in line_list if x in TRANSLATE_ORDER_TYPE)

    unit_type  = TRANSLATE_UNIT_TYPE[raw_unit_type]
    location   = TRANSLATE_LOCATION[raw_location]
    order_type = TRANSLATE_ORDER_TYPE[raw_order_type]

    base_order_vector = [country, unit_type, location, order_type]

    if order_type == "hold":
        return base_order_vector
    elif order_type == "attack":
        # destination is everything after the order type.
        raw_destination = " ".join(line_list[line_list.index("-")+1:])
        return base_order_vector + [TRANSLATE_LOCATION[raw_destination]]
    elif order_type in ["support", "convoy"]:
        # TODO: figure out how to get the country of the supported order
        print("ignoring support or convoy: not supported yet")
    else:
        assert False, "unknown order type: " + order_type

def process_lines(lines_string):
    lines = lines_string.split("\n")

    for line in lines:
        if line_is_country(line):
            # Remove trailing colon
            country = line[:-1]
            continue

        line_list = line.split()
        print(line_to_order_vector(line_list, country))


process_lines("""Germany:
F Kiel - Munich""")


# def get_unit_type(line):
#     words = line.split()
#     assert len(words) > 0, "line with no non-whitespace characters"
#     unit_type_character = words[0]
#     assert(unit_type_character in TRANSLATE_UNIT_TYPE,
#            "unknown unit type (first word in line): " + unit_type_character)
#     return TRANSLATE_UNIT_TYPE[unit_type_character]



# A Venice Hold
# A Brest - London
# F Rome Supports A Apulia - Venice
# F Belgium Supports F English Channel
# F English Channel Convoys A Brest - London

