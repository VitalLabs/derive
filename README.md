Derive
======

Derive is a kind of React library for data.  It simplifies the
derivation of data from an immutable specification coupled to a
client-side index / database.

Initial support is targeted for use with a front-end like React/Om and
Datascript, although it is intended to be a more general design
pattern.

Goals
=====

Derive is an attempt to solve a set of problems that are brought into
specific relief by the resource and programmatic constraints of user
interface design and implementation, particularly in the mobile
domain.

- Create and reuse derivative representations of raw server-side state
  consumed over REST APIs, often intermixed with rendering-specific
  client concerns with minimal performance impact.
- De-couple database schemas from the specifics of UI rendering functions
  (Use simple data structures, defined by schemas, to drive rendering and
  not to care about the extra cost of computing convenience representations)
- Localize the logic specific to deriving and computing "render
  models" to the components that will consume them.
- Provide cheap comparisons of whether a derived model needs to be updated
  given one or more changes to the database.
- Dynamic programming, only derive a resulting model once unless
  preconditions have changed, but call the generating methods many times.
- Avoid explicit management of preconditions and routing, consumers specify
  the data they want by calling a derivation method.

Longer term goals

- Export a protocol such that any database with appropriate features (e.g.
  a large persistent map) can participate in the derive protocol.
- Support explicit invalidation, memoized data TTL or LRU policies on
  fixed-size caches (to recover memory in long-running programs), and to
  make these policies configurable with metadata
- Utilize speculative parallelism to pre-populate caches.  In a
  threaded platform this reduces to a 'touch' operation running on a
  background thread.  In Clojurescript web workers will require a
  little more machinery to keep a background DB in sync with the main
  DB and to forward computed updates.

Installing
==========

You can get the latest version of Derive from Clojars.

```
[com.vitalreactor/derive "0.1.0"]
```

Usage Examples
==============

```clojure
(require '[derive :as dr :include-macros true])

(defn-derive 

Design
======

```clojure
(defn-derived note [db note-id]
  (->> (d/q '[:find ?note :in $ ?id :where
  	          [?note :id ?id]]
			db note-id)
			ffirst
			(d/entity @conn)))
```

A derivation method is a pure function of one or more database
references followed by zero or more, possibly complex, parameters.

Under the hood, the derived function tracks dependencies implied by
each database operation and associates them with the result returned
by the body.  On subsequent calls with equal parameters, the method
uses the previously captured dependencies to determine whether more
recent values of the database invalidate the prior result.  If not, it
returns the cached result.  This assumes that the dependency test is
much cheaper than the time/space cost of recomputing a result.

Derived methods can be nested, allowing top level methods to merge
the dependencies of all its children, assuming the children are also
pure functions of their arguments, and only recomputing the call tree
if some child requires an update and then, only updating the children
that need to be updated very similar to a React rendering tree.

Under the hood each database call uses the IDerive protocol against
a that populates

a backing cache such that subsequent calls to notes with a possibly
updated databases requires only a dependencies-changed? test to
determine whether the body needs to be recomputed, or a memoized
result can be returned.

Calls to database query methods export a representation of the
dependencies that query has on the database state.

React Integration
=================

In our usage case, a component file contains:

- Input schema specification
- Component definition
- One or more auxiliary render methods
- One or more derivation functions called by render functions
- A set of exported actions (also documented by schemas)

On client applications there can be latency induced by the use of
setInterval and setTimeout that encouraged us to provide some
utilities around a callback dictionary that passed down the render
tree in shared out-of-band state along with the database value.

Higher level components have more system awareness and are able to
determine where and how to update system state by plugging into 


Acknowledgements
================

This library emerged specifically from internal discussions between
Ian Eslick, Dan Jolicoeur, and Ryan Medlin as well as some
brainstorming with Dom Kiva-Meyer.  We benefitted heavily from work on
previous systems that targeted similar problems, namely Pedestal by
Cognitect / Brenton Ashworth and the various React Clojurescript
libraries Om/Reagent/etc.



