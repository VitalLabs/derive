Derive
======

Derive is a kind of React library for data.  It simplifies the
derivation of data from an immutable specification coupled to a
client-side index / database.

Initial support is targeted for use with a front-end like React/Om and
Datascript, although it is intended to be a more general design
pattern.  This library emerged from frustrations we encountered using
prior approaches to managing front-end UI construction in Clojurescript.


Pedestal 0.3.0:

- Complicated dataflow model connected via path naming conventions
- Difficulty in identifying over 6-7 different files where a particular dataflow is happening - hard to trace effects to their root causes.
- Potential for deadlock with cyclic core.async router dependencies.
- Limited control over prioritization / concurrency
- Heavy-weight tree-difference model for incremental updates that generate alot of garbage for GC
- New data 'feeds forward' unless explicitly inhibited (e.g. by making updates conditional on whether a target widget is active or not, adding state management complexity to every transform.)
- Difference to giant state models can be hard to reason about in practice.
- Overly complex stateful widget architecture


Om 0.1.5 State Management

- The domain model and the rendering model in practice aren't always easily co-embedded
- The representation we render from isn't always what we want to directly side-effect
- Constraining information flow to a tree is an artificial constraint
- Passing an index down the tree becomes tedious.
- No built-in method to implement effective cursors for database values (yet)
- Changes to giant state models can be hard to reason about in practice
- Sometimes hard to know what is a cursor value and what is not


Concept
=======

A user interface is a rendering of several elements of state:

1a. Domain model - An underlying domain model often synced with a
remote service and ideally indexed for fast lookups.

1b. Temporary domain model - When dealing with remote sychronization,
the UI often wants to speculatively render what the server will
eventually return (e.g. a note you are editing but isn't saved or a
temporary model of somethign you just sent to the server).

2. Durable interface state - A representation of the structure and
current state of the UI, possibly stored durably in local storage or
even on the server. (e.g. current tab selections, current search query)

3. Dynamic local state - This is state in the UI that is ephemeral and
can be thrown away.  We use this for form editing, animations, drag & drop,
DOM naming).

Some UI framework authors, including Nolan, emphasize the idea of
having all state live in a single atom so we can checkpoint and
backtrack, etc.  However, when you have a two way connection with
external state, you are limited in how far you can go back in history
before you end up with a UI state that is wrong (represents stale
data).  I think the value of this behavior is vastly overstated and we
should not be restricted in our choice of architectures by trying to
adhere to this model.

In Om, the typical design pattern combines #1a/b and #2 into the
cursor tree at the root.  Local state is dealt with locally and
global, static state (e.g. lookup tables, multi-lingual translations)
can be passed in shared state.

The biggest problem we've found is that the design of the rendering
function depends on having the domain model transformed into a
structure suitable for rendering, including scatter operations to
distribute structure over the domain representation.  Now cursors let
you mix and match in the renderers to create subtrees, but what if you
have deep nesting where some library method happens to need some index
that the intermediate functions needn't know about (e.g. an
auto-complete field)?  For shallow hierarchies this works fine, but as
UI grows more complex the coupling of top level and lower level
components becomes a source of complexity.

Instead, we propose to separate the representation of UI state so that
the immutable specification passed to a component is simply the
minimal parameter set needed to decide, for example, which note in a
list of notes to render.  The content of the thing to render is pulled
from a global index that is shared across the render tree.

Of course polluting rendering methods with index lookups, or a SQL or
datalog query is messy and we haven't answered the question of how we
rerender only when something we depend on has changed..  So we add to
this notion a hierarchy of "derivation functions" that transform a
database state plus parameters into a persistent data model more
suitable for rendering. 

```
Component: Note(ID,expanded?)
Depends on: note(db,id)
```

Here the component Node takes and ID and a boolean indicating whether
it renders in one of two states and when run, calls the derivation
note(db,id) which is an immutable answer given an immutable state of
the DB.  Since the database can change, we need some way to be
notified to rerun when a change to the DB would change the answer
provided by note.

If we are able to abstract the notion of changes to a persistent data
structure, e.g. note(db,id) -> (get-in db [:model :note id]) provides
a clear dependency on any side effects to the [:model :note id] path.
If we restrict the accessors and mutators of the DB, it is possible to
maintain a record of what parts of the DB a primitive call depends on
and to test deltas on that db state to see if it would change the
value returned by note(db,id).  David Dixon has written an extension
to Datascript that does this for datalog queries (index dependencies)
which turn an invalidation test to a set intersection query.

A derivation may depend on additional derivations and so on.  A
derivation captures dependencies submitted to a hook on a dynamic
variable and stores the database value, dependency set, and return
value so for any future calls with an identical database value or a
new database value that does not match the dependency set, it just
returns the prior answer.  Smart memoization.

For integration into component rendering, the render method of a
component can capture dependencies using the same mechanism, then
listen to a feed of changes to the database and trigger a rerender iff
any change matches the aggregate dependencies.  If so, it will rerun
the render, calling the derived functions which will invalidate and
call their subsidiary functions, some of which may not have been
invalidated (e.g. accesses to static data in the database).

Only the currently rendered components listen for changes, so we only
call derivation functions when the database has changed which results
in lazy realization of the derived structures.

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

Usage
=====

Design
======

There are three derivation protocols that work together:

1. Dependencies - A representation of the dependency of an output on a database state and input
2. Sources - Can return dependencies and validate whether they are satisfied by a change to the source
3. DependencyTracker - Implemented by derivation functions and components, an internal protocol for managing a set of dependencies and determining if an existing response to an input is still valid.


Benefits
========

- Localize the logic specific to deriving and computing "render
  models" to the components that will consume them (instead of in the
  parent functions or external 'tree construction' logic)
- Avoid explicit management of preconditions and/or routing, consuming
  components specify the data they want from the client state by
  calling a derivation method.  Components can easily share derivation
  methods.
- De-couple database schemas from the specifics of UI rendering functions
  (Use simple data structures, defined by schemas, to drive rendering and
  not to care about the extra cost of computing convenience representations)
- Provide cheap comparisons of whether a derived model needs to be updated
  given one or more changes to the database.
- Dynamic programming, only derive a resulting model once unless
  preconditions have changed, but feel free to call the generating
  methods many times.

Longer term goals

- Export a protocol such that any database with appropriate
  capabilities can participate in the derive protocol.
- Support explicit invalidation, memoized data TTL or LRU policies on
  fixed-size DependencyTracker caches (to recover memory in
  long-running programs), and to make these policies configurable with
  metadata
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

``` clojure
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

Workflow
=================

1) Start 'lein repl'
   - Creates an nrepl endpoint for Emacs
   - Creates a socket at localhost:9000 for the browser

2) Start 'lein figwheel'
   - Auto asset reloading

3) Connect emacs to cider port exported by 'lein repl'

4) Run '(browser-repl)'

5) Load localhost:3449/index.html into a browser


Orchestra Workflow Goals
========================

1) Live editing JS/CSS
   - e.g. widgets file for Amelia and us in dev mode
   - e.g. dev-noww full site editing of CSS/JS
   - Ability to connect a REPL to this environment
   
2) Headless repl environment with the full code
   - Connect from Emacs
   - Meta-. navigation
   - Developing new derive widgets
   - C-x C-e eval optional
   

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

TODO Spike 1 - Semantics
================
(DEBUGGING) 1) Simplest possible native data store (heap indexed by :id) 
(DEBUGGING) 2) Simplest possible value / range queries (simple indexing) 
3) Pipe all models from service layer to native store
4) Build derive functions that always recompute; force om models to always re-render
5) Develop derive functions for current mobile timeline (simple functions only)
6) Try executing mobile timeline using only non-pedestal models on a branch
   - Gives us a performance baseline
   - Compare to an om update that never renders (just to get performance gain possibility)

TODO Spike 2 - Dependencies
===============
1) Add transaction notification to native store
2) Derive functions as IFn objects that cache results given params
3) Capture query dependencies up derive call stack
4) Derive functions associate store, params, deps, and result
5) Derive functions listen to store txns passed into it
6) Invalidate cache as appropriate (use store dependency API)
7) Develop Om extension to only re-render if any dependencies were invalidated

TODO Spike 3 - Performance
===============
1) Native store indexing
2) Think carefully about copying
3) Develop conventions around manipulation of native objects
4) Think about how to lazy/eager policies for derive fns
   (e.g. simple derive functions could update cache from txn without query)


Other Desired Features
================
Inhibit side effects to heap objects outside transaction!
Secondary indexing allowing map/sort/filter (without copying?)
Simple schemas to identify relational links among objects
Create "Reference" values on import that, when derefed, return a copy of the target object
Turn on 'always copy' for debugging purposes


Acknowledgements
================

This library was written by Ian Eslick and Dan Jolicoeur and benefited
from discussions with Ryan Medlin and Dom Kiva-Meyer.  We pulled ideas
from quite a few other systems, but most heavily from work on Pedestal
by Cognitect / Brenton Ashworth and the various React Clojurescript
libraries Om/Reagent/etc.



