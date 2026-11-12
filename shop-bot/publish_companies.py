"""Apply the listing policy to existing companies and verify the Android catalog."""
import json
import time

import acquiring
import index
from places_sources import ExistingDatabaseSnapshot


def reconcile(storage, private):
    for attempt in range(5):
        snapshot = ExistingDatabaseSnapshot(private, index.DB_KEY)
        before = json.loads(snapshot.raw)
        if not before.get("bot_username") or "sellers" not in before:
            raise RuntimeError("Expected the existing seller database")
        bot = index.Bot(storage, index.OutboxTelegram(), data=snapshot)
        try:
            if bot.dirty or bot.catalog_dirty:
                bot.save()
            if not bot.catalog_dirty:
                # A previous attempt may have committed the migration before the catalog upload failed.
                bot.write_catalog()
        except index.Conflict:
            time.sleep(.2 * (attempt + 1))
            continue

        raw, revision = private.get_tagged(index.DB_KEY)
        sellers = json.loads(raw)["sellers"]
        public = json.loads(storage.get_tagged("catalog.json")[0])
        expected = {s["id"]: bool(s.get("partner")) for s in sellers.values()
                    if s["status"] == "approved" and not acquiring.unpaid(s)}
        published = {s["id"]: bool(s["partner"]) for s in public["sellers"]}
        waiting = sum(s["status"] == "awaiting_payment" for s in sellers.values())
        if (private.get_tagged(index.DB_KEY)[1] == revision and expected == published
                and (not waiting or acquiring.listing_fee_enabled())):
            recovered = sum(s.get("status") == "awaiting_payment" and sid in published
                            for sid, s in before["sellers"].items())
            return {"published": len(published), "waiting_for_listing_payment": waiting,
                    "recovered_in_this_pass": recovered, "public_catalog_matches": True}
        time.sleep(.2 * (attempt + 1))
    raise RuntimeError("Could not verify publication against a stable seller database")


if __name__ == "__main__":
    print(json.dumps(reconcile(index.make_storage(), index.make_storage(bucket=index.DATA_BUCKET))))
