# Clojure Diplomacy

An adjudicator for the game of [Diplomacy](https://en.wikipedia.org/wiki/Diplomacy_(game)).
This program supports determining the set of outcomes of a set of diplomacy moves.
Unlike other Diplomacy adjudicators, this project will provide explanations for why each order succeeded or failed, like so:

INPUT:
```
Austria:
A Serbia - Budapest
A Vienna - Budapest

Russia:
A Galicia Supports A Serbia - Budapest

Turkey:
A Bulgaria - Serbia
```

OUTPUT:
```
Austria:
A Serbia - Budapest = ADVANCES.         Non-interfering moves: A Vienna - Budapest attacked same destination.
A Vienna - Budapest = DOES NOT ADVANCE. Interfering moves: A Serbia - Budapest attacked same destination.

Russia:
A Galicia Supports A Serbia - Budapest = SUPPORT GIVEN.

Turkey:
A Bulgaria - Serbia. = ADVANCES.
```

# TODOs

**TODO**: pass all of the core [Diplomacy Adjudicator Test Cases](http://web.inter.nl.net/users/L.B.Kruijswijk/). Currently 106 out of 122 test cases are passing.

**TODO**: summarize design and implementation.

**TODO**: provide text-based wrapper that gives output like the above (currently this program only operates on Clojure data structures).

**TODO**: host test case viewer on the web.
