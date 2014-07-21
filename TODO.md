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
x 1) Simplest possible native data store (heap indexed by :id)
x 2) Simplest possible value / range queries (simple indexing) 
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


