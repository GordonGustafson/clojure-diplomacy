diplomacy.maps
==============

## :colocation-sets

Locations with north and south coasts need to be special-cased somehow.
There are two options:

1. Make them three different locations and add logic to detect 'collisions' in
   multiple 'colocated' locations.
2. Make them one location and add logic for moving in and out of them.

I chose option 1 because I think it adds less complexity and is easier to
understand.


## :location-accessibility and :edge-accessibility

(I will use the words 'node' and 'location' interchangeably)

Armies and fleets can only make certain moves from one location to another. To
implement this we can store accessibility metadata, what types of units can make
which moves, along the map's nodes, edges or both.

### storing :edge-accessibility only

In the classic map, a node can be occupied by an army if has an edge that can be
traversed by an army. However, the Ancient Mediterranean map has islands (land
nodes with no adjacent land nodes), and we need to distinguish them from the sea
to determine where convoys can bring an army. Similarly, in the classic map a
node can be occupied by a fleet if it is a sea node or adjacent to a sea node.
However, the Ancient Mediterranean map has a special rule for Memphis, which can
be occupied by fleets even though it is NOT adjacent to a sea node. Thus, we
must store location-accessibility.

### storing :location-accessibility only

We do not *need* to store per-edge accessibility. Armies can traverse all edges
between land nodes. Fleets can traverse all edges between nodes sea nodes and
between sea and land nodes, and can traverse edges between land nodes IF both
land nodes border the same sea node. However, I think 'precomputing'
edge-accessibility leads to a simpler implementation.

testing orders phase
====================
Our implementation breaks the orders phase down into three steps:
1. Validation - making sure the orders given are permitted in the current game
   state.
2. Resolution - feeding the orders into a resolution engine written with
   core.logic that determines which orders fail and succeed.
3. Post-resolution - Updating the unit positions and determining what choices
   are available in the retreat phase.

We only test the `diplomacy.order-pipeline/orders-phase` function, since its
output contains the result of every stage.

To make it easier to write concise tests that check one piece of functionality,
`test_expansion.clj` contains functions for expanding shorthand notation, and
optionally filling in optional parts of the test (parts that are redundant when
every unit is given a valid order, and parts that are unused in the orders
phase, like :game-time and :supply-center-ownership).
