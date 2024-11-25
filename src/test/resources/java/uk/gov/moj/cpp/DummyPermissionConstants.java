package uk.gov.moj.cpp.dummy.command.api.accesscontrol;

import static javax.json.Json.createObjectBuilder;

/**
 * Dummy PermissionConstants class for testing purposes.
 */
public class DummyPermissionConstants {
    // Dummy permission constants
    static final String DUMMY_DEFENCE_ACCESS = "dummy-defence-access";
    static final String DUMMY_COURTS_ACCESS = "dummy-courts-access";
    static final String DUMMY_COTR = "DUMMY_COTR";
    static final String DUMMY_OBJECT = "dummy-object";
    static final String DUMMY_ACTION = "dummy-action";
    private static final String DUMMY_CREATE_ACTION = "DummyCreate";
    private static final String DUMMY_OBJECT_CASE = "DummyCase";


    /**
     * Creates dummy archive permissions.
     *
     * @return An array of dummy archive permission JSON strings.
     */
    public static String[] dummyPermissions() {
        return new String[]{
                createObjectBuilder().add(DUMMY_OBJECT, DUMMY_COTR).add(DUMMY_ACTION, DUMMY_COURTS_ACCESS).build().toString(),
        };
    }

}
