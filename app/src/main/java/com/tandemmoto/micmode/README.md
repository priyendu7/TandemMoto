# micmode

Mic-mode state machine: pause -> both phones enter mic mode; resume -> both
exit. Single state synced over the command channel, not two independently
triggered local states, to avoid the "one phone thinks on, other thinks off"
race condition (see docs/DEVELOPMENT_PLAN.md §5 risks).

Development plan: Phase 4-5.
