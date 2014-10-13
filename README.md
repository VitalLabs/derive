Derive
======

Derive is a dependency tracking framework that supports practical
[Reactive Programming](http://en.wikipedia.org/wiki/Reactive_programming) in
ClojureScript.

Derive functions behave just like a memoizing function with automatic,
behind-the-scenes cache invalidation and upstream notification when
dependencies change.  Dependencies are captured during the dynamic
extent of the function execution for each unique set of parameters.
Dependencies are produced by other derive functions and from one or
more stores that implement derive's dependency tracking protocol.

The built-in SimpleStore (derive.simple) serves as a working example
and [NativeStore](http://github.com/vitalreactor/nativestore) is the
first complete store to participate in the tracking protocol and is
focused on efficient indexing and manipulation of native objects.

It should be possible to adapt Dave Dixon's work on Datascript to
support the derive model.

![Derive Architecture](https://docs.google.com/drawings/d/1lfblr7F8co5pXOmaeZ50Q1iqnplnjU3ynM3KaRPOqls/pub?w=953&amp;h=876)

Benefits
========

- Localize the logic specific to deriving and computing "renderable
  models" to the components that will consume them (instead of in the
  parent functions or external 'tree construction' logic)
- Derive functions replace the need to compute and manage
  'intermediate state' between your server and your render functions
- Automatically notify the UI when the latest data it depended on
  changed after side effects to the source data are committed to the
  data store.
- Parents in a render tree need minimal information about the
  representation manipulated by the child.  The data passed in
  is more like function call parameters than a state model.
- Avoid explicit specification of dataflow; the function call context
  within the body of a render loop will capture all dependencies.
- Derivation functions become the api to the database.
- Support dynamic programming: only derive a model when the underlying data
  has changed since the last call.  Calling derive functions multiple times
  is cheap, just like rendering a component in Om is cheap when nothing has
  changed.


Installing
==========

Add derive to your dependencies.

```clj
:dependencies [[com.vitalreactor/derive "0.2.0"]]
```

Then require it inside your code

```clj
(ns foo.bar
  (:require [derive.core :as d :include-macros true]))

(defnd note [store id]
  (transform-note (store id)))

(defnd 

```

Integration with Om/React
=========================

![Om/React Architecture](https://docs.google.com/drawings/d/11iQQ2r6XMKZ03LkAcRmIjSCMMrllc77q_oiLYYt8nzg/pub?w=960&amp;h=720)

Testing
========

- Test: lein with-profile test test
- TDD: lein with-profile test cljsbuild auto test


TODO
=====

- Proper tutorial leveraging SimpleStore
- Support explicit invalidation, memoized data TTL or LRU policies on
  fixed-size DependencyTracker caches (to recover memory in
  long-running programs), and to make these policies configurable with
  metadata.
- Utilize speculative parallelism to pre-populate caches.  In a
  threaded platform this reduces to a 'touch' operation running on a
  background thread.  In Clojurescript web workers will require a
  little more machinery to keep a background DB in sync with the main
  DB and forward pre-computed state to derive functions on the main thread.



Acknowledgements
================

This library was written by Ian Eslick and Dan Jolicoeur and benefited
from discussions with Ryan Medlin and Dom Kiva-Meyer.  We pulled ideas
from quite a few other systems, but most heavily from work on Pedestal
by Cognitect / Brenton Ashworth and the various React Clojurescript
libraries Om/Reagent/etc.



