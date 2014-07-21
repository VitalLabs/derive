Derive
======

One of the current trends in Functional UI design in the Clojurescript
community is exploits immutable state to enable highly efficient
updating of a user interface in response to changing state.  One
typical implementation pattern consists of a nested set of maps stored
in a singular Atom.  The benefits of this organization are many: UI
can watch a single point of state that can be easily checkpointed,
restored, etc.

Derive is a dependency tracking framework that provides an alternative
strategy for propogating state change into the UI that complements
existing strategies.  In short, Derive systematizes the derivation of
new data from a core transactional store which may not provide the
typicaly value comparison semantics of Clojurescript's persistent data
structures.  The key to derive is for the store value to expose a
protocol that enables a deriving function to track the dependencies it
has on the underlying state and to only recompute a result when
changes to the database would impact the answers provided by the
derivation function.  

Initial support is targeted for use with a front-end like React/Om and
our own experimental library NativeStore, although it is intended to
be a more general design pattern.  In particular, Datascript should
easily be adapted into this framework.

The other motivation for Derive is replacing the existing 'explicit
dataflow' semantics encouraged by frameworks like Hoplon and Pedestal
and libraries like Reflex.

Pedestal 0.3.0:

- Complicated dataflow model connected via path naming conventions
- Difficulty in identifying over 6-7 different files where a particular dataflow is happening - hard to trace effects to their root causes.
- Potential for deadlock with cyclic core.async router dependencies (deprecated version 0.3.0)
- Limited control over prioritization / concurrency
- Heavy-weight tree-difference model for incremental updates that generate alot of garbage for GC
- New data 'feeds forward' unless explicitly inhibited (e.g. by making updates conditional on whether a target widget is active or not, adding state management complexity to every transform.)
- Difference to giant state models can be hard to reason about in practice.
- Overly complex stateful widget architecture

Om 0.1.5 State Management

- The domain model and the rendering model in practice aren't always easily co-embedded
- The representation we render from isn't always what we want to directly side-effect
- Constraining information flow to a tree is artificial
- Passing an index like Datascript down the tree becomes tedious.
- No built-in method to implement effective cursors for database values (yet)
- Changes to giant state models can be hard to reason about in practice
- Sometimes hard to know what is a cursor value and what is not


Derive Benefits
===============

- Localize the logic specific to deriving and computing "renderable
  models" to the components that will consume them (instead of in the
  parent functions or external 'tree construction' logic)
- Avoid explicit management of preconditions and/or routing, consuming
  components specify the data they want from the domain state by
  referencing a derivation method.  Components can easily share derivation
  methods.
- De-couple database schemas from the specifics of UI rendering functions
  (Use simple data structures, defined by schemas, to drive rendering and
  not to care about the extra cost of computing convenience representations)
- Provide cheap comparisons as to whether a derived model needs to be recomputed,
  and the respective UI update, given side effects to the database.
- Support dynamic programming, only derive a resulting model once unless
  preconditions have changed, but it is cheap to call the generating
  methods many times.

Longer term goals

- Support explicit invalidation, memoized data TTL or LRU policies on
  fixed-size DependencyTracker caches (to recover memory in
  long-running programs), and to make these policies configurable with
  metadata
- Utilize speculative parallelism to pre-populate caches.  In a
  threaded platform this reduces to a 'touch' operation running on a
  background thread.  In Clojurescript web workers will require a
  little more machinery to keep a background DB in sync with the main
  DB and forward pre-computed state to derive functions on the main thread.


Installing
==========

You can get the latest version of Derive from Clojars.

```
[com.vitalreactor/derive "0.1.0"]
```

```
(require '[derive.core :as dr :include-macros true])
```


Tutorial
========

A derivation method is a pure function of one or more database
references followed by zero or more, possibly complex, parameters.
Here is an example based on a datascript DB value:

``` clojure
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

NativeStore, the Native Type, Cursors, and References
=====================================================

NativeStore provides a simple, explicitly indexed in-memory database
for managing native objects.  It is efficient, transactional, and
supports emulation of immutability through dependency tracking.  It
directly supports the ITrackDependencies interface required by Derive
methods to invalidate results that are likely to change given the
prior transaction.  The store does not currently maintain historical
versions or a transaction log, but that is under consideration for
future versions to support efficient snapshotting and restore of
system state.

All objects added to the store must be of type Native.  Native objects
support the usual countable, assocable, transient, and lookup
interfaces.  Standard assoc operations return copies.

Native objects stored in the store are marked read-only on insertion.
After a copy operation, these objects can be freely mutated by
downstream code.  The original object can be updated by calling
insert! directly or within a transaction body (to batch up changes and
ensure database consistency during a transaction).

We provide a Reference type to simplify modeling state with graph-like
or relational structures.  When accessing a Native attribute, if the
returned value implements the IReference protocol, the reference is
unpacked and the value returned is the read-only underlying native
instance referenced.  This is implemented as a deftype which maintains
a reference to the store value and the root ID to lookup ("id").

The state of the store is modelled as a heap indexed by the value in
the "id" slot with zero or more indices on values of the objects
accessed via NativeCursor values.  The NativeCursor type currently is only
compatible with the reducers library via IReduce.  To do things like
sorting, a reducer chain should return a fresh array.  The derive.dfns
namespace contains various helper methods for working with Cursors,
reducers, and native arrays.

The immutable abstraction is not currently as rigorously enforced as
it is in other parts of the Clojure ecosystem (Datomic, etc).  Some
things to note:

- Native objects may not be mutated outside transactions unless they are
  first copied.  This is to inhibit side effects to the DB outside of transaction
  functions and insert!/remove!.
- It is currently an error to mutate a database object within a transaction function
  without calling insert! to update the indices.
- Object identity is retained across object boundaries, but code
  should not currently depend identity or '=' holding across transactions.
- Database cursors are also invalidated by transactions.  There are currently
  no checks for cursor invalidation, so it is best to use them in environments
  where cursors have finite extent.
- Conventions are only enforced if you stick to clojure interfaces.
  aget, aset, and direct member access bypass reference resolution,
  read-only enforcemenet, etc.  However, if you kow what you are doing
  you can still access values using constructs like (aget native
  "field") and get near-optimal read performance on natives.

- Use NativeStore responsibly.  We emphasized maximal performance,
  decent flexiblity, with only a modicum of safety.  Safety emerges
  from proper use of convention.

All these tradeoffs may be reconsidered in future revisions of the
NativeStore and NativeStore should be considered at an Alpha level of
completeness.


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


Design
======

There are three derivation protocols that work together:

1. Dependencies - A representation of the dependency of an output on a database state and input
2. Sources - Can return dependencies and validate whether they are satisfied by a change to the source
3. DependencyTracker - Implemented by derivation functions and components, an internal protocol for managing a set of dependencies and determining if an existing response to an input is still valid.


Testing Workflow
=================

1) Start 'lein repl'
   - Creates an nrepl endpoint for Emacs
   - Creates a socket at localhost:9000 for the browser

2) Start 'lein figwheel'
   - Auto asset reloading

3) Connect emacs to cider port exported by 'lein repl'

4) Run '(browser-repl)'

5) Load localhost:3449/index.html into a browser


Acknowledgements
================

This library was written by Ian Eslick and Dan Jolicoeur and benefited
from discussions with Ryan Medlin and Dom Kiva-Meyer.  We pulled ideas
from quite a few other systems, but most heavily from work on Pedestal
by Cognitect / Brenton Ashworth and the various React Clojurescript
libraries Om/Reagent/etc.



