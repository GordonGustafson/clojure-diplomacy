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
    parent.innerHTML += '<g id="insertedByJS"></g>';
    const insertedByJSContainer = parent.getElementById("insertedByJS");
    console.log(insertedByJSContainer);

    const capitalizedSeason = season.charAt(0).toUpperCase() + season.slice(1);
    insertedByJSContainer.innerHTML +=
        '<text x="5" y="20" style="font-size: 20px">' + capitalizedSeason + " "
        + year.toString() + "</text>";

    for (const [location, unit] of Object.entries(unitPositions)) {
        addSvgNode(insertedByJSContainer, "use",
                   {"xlink:href": "#" + unit["unit-type"],
                    "class": unit["country"],
                    "transform":
                    "translate(" + unitSVGPointString(location) + ")"
                   });
    }

    for (const [country, supplyCenters] of Object.entries(scOwnership)) {
        for (const location of supplyCenters) {
            addSvgNode(insertedByJSContainer, "use",
                       {"xlink:href": "#sc",
                        "class": country,
                        "transform":
                        "translate(" + supplyCenterSVGPointString(location)
                        + ")"
                       });
        }
    }
}

// Clear all gamestate that was rendered directly to `parent`.
function clearRenderedGamestate(parent) {
    const insertedByJSContainer = parent.getElementById("insertedByJS");
    if (insertedByJSContainer !== null) {
        insertedByJSContainer.parentNode.removeChild(insertedByJSContainer);
    }
}

document.getElementById("mapObjectTag").addEventListener("load", function() {
    // The <object> tag 'contains' the SVG DOM, which 'contains' the <svg> tag
    // for the map.
    const rootSvg = (document.getElementById("mapObjectTag")
                     .contentDocument
                     .getElementById("mapSvgTag"));

    const renderButtons = document.getElementsByClassName("render-button");
    for (var i = 0; i < renderButtons.length; i++) {
        renderButtons[i].addEventListener("click", function(event) {
            // When the user presses a button they expect to see only the gamestate
            // for that button.
            clearRenderedGamestate(rootSvg);

            axios({url: event.target.dataset.gamestateUrl, responseType: "json"})
                .then(response => { renderGamestate(rootSvg, response.data); })
                .catch(err => { console.log(err.message); });
        });
    }
});
