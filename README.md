Derive
======

Derive is a dependency tracking framework built to support
a new kind of (Functional-Reactive
Programming)[http://en.wikipedia.org/wiki/Functional_reactive_programming]
paradigm in ClojureScript.

Derive consists of a protocol and a derivation macro.
[NativeStore](http://github.com/vitalreactor/nativestore) is the first
store to participate in the tracking protocol.  A derivation function
maps an input domain to a set of memoized values that are _derived_
via one or more calls to other derivation functions and/or data stores
that support the derive protocol.  The function uses the dynamic call
graph to collect the actual dependencies of the output on the called
methods.

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

(derivation note [store id]
  

```

Testing
========

- Test: lein with-profile test test
- TDD: lein with-profile test cljsbuild auto test

Discussion
===========

Derive exports a dependency tracking protocol and calling convention
enabling, a deriving function to capture dependencies and track
changes against a specific subset of the underlying state captured
during calls to an underlying store.  Derive functions memoize their
answers and only recompute results when changes to the database would
impact the answers provided by the derivation function.

Derive is currently targeted at React/Om and our experimental library
NativeStore, although it is intended to be a more general design
pattern.  In particular, Datascript should easily be adapted to this
framework.

The other motivation for Derive is replacing the existing 'explicit
dataflow' semantics encouraged by frameworks like Hoplon and Pedestal
App and libraries like Reflex.

Om 0.1.5 State Management

- The domain model and the rendering model in practice aren't always easily co-embedded
- The representation we render from isn't always what we want to directly side-effect
- Constraining information flow to a tree is artificial
- Passing an index like Datascript down the tree is tedious.
- No built-in method to implement effective cursors for database values (yet)
- Changes to giant state models can be hard to reason about in practice
- Sometimes hard to know what is a "cursor" value and what is a standard structure

Pedestal App (0.3.0):

- Complicated dataflow model connected via path naming conventions
- Difficulty in identifying over 6-7 different files where a particular dataflow is happening - hard to trace effects to their root causes.
- Potential for deadlock with cyclic core.async router dependencies (deprecated version 0.3.0)
- Limited control over prioritization / concurrency
- Heavy-weight tree-difference model for incremental updates that generate alot of garbage for GC
- New data 'feeds forward' unless explicitly inhibited (e.g. by making updates conditional on whether a target widget is active or not, adding state management complexity to every transform.)
- Difference to giant state models can be hard to reason about in practice.
- Overly complex stateful widget architecture

Functional-Reactive Systems (e.g. Hoplon)

- 

Benefits
========

- Localize the logic specific to deriving and computing "renderable
  models" to the components that will consume them (instead of in the
  parent functions or external 'tree construction' logic)
- Parents in a render tree need to know minimal information about the
  representation required by the child.
- De-couple database schemas from the specifics of UI rendering functions
  (Use simple data structures, defined by schemas, to drive rendering and
  not care about the extra cost of computing convenience representations)
- Avoid explicit management of preconditions and/or routing. Consuming
  components specify the data they care about in the data store by
  calling a derivation function.  Components can easily share derivation
  functions.
- Automatically notify the UI when the data it depends on has changed
  when side effects are committed to the data store.
- Support dynamic programming: only derive a model when the underlying data
  has changed since the last call.  Calling derive functions multiple times
  is cheap, just like rendering a component in Om is cheap when nothing has
  changed.

TODO
=====

- Atom-based store.  Derive will be a better standlone library when
  we have a standard tree-map based data store that participates in
  the derive protocol.
- Support explicit invalidation, memoized data TTL or LRU policies on
  fixed-size DependencyTracker caches (to recover memory in
  long-running programs), and to make these policies configurable with
  metadata.
- Utilize speculative parallelism to pre-populate caches.  In a
  threaded platform this reduces to a 'touch' operation running on a
  background thread.  In Clojurescript web workers will require a
  little more machinery to keep a background DB in sync with the main
  DB and forward pre-computed state to derive functions on the main thread.


Installing
==========

You can get the latest version of Derive from Clojars.

```
[com.vitalreactor/derive "0.2.0"]
```

```
(require '[derive.core :as dr :include-macros true])
```


Tutorial
========

A derivation method is a pure function of a database reference
followed by zero or more, possibly complex, parameters.  Here is an
example based on a datascript DB value:

``` 
(defn-derived note [db note-id]
  (->> (d/q '[:find ?note :in $ ?id :where
  	          [?note :id ?id]]
			db note-id)
       ffirst
       (d/entity db)
       prepare-for-rendering))
			
```

This function returns a note object, converts it to a map, and runs a
transform function (not a derive function) which modifies its state.

Under the hood, the derived function tracks the internal dependencies
of each database read operation and associates them with the result
returned by the body.  On subsequent calls with equal parameters, the
method uses the previously captured dependencies to determine whether
more recent values of the database invalidate the prior result.  If
not, it returns the previously cached result.  This assumes that the
dependency test is much cheaper than the time/space cost of
recomputing a result.  

Derived methods can be nested, allowing top level methods to merge
the dependencies of all its children, assuming the children are also
pure functions of their arguments, and only recomputing the call tree
if some child requires an update and then, only updating the children
that need to be updated very similar to a React rendering tree.

```
Show nesting example here
```

Under the hood each database call checks for an active dependency
*tracker* implementing the IDependencyTracker protocol.  The tracker
maintains a backing cache such that subsequent calls to notes with a
possibly updated databases requires only a dependencies-changed? test
to determine whether the body needs to be recomputed, or a memoized
result returned.

The dependency tracker can be used by non-derived functions using the
macro capture-dependencies. The resulting dependency set can be
explicitly stored and a transaction watcher can extract the dependency
set implied by the latest transaction to determine whether the result of
the body would change if rerun.

```
Show Om ShouldComponentUpdate here
```

Concepts
========

Following are some of the working thoughts we are developing as part
of the Derive effort.

A user interface is a rendering of several elements of state:

1a. Domain model - An underlying domain model often synced with a
remote service and ideally indexed for fast lookups.

1b. Temporary domain model - When dealing with remote sychronization,
the UI often wants to speculatively render what the server will
eventually return (e.g. a note you are editing but isn't saved or a
temporary model of something you just sent to the server).

2. Durable interface state - A representation of the structure and
current state of the UI, possibly stored durably in local storage or
even on the server. (e.g. current tab selections, current search query)

3. Dynamic local state - This is state in the UI that is ephemeral and
can be thrown away.  We use this for form editing, animations, drag & drop,
DOM naming).

Some Clojurescript UI framework authors emphasize the idea of having
all state live in a single atom for simplicity and to support
checkpointing and backtracking.  We believe this is theoretically
interesting, but creates problem when building complex systems in
practice.

When you have a two way connection with external state, you are
limited in how far you can go back in history before you end up with a
UI state that is wrong (represents stale data).  The value of this
behavior is vastly overstated and we should not be restricted in our
choice of architectures by trying to adhere to this model.

In Om, the typical design pattern combines #1a/b and #2 into the
cursor tree at the root.  Local state is dealt with locally and
global, static state (e.g. lookup tables, multi-lingual translations)
can be passed in shared state.

The biggest problem we've found is that the design of the rendering
function depends on having the domain model transformed into a
structure suitable for rendering, including scattering operations on
updates to push the domain representation to all the locations it
needs to be rendered.  Cursors let you mix and match subtrees when
creating children, but what if you have deep nesting where some
library method happens to need some index that the intermediate
functions needn't know about (e.g. an auto-complete field)?  For
shallow hierarchies this works fine, but as UI grows more complex the
coupling of top level and lower level components becomes a non-trivial
source of incidental complexity.

Instead, we propose to separate the representation of UI state so that
the immutable specification passed to a component is simply the
minimal parameter set needed to parameterize the component.  The
content of the component's UI is pulled from a global database that is
shared across the render tree.

Of course polluting rendering methods with index lookups, or a SQL or
datalog query is messy and we haven't answered the question of how we
rerender only when something we depend on in the data store has
changed.  We add to the above concept a collection of "derivation
functions" that use parameters and a database to transform the raw
domain state into a data model more suitable for rendering.

e.g.

```
Component: Note[ID,expanded?]
Depends on: (note db id), (subject db (:subject note)) ...
```

The component Note takes an ID and a boolean indicating whether it
renders one of two UI states.  When run it calls the derivation
function (note db id) which is an immutable answer given an immutable state of
the DB.  Since the database can change, we need some way to be
notified to rerun when a change to the DB would change the answer
provided by note.

If we are able to abstract the notion of changes to a persistent data
structure, e.g. note(db,id) -> (get-in db [:model :note id]) provides
a clear dependency on any side effects at or below the [:model :note
id] path in the tree.  If we control the accessors and mutators of the
DB, it is possible to create record of what parts of the DB a call
depends on and to see if a side effect to the database would change
the value returned by note(db,id) (i.e. if the path [:model :note id]
is unchanged, then note(db,id) is unchanged..  David Dixon has written
an extension to Datascript that does this for datalog queries (index
dependencies) which turns an invalidation test into a set intersection
operation.  This is essentially smart memoization.

A derivation may depend on a primitive store or other derivations.  A
derivation registers it's dependencies with the underlying database
dependencies submitted to a hook on a dynamic variable and stores the
database value, dependency set, and return value so for any future
calls with an identical database value or a new database value that
does not match the dependency set, it just returns the prior answer.

For integration into component rendering, the render method of a
component can capture dependencies using the same mechanism, then
listen to a feed of changes to the database and trigger a re-render
iff any change matches the aggregate dependencies.  If so, it will
rerun the render, calling the derived functions which will invalidate
and call their subsidiary functions, some of which may not have been
invalidated (e.g. accesses to static data in the database).

If a component unregistered it's dependencies when unmounted, then
only the currently rendered components listen for changes and we only
call derivation functions when the database has changed which
results in lazy realization of the derived structures.

The other advantage of derived structures is that we can add prismatic
types to the derivation system so during development we have both an
exact specification of what each function returns and the ability to
perform runtime validation.  We can have domain-oriented derivations
and rendering-specific derivations.  We can go so far as to have a
derive function for every component that generates the model we would
traditionally pass into Om, but without our parents being required to
know anything about it except their their share usage of this family
of methods.

For derivations that are expensive, we can pre-compute the answer, for
example in a web worker, and background update the derivation cache so
future rendering is faster.

We can have stream derivation functions that render the latest result
of a long-running process so long as the process implements the
derivation protocol.  

Extending Om via Derive Components
==================================

We leverage the excellent work on Om/React, but choose a set of
different conventions and add some functionality to the default Om
component.

1. The Derive Component

A derive component depends on a shared database reference and one or
more parameters generated by the parent.  Rather than passing down all
data in these parameters, the render methods uses derive methods of
the database and parameters to lookup the data it needs to render (the
database as a value is static - meaning the current value of the
database).  When the DB changes, the component is marked dirty via
'om/refresh!' and will be re-rendered on the next animation cycle,
calling the invalidated derivation function in turn.

We implement this via a RenderDerived function that components
call from their default render method with a binding context that
captures dynamic dependencies just like a derive method.  

2. Pure UI or Persistent UI state.

Local state is use for memoryless-UI interactions (tab states, form
state, etc.a)

If the UI is persistent the lifecycle methods ensure that a database
model with default parameters exists and uses that to store state.  

3. Actions

We eschew core.async for reasons of latency and the general hair of
setting up little go loops in all our components.  Instead we use
callbacks passed down via Om's shared state mechanism to communicate
with parents.  Lateral communication, if needed, requires a
collaborating parent to route messages (e.g. drag and drop).  We
provide some tools for setting up this interaction in Om.

4. Organization

Typically we generate a model file which contains any prismatic schema
data about the database for sanity checking and documentation
purposes.  We also implement some core derive methods to compute
common functions of the model and action methods to perform changes to
the model, create new models, etc.

A d-component is a file with the component creation function, the
RenderDerived method implementation and any component-specific derive
methods.  Controll components will also implement a set of action
handlers that call action methods on appropriate objects.


Design
======

There are three derivation protocols that work together:

1. Dependencies - A representation of the dependency of an output on a database state and input
2. Sources - Can return dependencies and validate whether they are satisfied by a change to the source
3. DependencyTracker - Implemented by derivation functions and components, an internal protocol for managing a set of dependencies and determining if an existing response to an input is still valid.


Acknowledgements
================

This library was written by Ian Eslick and Dan Jolicoeur and benefited
from discussions with Ryan Medlin and Dom Kiva-Meyer.  We pulled ideas
from quite a few other systems, but most heavily from work on Pedestal
by Cognitect / Brenton Ashworth and the various React Clojurescript
libraries Om/Reagent/etc.



