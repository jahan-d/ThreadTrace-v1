-- ============================================================
-- ThreadTrace V2: Enforce Database-Level Event Ledger Immutability
-- PostgreSQL 17
--
-- Business & Compliance Requirement:
-- The event ledger (lot_events) is an append-only audit trail.
-- Once recorded, events must never be updated or deleted under
-- any circumstances.
-- ============================================================

CREATE OR REPLACE FUNCTION prevent_lot_event_modification()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'UPDATE' THEN
        RAISE EXCEPTION 'Lot events are immutable: UPDATE operation is strictly prohibited on table lot_events'
            USING ERRCODE = 'integrity_constraint_violation';
    ELSIF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Lot events are immutable: DELETE operation is strictly prohibited on table lot_events'
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_lot_events_immutable
BEFORE UPDATE OR DELETE ON lot_events
FOR EACH ROW
EXECUTE FUNCTION prevent_lot_event_modification();
