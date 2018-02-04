"use strict";

// All this data for where to render units and supply centers in the SVG is
// completely coupled to the particular svg we're using for the map.
//
// It would have been nice if we could use the XML entities for the positions of
// units and supply centers defined in the SVG, but Chrome didn't seem to apply
// them to the SVG inserted via javascript (whether done via the SVG DOM or
// innerHTML).
const LOCATION_TO_UNIT_RENDER_POSITION = {
    "swi": [219,376],
    "adr": [296,441],
    "aeg": [403,524],
    "alb": [339,469],
    "ank": [500,460],
    "apu": [302,472],
    "arm": [576,456],
    "bal": [323,250],
    "bar": [445,41],
    "bel": [197,317],
    "ber": [279,283],
    "bla": [484,420],
    "boh": [289,336],
    "bre": [125,334],
    "bud": [353,378],
    "bul": [395,443],
    "bul-ec": [410,440],
    "bul-sc": [399,462],
    "bur": [191,360],
    "cly": [139,188],
    "con": [439,473],
    "den": [256,245],
    "eas": [474,546],
    "edi": [157,210],
    "eng": [119,307],
    "fin": [385,143],
    "gal": [377,343],
    "gas": [137,388],
    "gre": [366,515],
    "gol": [180,444],
    "bot": [348,199],
    "hel": [226,252],
    "hol": [205,297],
    "ion": [324,540],
    "iri": [90,276],
    "kie": [243,295],
    "lvp": [142,241],
    "lvn": [382,245],
    "lon": [162,281],
    "mar": [184,402],
    "mao": [23,355],
    "mos": [505,226],
    "mun": [243,347],
    "nap": [299,505],
    "nao": [65,140],
    "naf": [100,536],
    "nth": [204,215],
    "nor": [264,160],
    "nwg": [220,90],
    "par": [162,346],
    "pic": [168,319],
    "pie": [220,399],
    "por": [34,417],
    "pru": [315,283],
    "rom": [264,452],
    "ruh": [223,320],
    "rum": [415,405],
    "ser": [351,438],
    "sev": [515,330],
    "sil": [304,314],
    "ska": [260,212],
    "smy": [490,505],
    "spa": [64,439],
    "spa-nc": [80,404],
    "spa-sc": [52,475],
    "stp": [500,140],
    "stp-nc": [472,122],
    "stp-sc": [418,205],
    "swe": [315,140],
    "syr": [570,520],
    "tri": [305,412],
    "tun": [212,542],
    "tus": [247,430],
    "tyr": [277,378],
    "tys": [246,483],
    "ukr": [427,327],
    "ven": [250,408],
    "vie": [314,360],
    "wal": [125,285],
    "war": [361,315],
    "wes": [140,492],
    "yor": [161,254],
}

const LOCATION_TO_SUPPLY_CENTER_RENDER_POSITION = {
    "ank": [482,469],
    "bel": [186,305],
    "ber": [281,298],
    "bre": [106,322],
    "bud": [326,376],
    "bul": [377,444],
    "con": [429,460],
    "den": [272,252],
    "edi": [154,219],
    "gre": [378,507],
    "hol": [205,284],
    "kie": [254,278],
    "lvp": [144,257],
    "lon": [162,290],
    "mar": [186,417],
    "mos": [481,234],
    "mun": [258,359],
    "nap": [278,469],
    "nor": [270,187],
    "par": [173,334],
    "por": [15,434],
    "rom": [252,443],
    "rum": [402,413],
    "ser": [343,419],
    "sev": [483,396],
    "smy": [424,502],
    "spa": [80,432],
    // Should we add "stp-nc" and "stp-sc" here?
    "stp": [418,187],
    "swe": [323,196],
    "tri": [284,396],
    "tun": [220,529],
    "ven": [261,397],
    "vie": [301,363],
    "war": [346,302],
}

function pointToString(point) {
    return point.map(n => n.toString()).join(",");
}

// Takes any location in the classic diplomacy map and returns the point in the
// SVG diplomacy map where a unit at that location should appear. Returned
// string is of the form "x,y".
function unitSVGPointString(location) {
    return pointToString(LOCATION_TO_UNIT_RENDER_POSITION[location]);
}

// Takes any location that has a supply center in the classic diplomacy map and
// returns the point in the SVG diplomacy map where the supply center at that
// location should appear. Returned string is of the form "x,y".
function supplyCenterSVGPointString(location) {
    return pointToString(LOCATION_TO_SUPPLY_CENTER_RENDER_POSITION[location]);
}

function midpoint([a1, a2], [b1, b2]) {
    return [(a1+b1)/2, (a2+b2)/2];
}
