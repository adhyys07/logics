package com.redstonelogics.selection;

import com.redstonelogics.core.Pos;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SelectionStore {
    public record Selection(Pos first, Pos second) {

        public boolean complete(){
            return first != null && second != null;
        }

        public long volume() {
            if (!complete()) {
                return 0;
            }
            return (long) (Math.abs(first.x() - second.x()) + 1)
                    * (Math.abs(first.y() - second.y()) + 1)
                    * (Math.abs(first.z() - second.z()) + 1);
        }
    }

    private static final Map<UUID, Selection> SELECTIONS = new ConcurrentHashMap<>();

    private SelectionStore() {
    }

     public static Selection get(UUID player) {
        return SELECTIONS.getOrDefault(player, new Selection(null, null));
    }

    public static Selection setFirst(UUID player, Pos pos) {
        return SELECTIONS.compute(player,
                (k, v) -> new Selection(pos, v == null ? null : v.second()));
    }

    public static Selection setSecond(UUID player, Pos pos) {
        return SELECTIONS.compute(player,
                (k, v) -> new Selection(v == null ? null : v.first(), pos));
    }

    public static void clear(UUID player) {
        SELECTIONS.remove(player);
    }
}
