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

// Returns the child of `parent` that should be used for inserting content
// rendered via javascript, creating it if it deosn't exist. `parent` must be an
// element in an SVG DOM.
function getChildElementForGeneratedContent(parent) {
    if (parent.getElementById("insertedByJS") === null) {
        parent.innerHTML += '<g id="insertedByJS"></g>';
    }
    return parent.getElementById("insertedByJS");
}

function renderGamestate(parent, {"unit-positions": unitPositions,
                                  "supply-center-ownership": scOwnership,
                                  "game-time": {year, season}}) {
    const renderTarget = getChildElementForGeneratedContent(parent);

    const capitalizedSeason = season.charAt(0).toUpperCase() + season.slice(1);
    renderTarget.innerHTML +=
        '<text x="5" y="20" style="font-size: 20px">' + capitalizedSeason + " "
        + year.toString() + "</text>";

    for (const [location, unit] of Object.entries(unitPositions)) {
        addSvgNode(renderTarget, "use",
                   {"xlink:href": "#" + unit["unit-type"],
                    "class": unit["country"],
                    "transform":
                    "translate(" + unitSVGPointString(location) + ")"
                   });
    }

    for (const [country, supplyCenters] of Object.entries(scOwnership)) {
        for (const location of supplyCenters) {
            addSvgNode(renderTarget, "use",
                       {"xlink:href": "#sc",
                        "class": country,
                        "transform":
                        "translate(" + supplyCenterSVGPointString(location)
                        + ")"
                       });
        }
    }
}

function renderResolutionResults(parent, resolutionResults) {
    const renderTarget = getChildElementForGeneratedContent(parent);
    for (const [order, conflictJudgments] of resolutionResults) {
        const {"country": country,
               "unit-type": unitType,
               "location": location,
               "order-type": orderType,
               "destination": destination = null} = order;

        if (orderType === "attack") {
            let pathString =
                "M" + unitSVGPointString(location) +
                "L" + unitSVGPointString(destination);
            addSvgNode(renderTarget, "path",
                       {"class": country + "-attack",
                        "d": pathString
                       });
        }
    }
}


// Clear all gamestate that was rendered directly to `parent`.
function clearRenderedGamestate(parent) {
    // If it doesn't already exist, `getChildElementForGeneratedContent` will
    // create the child element for generated content, which we will immediately
    // remove. We could avoid the unnecessary insert and remove in that case,
    // but it's not worth it.
    const renderTarget = getChildElementForGeneratedContent(parent);
    parent.removeChild(renderTarget);
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

            axios({url: event.target.dataset.ordersPhaseTestUrl, responseType: "json"})
                .then(response => {
                    const d = response.data;
                    const gamestateBefore = {
                        "unit-positions":          d["unit-positions-before"],
                        "supply-center-ownership": d["supply-center-ownership-before"],
                        "game-time":               d["game-time-before"]
                    }
                    renderGamestate(rootSvg, gamestateBefore);

                    renderResolutionResults(rootSvg, d["resolution-results"]);
                })
                .catch(err => { console.log(err.message); });
        });
    }
});
