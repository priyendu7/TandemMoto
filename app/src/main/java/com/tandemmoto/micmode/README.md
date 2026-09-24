# micmode

Mic-mode state machine: pause -> both phones enter mic mode; resume -> both
exit. Single state synced over the command channel, not two independently
triggered local states, to avoid the "one phone thinks on, other thinks off"
race condition (see docs/DEVELOPMENT_PLAN.md §5 risks).

Self-mute is deliberately *not* part of this shared state: it's a per-phone flag
that only gates the local mic, changed only by the user, remembered across
restarts, and announced to the partner ("Partner muted").

Development plan: Phase 4-5.
