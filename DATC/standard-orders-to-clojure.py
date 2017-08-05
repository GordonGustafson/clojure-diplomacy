# Explanation:
# DATC generally uses 'standard notation' as described here:
# https://everything2.com/title/Standard+Diplomacy+notation
# Some specifics are different, like DATC using '-' instead of '->' for attacks.
#
# DATC examples orders:
# A Venice Hold
# A Brest - London
# F Rome Supports A Apulia - Venice
# F Belgium Supports F English Channel
# F English Channel Convoys A Brest - London
#
# Each text line of input is split on whitespace into a list of words early in
# the processing. The term 'line' *always* refers to these lists of words (lists
# of strings), never the raw text lines of input (strings).

import sys

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
    "Bulgaria(ec)": "bul-ec",
    "Bulgaria(sc)": "bul-sc",
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
    "Spain(nc)": "spa-nc",
    "Spain(sc)": "spa-sc",
    "St Petersburg": "stp",
    "St Petersburg(nc)": "stp-nc",
    "St Petersburg(sc)": "stp-sc",
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
    # This is a set of one-tuples, which looks pretty terrible in Python.
    return line in {("Austria:",), ("England:",), ("France:",), ("Germany:",),
                    ("Italy:",), ("Russia:",), ("Turkey:",)}

def line_to_order_vector(line, raw_location_to_country_map):
    # If there's no order_type in the list, return the index one past the end of
    # the list (where the order type *should* be).
    index_of_order_type = next((idx for (idx, value) in enumerate(line)
                                if value in TRANSLATE_ORDER_TYPE),
                               len(line))

    raw_unit_type = line[0]
    # location is everything between the unit type and first order type.
    raw_location = " ".join(line[1:index_of_order_type])
    # If there's no order type, assume it's a hold. This is useful because the
    # Clojure code requires that supports specify the full order they are
    # supporting, with an order type of either attack or hold, and supporting
    # stationary units is written like "F Belgium Supports A Holland" in the
    # DATC (without "Hold" specified in the supported order).
    raw_order_type = ("Hold" if index_of_order_type == len(line)
                      else line[index_of_order_type])
    rest_of_line = line[index_of_order_type+1:]

    country    = raw_location_to_country_map[raw_location]
    unit_type  = TRANSLATE_UNIT_TYPE[raw_unit_type]
    location   = TRANSLATE_LOCATION[raw_location]
    order_type = TRANSLATE_ORDER_TYPE[raw_order_type]

    base_order_vector = (country, unit_type, location, order_type)

    if order_type == "hold":
        return base_order_vector
    elif order_type == "attack":
        # `rest_of_line` contains the destination and an optional ("via",
        # "Convoy") suffix.
        # TODO: Currently the order representation in the Clojure code doesn't
        # permit specifying "via Convoy", so we ignore it here.
        is_via_convoy = (len(rest_of_line) > 2
                         and rest_of_line[-2:] == ("via", "Convoy"))
        raw_destination = (" ".join(rest_of_line[:-2]) if is_via_convoy
                           else " ".join(rest_of_line))
        return base_order_vector + (TRANSLATE_LOCATION[raw_destination],)
    elif order_type in ["support", "convoy"]:
        assisted_order_vector = (
            line_to_order_vector(rest_of_line, raw_location_to_country_map))
        return base_order_vector + assisted_order_vector
    else:
        assert False, "unknown order type: " + order_type

def standard_notation_to_order_vectors(standard_notation_string):
    raw_lines = standard_notation_string.split("\n")
    lines = [tuple(line.split()) for line in raw_lines
             if line != ""]

    # loop to build raw_location_to_country_map
    raw_location_to_country_map = {}
    country = None
    for line in lines:
        if line_is_country(line):
            raw_country = line[0]
            # Remove trailing colon. The Clojure code uses lower-case keywords
            # for countries.
            country = raw_country[:-1].lower()
        else:
            # Deduplicating this would be nice, but not sure if worth it.
            index_of_order_type = next(idx for (idx, value) in enumerate(line)
                                       if value in TRANSLATE_ORDER_TYPE)
            raw_location = " ".join(line[1:index_of_order_type])
            assert country is not None, "Country not specified before order"
            raw_location_to_country_map[raw_location] = country

    return [line_to_order_vector(line, raw_location_to_country_map)
            for line in lines
            if not line_is_country(line)]

def order_vector_to_clojure_order_vector(order_vector):
    contents = " ".join(":" + keyword for keyword in order_vector)
    return "[" + contents + "]"

def order_vectors_to_clojure_orders_map(order_vectors):
    clojure_order_vectors = [order_vector_to_clojure_order_vector(ov)
                             for ov in order_vectors]
    clojure_pairs = [cov + " #{[:interfered? :interferer :rule]}"
                     for cov in clojure_order_vectors]
    contents = "\n".join(clojure_pairs)
    return "{" + contents + "}"


order_vectors = standard_notation_to_order_vectors(sys.stdin.read())

print(order_vectors_to_clojure_orders_map(order_vectors))
