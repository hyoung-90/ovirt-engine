package org.ovirt.engine.core.dao;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ovirt.engine.core.common.businessentities.UserProfile;
import org.ovirt.engine.core.common.businessentities.UserProfileProperty;
import org.ovirt.engine.core.common.businessentities.UserSshKey;
import org.ovirt.engine.core.compat.Guid;
import org.springframework.dao.DataIntegrityViolationException;

public class UserProfileDaoTest extends BaseDaoTestCase<UserProfileDao> {
    private static final String existingLoginName = "userportal2@testportal.redhat.com@testportal.redhat.com";
    private static final Guid existingUserId = new Guid("9bf7c640-b620-456f-a550-0348f366544a");

    private UserProfileProperty existingSshProperty;
    private UserProfileProperty existingJsonProperty;

    @BeforeEach
    @Override
    public void setUp() throws Exception {
        super.setUp();

        // PostgresqlDataTypeFactory provided by DbUnit does not support JSONB
        // no "content" attribute is provided in the fixture.xml (will default to null)
        // non-null content is tested update/insert tests
        // note that deserializing null is also a valid test case that should not be
        // dropped even when JSONB will be fully supported

        existingSshProperty = UserProfileProperty.builder()
                .withDefaultSshProp()
                .withPropertyId(new Guid("39df6903-7eda-46c3-bd54-50be69a0d512"))
                .withContent("")
                .withName("key1")
                .withUserId(existingUserId)
                .build();

        existingJsonProperty = UserProfileProperty.builder()
                .withTypeJson()
                .withPropertyId(new Guid("39df6903-7eda-46c3-bd54-50be69a0d513"))
                .withContent("null")
                .withName("key2")
                .withUserId(existingUserId)
                .build();
    }

    @Test
    void testGetPropertyForNull() {
        assertThat(dao.get(null)).isNull();
    }

    @Test
    void testGetPropertyForEmptyId() {
        assertThat(dao.get(Guid.Empty)).isNull();
    }

    @Test
    public void testGetSshProperty() {
        UserProfileProperty result = dao.get(existingSshProperty.getPropertyId());

        assertThat(result).isEqualTo(existingSshProperty);
        assertThat(result).hasSameClassAs(new UserProfileProperty());
    }

    @Test
    public void testGetJsonProperty() {
        UserProfileProperty result = dao.get(existingJsonProperty.getPropertyId());

        assertThat(result).isEqualTo(existingJsonProperty);
        assertThat(result).hasSameClassAs(new UserProfileProperty());
    }

    @Test
    public void testGetMissingProperty() {
        assertNull(dao.get(Guid.newGuid()));
    }

    @Test
    public void testSaveSshProperty() {
        UserProfileProperty newProp = UserProfileProperty.builder()
                .withNewIdIfEmpty()
                .withUserId(existingUserId)
                .withDefaultSshProp()
                .withContent("key4")
                .build();

        dao.save(newProp);
        UserProfileProperty result = dao.get(newProp.getPropertyId());

        assertThat(result).isEqualTo(newProp);
    }

    @Test
    public void testSaveJsonProperty() {
        UserProfileProperty newProp = UserProfileProperty.builder()
                .withNewIdIfEmpty()
                .withUserId(existingUserId)
                .withName("some_prop")
                .withType(UserProfileProperty.PropertyType.JSON)
                .withContent("[{}]")
                .build();

        dao.save(newProp);
        UserProfileProperty result = dao.get(newProp.getPropertyId());

        assertThat(result).isEqualTo(newProp);
    }

    @Test
    public void testSavePropsWithEmptyId() {
        UserProfileProperty newProp = UserProfileProperty.builder()
                .withDefaultSshProp()
                .withPropertyId(Guid.Empty)
                .build();

        assertThrows(IllegalArgumentException.class, () -> dao.save(newProp));
    }

    @Test
    public void testUpdateProperty() {
        UserProfileProperty update = UserProfileProperty.builder()
                .from(existingSshProperty)
                .withContent("key4")
                .build();
        Guid newId = Guid.newGuid();

        dao.update(update, newId);
        UserProfileProperty result = dao.get(newId);

        assertNotNull(result);
        assertThat(result).isEqualTo(
                UserProfileProperty.builder()
                        .from(update)
                        .withPropertyId(newId)
                        .build());
    }

    @Test
    public void testUpdatePropertyWithStaleKeyId() {
        UserProfileProperty update = UserProfileProperty.builder()
                .from(existingSshProperty)
                // random Guid instead of current ID
                .withPropertyId(Guid.newGuid())
                .withContent("key4")
                .build();

        assertThrows(DataIntegrityViolationException.class, () -> dao.update(update, Guid.newGuid()));
    }

    @Test
    public void testUpdatePropertyWithEmptyId() {
        UserProfileProperty update = UserProfileProperty.builder()
                .from(existingSshProperty)
                .build();

        assertThrows(IllegalArgumentException.class, () -> dao.update(update, Guid.Empty));
    }

    @Test
    public void testRemoveProperty() {
        dao.remove(existingSshProperty.getPropertyId());
        UserProfileProperty result = dao.get(existingSshProperty.getPropertyId());

        assertNull(result);
    }

    @Test
    void getAll() {
        List<UserProfileProperty> result = dao.getAll(existingUserId);

        assertThat(result).containsExactlyInAnyOrder(existingSshProperty, existingJsonProperty);
        assertThat(result.get(0)).hasSameClassAs(new UserProfileProperty());
    }

    @Test
    void getAllForNull() {
        assertThat(dao.getAll(null)).isEmpty();
    }

    @Test
    void getAllForEmptyId() {
        assertThat(dao.getAll(Guid.Empty)).isEmpty();
    }

    @Test
    void getProfile() {
        UserProfile profile = dao.getProfile(existingUserId);

        UserProfile existingProfile = UserProfile.builder()
                .withUserId(existingUserId)
                .withProp(existingSshProperty)
                .withProp(existingJsonProperty)
                .build();

        assertThat(profile).isEqualTo(existingProfile);
        assertThat(profile.getFirstSshPublicKeyProperty()).contains(existingSshProperty);
        assertThat(profile.getFirstSshPublicKeyProperty().orElseThrow()).hasSameClassAs(new UserProfileProperty());
    }

    @Test
    void getEmptyProfile() {
        Guid userId = Guid.newGuid();
        UserProfile expectedProfile = UserProfile.builder().withUserId(userId).build();

        UserProfile profile = dao.getProfile(userId);

        assertThat(profile).isEqualTo(expectedProfile);
    }

    @Test
    void getProfileForNull() {
        assertThat(dao.getProfile(null)).isEqualTo(new UserProfile());
    }

    @Test
    void getProfileForEmptyId() {
        assertThat(dao.getProfile(Guid.Empty)).isEqualTo(new UserProfile());
    }

    @Test
    void getAllPublicSshKeys() {
        List<UserSshKey> result = dao.getAllPublicSshKeys();

        UserSshKey expectedKey = new UserSshKey(existingLoginName, existingUserId, existingSshProperty.getContent());

        assertThat(result).containsExactly(expectedKey);
    }
}
