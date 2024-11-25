package uk.gov.moj.cpp.dummy.query.api.accesscontrol;

import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.stream.Stream;

/**
 * Dummy UserGroupType enum for testing purposes.
 */
public enum DummyGroupType {

    DUMMY_ADMIN("Dummy Admin"),
    DUMMY_ADVISERS("Dummy Advisers"),
    DUMMY_MANAGER("Dummy Manager"),
    DUMMY_SUPPORT("Dummy Support"),
    DUMMY_OPERATOR("Dummy Operator"),
    DUMMY_CARE_ADMIN("Dummy Care Admin"),
    DUMMY_SERVICE_ADMIN("Dummy Service Admin"),
    DUMMY_AID_ADMIN("Dummy Aid Admin"),
    DUMMY_CLERK("Dummy Clerk"),
    DUMMY_ASSOCIATE("Dummy Associate"),
    DUMMY_SYSTEM_USERS("Dummy System Users"),
    DUMMY_CONSUMERS("Dummy Consumers"),
    DUMMY_COURT_ADMINS("Dummy Court Admins"),
    DUMMY_ADMINISTRATORS("Dummy Administrators"),
    DUMMY_OFFICERS("Dummy Officers"),
    DUMMY_JUDICIARY("Dummy Judiciary"),
    DUMMY_MAGISTRATES("Dummy Magistrates");

    private final String name;

    UserGroupType(String name) {
        this.name = name;
    }

    public static List<String> personDetailsGroups() {
        return Stream.of(DUMMY_MANAGER, DUMMY_SUPPORT, DUMMY_OPERATOR, DUMMY_CARE_ADMIN, DUMMY_SERVICE_ADMIN, DUMMY_AID_ADMIN, DUMMY_CLERK, DUMMY_ASSOCIATE)
                .map(UserGroupType::getName).collect(toList());
    }

    public static List<String> hearingDetailsGroups() {
        return Stream.of(DUMMY_MANAGER, DUMMY_SUPPORT, DUMMY_OPERATOR, DUMMY_CARE_ADMIN, DUMMY_SERVICE_ADMIN, DUMMY_AID_ADMIN, DUMMY_CLERK, DUMMY_ASSOCIATE)
                .map(UserGroupType::getName).collect(toList());
    }

    public static List<String> resultsDetailsGroups() {
        return Stream.of(DUMMY_ADMIN, DUMMY_ADVISERS, DUMMY_MANAGER, DUMMY_SUPPORT, DUMMY_OPERATOR, DUMMY_CARE_ADMIN, DUMMY_SERVICE_ADMIN, DUMMY_AID_ADMIN, DUMMY_CLERK, DUMMY_ASSOCIATE, DUMMY_SYSTEM_USERS, DUMMY_MAGISTRATES)
                .map(UserGroupType::getName).collect(toList());
    }

    public static List<String> resultsSummaryGroups() {
        return Stream.of(DUMMY_MANAGER, DUMMY_SUPPORT, DUMMY_OPERATOR, DUMMY_CARE_ADMIN, DUMMY_SERVICE_ADMIN, DUMMY_AID_ADMIN, DUMMY_CLERK, DUMMY_ASSOCIATE)
                .map(UserGroupType::getName).collect(toList());
    }

    public static List<String> defendantsTrackingStatusGroups() {
        return Stream.of(DUMMY_ADMINISTRATORS, DUMMY_COURT_ADMINS, DUMMY_OFFICERS, DUMMY_JUDICIARY, DUMMY_ADVISERS, DUMMY_CLERK, DUMMY_ASSOCIATE)
                .map(UserGroupType::getName).collect(toList());
    }

    @Override
    public String toString() {
        return name;
    }

    public String getName() {
        return this.name;
    }
}
