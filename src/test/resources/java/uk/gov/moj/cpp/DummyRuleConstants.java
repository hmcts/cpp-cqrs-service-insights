package uk.gov.moj.cpp.dummy.command.api.accesscontrol;

import static java.util.Arrays.asList;

import java.util.List;

import com.google.common.collect.ImmutableList;

public final class DummyRuleConstants {
    // Public Constants
    public static final String DUMMY_LISTING_OFFICERS = "Dummy Listing Officers";
    public static final String DUMMY_CROWN_COURT_ADMIN = "Dummy Crown Court Admin";
    public static final String DUMMY_COURT_CLERKS = "Dummy Court Clerks";
    public static final String DUMMY_LEGAL_ADVISERS = "Dummy Legal Advisers";
    public static final String DUMMY_COURT_ADMINISTRATORS = "Dummy Court Administrators";
    public static final String DUMMY_MAGISTRATES = "Dummy Magistrates";
    public static final String DUMMY_SYSTEM_USERS = "Dummy System Users";
    public static final String DUMMY_YOTS = "Dummy Youth Offending Service Admin";
    public static final String DUMMY_CPS = "Dummy CPS";
    public static final String DUMMY_NPS = "Dummy Probation Admin";
    public static final String DUMMY_COURT_ASSOCIATE = "Dummy Court Associate";
    public static final String DUMMY_GROUP_POLICE_ADMIN = "Dummy Police Admin";
    public static final String DUMMY_GROUP_VICTIMS_WITNESS_CARE_ADMIN = "Dummy Victims & Witness Care Admin";
    public static final String DUMMY_JUDGE = "Dummy Judge";
    public static final String DUMMY_DJMC = "Dummy DJMC";
    public static final String DUMMY_DEPUTIES = "Dummy Deputies";
    public static final String DUMMY_RECORDERS = "Dummy Recorders";

    // Private Constants
    private static final String DUMMY_GROUP_LISTING_OFFICERS = "Dummy Listing Officers";
    private static final String DUMMY_GROUP_COURT_CLERKS = "Dummy Court Clerks";
    private static final String DUMMY_GROUP_LEGAL_ADVISERS = "Dummy Legal Advisers";
    private static final String DUMMY_GROUP_SYSTEM_USERS = "Dummy System Users";
    private static final String DUMMY_GROUP_COURT_ASSOCIATE = "Dummy Court Associate";
    private static final String DUMMY_GROUP_MAGISTRATES = "Dummy Magistrates";

    // Private Constructor to prevent instantiation
    private DummyRuleConstants() {
        throw new IllegalAccessError("Utility class");
    }

    // Dummy Methods
    public static String[] dummyArchivePermissions() {
        return new String[]{
                createObjectBuilder().add(OBJECT, DUMMY_COTR).add(ACTION, DUMMY_COTR_COURTS_ACCESS).build().toString(),
        };
    }

    public static List<String> dummyGetUpdateNowsStatusActionGroups() {
        return asList(
                DUMMY_GROUP_LISTING_OFFICERS,
                DUMMY_GROUP_COURT_CLERKS,
                DUMMY_GROUP_LEGAL_ADVISERS,
                DUMMY_GROUP_SYSTEM_USERS,
                DUMMY_GROUP_COURT_ASSOCIATE,
                DUMMY_GROUP_MAGISTRATES
        );
    }

    public static List<String> dummyGetPoliceResultsForDefendantGroups() {
        return asList(
                DUMMY_GROUP_LISTING_OFFICERS,
                DUMMY_GROUP_COURT_CLERKS,
                DUMMY_GROUP_LEGAL_ADVISERS,
                DUMMY_GROUP_SYSTEM_USERS,
                DUMMY_GROUP_COURT_ASSOCIATE
        );
    }

    public static List<String> dummyGetCreateResultsActionGroups() {
        return ImmutableList.of(DUMMY_GROUP_SYSTEM_USERS);
    }

    public static List<String> dummyGetTrackResultsActionGroups() {
        return ImmutableList.of(
                DUMMY_GROUP_SYSTEM_USERS,
                DUMMY_GROUP_MAGISTRATES
        );
    }

    // Assuming these methods exist in the original class
    private static ObjectBuilder createObjectBuilder() {
        // Dummy implementation
        return new ObjectBuilder();
    }

    // Dummy placeholders for undefined variables/methods in original class
    private static final String OBJECT = "Dummy_OBJECT";
    private static final String COTR = "Dummy_COTR";
    private static final String ACTION = "Dummy_ACTION";
    private static final String COTR_COURTS_ACCESS = "Dummy_COTR_COURTS_ACCESS";

    // Dummy ObjectBuilder class to mimic original functionality
    private static class ObjectBuilder {
        private String object;
        private String action;

        public ObjectBuilder add(String key, String value) {
            if ("OBJECT".equals(key)) {
                this.object = value;
            } else if ("ACTION".equals(key)) {
                this.action = value;
            }
            return this;
        }

        public ObjectBuilder build() {
            // Dummy build method
            return this;
        }

        @Override
        public String toString() {
            return "ObjectBuilder{object='" + object + "', action='" + action + "'}";
        }
    }
}
