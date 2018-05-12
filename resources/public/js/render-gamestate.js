"use strict";

// Returns the child of `parent` that should be used for inserting content
// rendered via javascript, creating it if it doesn't exist. `parent` must be an
// element in an SVG DOM.
function getChildElementForGeneratedContent(parent) {
    if (parent.getElementById("insertedByJS") === null) {
        parent.innerHTML += '<g id="insertedByJS"></g>';
    }
    return parent.getElementById("insertedByJS");
}

// Clear all gamestate that was rendered directly to `parent`.
function clearGeneratedContent(parent) {
    // If it doesn't already exist, `getChildElementForGeneratedContent` will
    // create the child element for generated content, which we will immediately
    // remove. We could avoid the unnecessary insert and remove in that case,
    // but it's not worth it.
    const renderTarget = getChildElementForGeneratedContent(parent);
    parent.removeChild(renderTarget);
}

// Only supports adding single self-closing tags at the moment.
function addSvgNode(renderTarget, tagName, attributes) {
    const newTag = "<" + tagName + " "
          + Object.entries(attributes).map(
              ([name, value]) => name + '="' + value + '"').join(" ") + " />";
    // I would prefer to use the SVG DOM to do this, but when I tried that in
    // Chrome the element was added to the document but didn't follow the <use>
    // tag to render the desired shapes.
    renderTarget.innerHTML += newTag;
}

// TODO: break this into a separate Javascript file?
function displayTestMetadata(longName, summary, explanation) {
    document.getElementById("test-long-name").textContent = longName;
    document.getElementById("test-summary").textContent = summary;
    document.getElementById("test-explanation").innerHTML = explanation;
}

function renderGamestate(renderTarget, {"unit-positions": unitPositions,
                                        "supply-center-ownership": scOwnership,
                                        "game-time": {year, season}}) {
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

function renderAttack(renderTarget, country, location, destination) {
    const pathString =
        "M" + unitSVGPointString(location) +
        "L" + unitSVGPointString(destination);
    // Add a border around the attack line to make it more distinguished from
    // whatever's behind it. The 'border' is actually a thicker line placed
    // under the attack line such that the 'border' shows around the edges of
    // the attack line. The SVG 1.1 standard states "Subsequent elements are
    // painted on top of previously painted elements", so the 'border' must be
    // drawn before the attack line.
    addSvgNode(renderTarget, "path",
               {"class": "order-border",
                "d": pathString,
               });
    // The attack line with an arrow.
    addSvgNode(renderTarget, "path",
               {"class": country + "-order",
                "d": pathString,
                "marker-end": "url(#attack-arrow)"
               });
}

function renderSupport(renderTarget, country, location, assistedOrder) {
    const {"location": supportedFrom,
           "order-type": supportedOrderType,
           "destination": supportedTo = null} = assistedOrder;
    let pathString = "";
    if (supportedOrderType === "hold") {
        pathString =
            "M" + unitSVGPointString(location) +
            "L" + unitSVGPointString(supportedFrom);
    } else if (supportedOrderType === "attack") {
        const startPoint = LOCATION_TO_UNIT_RENDER_POSITION[location];
        const endPoint = midpoint(LOCATION_TO_UNIT_RENDER_POSITION[supportedFrom],
                                  LOCATION_TO_UNIT_RENDER_POSITION[supportedTo]);
        const controlPoint1 = midpoint(startPoint, endPoint);
        const controlPoint2 = LOCATION_TO_UNIT_RENDER_POSITION[supportedFrom];
        pathString =
            "M" + pointToString(startPoint) +
            "C" + pointToString(controlPoint1) +
            "," + pointToString(controlPoint2) +
            "," + pointToString(endPoint);
    } else {
        console.assert(false, "Invalid assisted order type: " + supportedOrderType)
    }

    addSvgNode(renderTarget, "path",
               {"class": "order-border",
                "d": pathString,
               });
    addSvgNode(renderTarget, "path",
               {"class": country + "-order, support",
                "d": pathString,
                "marker-end": "url(#support-arrow)"
               });
}

function renderOrders(renderTarget, orders) {
    for (const order of orders) {
        const {"country": country,
               "unit-type": unitType,
               "location": location,
               "order-type": orderType,
               "destination": destination = null,
               "assisted-order": assistedOrder = null} = order;

        if (orderType === "attack") {
            renderAttack(renderTarget, country, location, destination);
        } else if (orderType === "support") {
            renderSupport(renderTarget, country, location, assistedOrder);
        }
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
            clearGeneratedContent(rootSvg);
            const renderTarget = getChildElementForGeneratedContent(rootSvg);

            axios({url: event.target.dataset.ordersPhaseTestUrl, responseType: "json"})
                .then(response => {
                    const d = response.data;
                    displayTestMetadata(d["long-name"],
                                        d["summary"],
                                        d["explanation"]);

                    const gamestateBefore = {
                        "unit-positions":          d["unit-positions-before"],
                        "supply-center-ownership": d["supply-center-ownership-before"],
                        "game-time":               d["game-time-before"]
                    }
                    renderGamestate(renderTarget, gamestateBefore);

                    // Use the keys of "validation-results" to get the orders
                    // actually given, regardless of whether they were valid or
                    // not.
                    const orders = d["validation-results"].map(([order, res]) => order);
                    renderOrders(renderTarget, orders);
                });
        });
    }
    // This is a quick hack that makes the page flicker horribly when it's
    // loaded, but it works for now.
    if (renderButtons.length > 0) {
        renderButtons[0].click();
    }
});
