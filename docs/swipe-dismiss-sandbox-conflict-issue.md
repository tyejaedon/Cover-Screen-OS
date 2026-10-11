# Issue: Swipe-to-Dismiss Gesture Sandbox Conflict Resolution

## Summary
Resolve the swipe-to-dismiss gesture conflict in the cover-screen notification flow so users can dismiss notifications without accidentally triggering overlay or app-grid gestures.

## Problem
The notification list on the cover-screen overlay is using swipe interactions in a surface that also handles pager gestures, app selection, and ambient navigation. When the swipe lifecycle overlaps with the sandboxed overlay gesture area, the system can interpret a single drag as both a dismissal and a page or widget interaction.

This makes dismiss actions feel unreliable and can cancel valid notification actions or cause the user to trigger the wrong motion while on the lock/pager surface.

## Scope
- fix swipe-to-dismiss recognition for notification cards in the overlay
- separate notification swipe gestures from app-grid/pager gesture handling
- prevent swipe conflicts with the secure cover-surface sandbox
- keep dismissal actions usable while the overlay is locked, in the app grid, and during search/widget modes
- add a clear-all notification action if it is not already present in the notification panel flow

## Current repo findings
- the notification listener service exists and supports dismissing individual notification keys
- the launcher overlay reads notification state and renders notification cards
- there is no clear-all notification action currently found in the app source for the overlay flow

## Milestone
Notification overlay polish

## Labels
- notification
- overlay
- gesture
- UX
- accessibility

## Notes
This issue should close when the swipe-to-dismiss flow is stable, the conflict is removed, and the notification panel either includes a clear-all action or explicitly documents why it is intentionally omitted.

