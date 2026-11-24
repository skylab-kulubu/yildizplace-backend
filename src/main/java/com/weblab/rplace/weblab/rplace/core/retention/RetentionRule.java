package com.weblab.rplace.weblab.rplace.core.retention;

/**
 * One rule of the periodic destruction run. The dry run's count and apply's batch are
 * built from the same WHERE clause (predicate), so they select the same rows. The
 * predicate leaves out the rows the rule has already handled, so a second run changes
 * nothing.
 *
 * <p>The predicate may use :eventEnd (EVENT_ENDED_AT) and the three cutoffs :ipCutoff
 * (90 days ago), :accountCutoff (a year ago) and :banCutoff (two years ago); cutoff
 * names the rule's own one, which the record shows.
 *
 * @param name    the rule's name in the log and the record
 * @param version a new version when the rule selects or changes something else
 * @param table   the table it changes
 * @param alias   the table's alias in the predicate
 * @param action  delete the row, or empty its personal fields (set)
 * @param set     the SET clause of an emptying rule; null for a deleting one
 * @param cutoff  :ipCutoff, :accountCutoff or :banCutoff
 */
public record RetentionRule(String name, int version, String table, String alias, Action action, String set,
                            Cutoff cutoff, String predicate) {

    public enum Action {
        DELETE("delete"),
        EMPTY("empty");

        private final String value;

        Action(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }

    /** ADR-0062's three periods for Place, each counted from the event's end. */
    public enum Cutoff {
        // IP addresses, login and Turnstile tokens: event + 90 days.
        IP("ipCutoff"),
        // The account (school address): event + 1 year.
        ACCOUNT("accountCutoff"),
        // A banned account (not an IP): event + 2 years.
        BAN("banCutoff");

        private final String parameter;

        Cutoff(String parameter) {
            this.parameter = parameter;
        }

        public String parameter() {
            return parameter;
        }
    }

    String countSql() {
        return "SELECT count(*) FROM " + table + " " + alias + " WHERE " + predicate;
    }

    /** At most :batch rows; rows another transaction holds wait for the next run. */
    String batchSql() {
        String selected = "SELECT " + alias + ".id FROM " + table + " " + alias + " WHERE " + predicate
                + " ORDER BY " + alias + ".id LIMIT :batch FOR UPDATE OF " + alias + " SKIP LOCKED";
        return switch (action) {
            case DELETE -> "DELETE FROM " + table + " WHERE id IN (" + selected + ")";
            case EMPTY -> "UPDATE " + table + " SET " + set + " WHERE id IN (" + selected + ")";
        };
    }
}
