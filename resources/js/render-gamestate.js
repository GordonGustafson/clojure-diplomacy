"use strict";

// Only supports adding single self-closing tags at the moment.
function addSvgNode(parent, tagName, attributes) {
    const newTag = "<" + tagName + " "
          + Object.entries(attributes).map(
              ([name, value]) => name + '="' + value + '"').join(" ") + " />";
    // I would prefer to use the SVG DOM to do this, but when I tried that in
    // Chrome the element was added to the document but didn't follow the <use>
    // tag to render the desired shapes.
    parent.innerHTML += newTag;
}

function renderGamestate(parent, {"unit-positions": unitPositions,
                                   "supply-center-ownership": scOwnership,
                                   "game-time": {year, season}}) {
    const capitalizedSeason = season.charAt(0).toUpperCase() + season.slice(1);
    parent.innerHTML +=
        '<text x="5" y="20" style="font-size: 20px">' + capitalizedSeason + " "
        + year.toString() + "</text>";

    for (const [location, unit] of Object.entries(unitPositions)) {
        addSvgNode(parent, "use",
                   {"xlink:href": "#" + unit["unit-type"],
                    "class": unit["country"],
                    "transform":
                    "translate(" + unitSVGPointString(location) + ")"
                   });
    }

    for (const [country, supplyCenters] of Object.entries(scOwnership)) {
        for (const location of supplyCenters) {
            addSvgNode(parent, "use",
                       {"xlink:href": "#sc",
                        "class": country,
                        "transform":
                        "translate(" + supplyCenterSVGPointString(location)
                        + ")"
                       });
        }
    }
}

document.getElementById("mapObjectTag").addEventListener("load", function() {
    // The <object> tag 'contains' the SVG DOM, which 'contains' the <svg> tag
    // for the map.
    const rootSvg = (document.getElementById("mapObjectTag")
                     .contentDocument
                     .getElementById("mapSvgTag"));

    axios({url: "/gamestate", responseType: "json"})
        .then(response => { renderGamestate(rootSvg, response.data); })
        .catch(err => { console.log(err.message); });
});
