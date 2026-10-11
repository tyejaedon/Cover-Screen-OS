# Plan: Swipe-to-Dismiss Gesture Sandbox Conflict Resolution

## Goal
Make notification swipe-to-dismiss reliable on the cover-screen overlay without interfering with app-grid paging, widget gestures, or other sandboxed interactions.

## Problem to solve
Notification cards are inside an overlay that also manages horizontal pager swipes, search input, and accessory interactions. The gesture system needs clear boundaries so a swipe intended to dismiss a notification does not get consumed by a broader overlay drag or page gesture.

## Scope
- fix gesture priority for notification cards
- keep dismiss actions within the notification panel sandbox
- maintain safe interaction when the overlay is in locked, app-grid, and search states
- add a clear-all notifications action if the flow does not already expose one

## Proposed work

### 1. Audit the notification panel gesture flow
- locate the notification card composables and swipe state handling
- confirm which parent gestures are intercepting notification drags
- verify the active overlay pager, search, and widget gestures are not competing with the dismiss gesture

### 2. Define the dismissal boundary
- prefer per-card swipe gestures over container-level pager gestures when the drag starts within a notification tile
- lock the gesture to the notification item until the swipe is resolved
- avoid triggering page changes or app selection while a notification card is actively dragging

### 3. Add safe dismissal behavior
- only dismiss after the user passes the clear swipe threshold
- keep an escape/back-state when the user drags back below threshold
- preserve contact area and haptic feedback so the gesture feels consistent on a cover screen

### 4. Handle the overlay sandbox rules
- make sure the notification drag cannot pass into the surrounding pager area unless the user intentionally starts outside the card
- keep the dismiss action available while the system is locked and while the app grid is visible
- validate search and widget states so they do not steal the gesture

### 5. Add clear-all notification support
- if there is no existing clear-all action, add it to the notification panel header or footer actions
- wire it to the notification listener API to remove all active notifications in a controlled way
- confirm the action is safe for the lock-screen sandbox and does not bypass expected user confirmation

## Acceptance criteria
- swiping a notification card dismisses only that notification when the swipe target is valid
- pager gestures do not steal notification dismissal gestures
- accidental swipe conflicts are reduced to a practical minimum
- the panel either has a clear-all action or the reason it is omitted is documented in code or design notes
- the notification flow remains stable when the user is locked, scrolling the app grid, or using search

## Implementation notes
- prefer small, focused changes in the overlay notification card and gesture layer
- keep the app's existing notification listener service as the source of truth for dismissal actions
- use the same plain-language naming and structure already used in the repo

## Related issue
Closes #<issue-number>

