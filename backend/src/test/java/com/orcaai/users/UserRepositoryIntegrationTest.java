package com.orcaai.users;

import static org.assertj.core.api.Assertions.assertThat;

import com.orcaai.organizations.Organization;
import com.orcaai.shared.security.Role;
import com.orcaai.support.IntegrationTest;
import com.orcaai.support.TestData;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.repository.CrudRepository;

@IntegrationTest
class UserRepositoryIntegrationTest {

    @Autowired
    UserRepository users;

    @Autowired
    TestData testData;

    @Test
    void lookupByIdRequiresTheOwningOrganization() {
        Organization orgA = testData.organization();
        Organization orgB = testData.organization();
        User userOfA = testData.user(orgA, "password-123", Role.MEMBER);

        assertThat(users.findByIdAndOrganizationId(userOfA.getId(), orgB.getId())).isEmpty();
        assertThat(users.findByIdAndOrganizationId(userOfA.getId(), orgA.getId())).isPresent();
    }

    @Test
    void repositoryDoesNotExposeUnscopedAccessById() {
        assertThat(CrudRepository.class.isAssignableFrom(UserRepository.class)).isFalse();
        assertThat(Arrays.stream(UserRepository.class.getMethods()).map(Method::getName))
                .doesNotContain("findById", "findAll", "deleteById", "existsById");
    }
}
