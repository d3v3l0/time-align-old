# time align
Life is how you spend time. This tool will help you work towards aligning what you plan and what you do.  

generated using Luminus version "2.9.11.46"

[trello board](https://trello.com/b/kGu6Xm74/time-align)

## prerequisites to dev
- [Vagrant](https://www.vagrantup.com/)
  - version 2
- [Rsync](https://www.vagrantup.com/docs/synced-folders/rsync.html)
- [VirtualBox](https://www.virtualbox.org/wiki/VirtualBox)
  - version 5.1+
- [nRepl enabled editor](https://cb.codes/what-editor-ide-to-use-for-clojure/)

## running dev
- clone repo
- run 
```
vagrant up
```
 (in a command line environment)
- run 
```
vagrant rsync-auto
```
 in the background or another terminal window
- run 
```
bash start.sh
```
 or 
```
start.bat
```
 (will open an ssh connection to vm)
- run 
```
lein run migrate
```
 (first time only)
- run 
```
./cider-deps-repl
```
 (in vm, will start an nrepl)
- connect to nrepl at port 7000 (using nrepl enabled editor)
- run in nrepl 
```
(start)
```
 (launches the server)
- run in nrepl 
```
(start-fw)
```
 (transpiles cljs and starts figwheel server)
- run in nrepl 
```
(cljs)
```
 (starts a clojurescript repl in your browser that will connect automagically when you open localhost:3000)
- run in nrepl 
```
(start-autobuild worker)
```
 to compile the web worker clojurescript files

## sequence of events
- *more iteratively* finish all cards before experience reports
- user exp reports and review
- use the review workflow and paired sessions to merge and code review
- manual soft launch
- figure out CI/CD
- start using 
- add all of the stuff to show to public
  - responsive design supporting desktop
  - analytics back end
  - analytics integration into spa
- final launch before sharing with strangers alpha SPA (2017/10/31)

... iterate new features and refactoring

- [refactor](#first-great-refactor) skeleton to be readable and maintainable without changing any functionality
- figure out mvp-b
- finish mvp-b (2018/07/01)
- figure out mvp
- finish 1.0 mvp (2018/12/31)

## launches
### alpha
- web app 
- no syncing
- no auth
### beta
- web app
- api
- with syncing 
- oauth via google
### 1.0
ios + android + webapp

## mvp-alpha
- CRUD on all entities
  - categories
  - tasks
  - periods
- intuitive display
- feedback or email list
- testing on all browsers
- local storage persistance

## first great refactor
### tests
- utils functions
- every handler
- maybe test view only helper functions

### other
- use [specter](https://github.com/nathanmarz/specter) to get rid of ugly merge stuff in handlers
- merge `:planned-periods` and `:actual-periods` in app-db
  - add back a `:type` flag
  - refactor handlers, components to work with new structure
- use spec on app-db to validate every action
- pull out all state from core and put it in view
- merge selection handlers into an entity selection handler
- all handlers have non-anon functions
- all subscriptions have non-anon functions
- custom svg defn's have name format
  - svg-(mui-)-[icon-name]
- enforced rule for subs/handlers
  - only give back individual values or lists never chunks of structure?
- all subscriptions in render code at top most levels and fed down --- Maybe not...
  - will make testing easier
  - seems too _complex_ to have individual components subscribe to things
  - then would too many components be unessentially injecting subs they dont' care about for their children?
 
## ux progress
### thoughts
- idiomatic organization
  - user case CGP grey
    - optiona A
      - category : videos
      - task     : video x (task completes when video x is done)
      - period   : edit script (needs some sort of searchable identifer meta data to say it is scripting and not editing)
    - option B
      - category : videos
      - task     : scripting videos (task never completes)
      - period   : video x
- copy option on all entity types (put in edit form and on press navigate to another edit form of new entity with something blank so it can't be submitted)
- some sort of template system (sets of category > task > periods or task > periods or just periods) that can be generated by a shortcut
- [push notifications](https://developers.google.com/web/fundamentals/engage-and-retain/push-notifications/)
  - when a playing period is planned to end
  - before a planned period starts
  - ever {user set interval} after a playing periods planned counterpart has ended
- queue needs two options (tabs)
  - queue of tasks with no stamps
  - queue of upcoming planned tasks

### actionable
...

## bugs to fix later
- firefox moving periods
- 11:59 ticker really thin

## techincal challenges to address in beta
- animations
- responsive design
  - https://github.com/Jarzka/stylefy
- routing and pretty urls
  - treat urls as _windows_ to look at teh state. The state doesn't care what window a user is peering through. Form ids are passed in and dispatched as load events from secratary handlers.

## license
MIT
Copyright 2017 Justin Good

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
