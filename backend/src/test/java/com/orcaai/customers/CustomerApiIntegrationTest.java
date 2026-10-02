package com.orcaai.customers;

import static com.orcaai.support.Authenticated.as;
import static com.orcaai.support.CsrfSupport.csrfToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.orcaai.organizations.Organization;
import com.orcaai.shared.security.Role;
import com.orcaai.support.IntegrationTest;
import com.orcaai.support.TenantScope;
import com.orcaai.support.TestData;
import com.orcaai.users.User;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@IntegrationTest
class CustomerApiIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    TestData testData;

    @Autowired
    TenantScope tenant;

    @Autowired
    JdbcTemplate jdbc;

    Organization organization;
    User owner;
    User admin;
    User member;

    @BeforeEach
    void setUp() {
        organization = testData.organization();
        owner = testData.user(organization, "senha qualquer longa", Role.OWNER);
        admin = testData.user(organization, "senha qualquer longa", Role.ADMIN);
        member = testData.user(organization, "senha qualquer longa", Role.MEMBER);
    }

    @Test
    void createsNormalizedCustomerAndStoresItInTheUsersOrganization() throws Exception {
        String id = create(owner, """
                {"name":"  João da Silva  ","phone":" (11) 98888-7777 ","email":" Joao.Silva@Example.COM ",
                 "notes":"  Prefere contato à tarde.  "}""")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/customers/")))
                .andExpect(jsonPath("$.name").value("João da Silva"))
                .andExpect(jsonPath("$.phone").value("(11) 98888-7777"))
                .andExpect(jsonPath("$.email").value("joao.silva@example.com"))
                .andExpect(jsonPath("$.notes").value("Prefere contato à tarde."))
                .andExpect(jsonPath("$.archived").value(false))
                .andExpect(jsonPath("$.organizationId").doesNotExist())
                .andReturn().getResponse().getContentAsString().transform(body -> JsonPath.read(body, "$.id"));

        Map<String, Object> row = tenant.as(organization, () -> jdbc.queryForMap(
                "select organization_id, name, email, archived_at from customers where id = ?::uuid", id));
        assertThat(row.get("organization_id")).hasToString(organization.getId().toString());
        assertThat(row).containsEntry("name", "João da Silva").containsEntry("email", "joao.silva@example.com");
        assertThat(row.get("archived_at")).isNull();
    }

    @Test
    void blankOptionalFieldsAreStoredAsEmpty() throws Exception {
        create(member, """
                {"name":"Maria","phone":"   ","email":"","notes":" "}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.phone").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.notes").doesNotExist());
    }

    @Test
    void rejectsInvalidInput() throws Exception {
        Map<String, String> invalid = Map.of(
                "{}", "name",
                "{\"name\":\"   \"}", "name",
                "{\"name\":\"" + "a".repeat(151) + "\"}", "name",
                "{\"name\":\"Ana\",\"email\":\"nao-e-email\"}", "email",
                "{\"name\":\"Ana\",\"phone\":\"" + "9".repeat(41) + "\"}", "phone",
                "{\"name\":\"Ana\",\"notes\":\"" + "x".repeat(4001) + "\"}", "notes");
        for (var entry : invalid.entrySet()) {
            create(owner, entry.getKey())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value(entry.getValue()));
        }
        assertThat(countCustomers()).isZero();
    }

    @Test
    void rejectsUnknownAndSensitiveProperties() throws Exception {
        Organization other = testData.organization();

        for (String body : List.of(
                "{\"name\":\"Ana\",\"organizationId\":\"" + other.getId() + "\"}",
                "{\"name\":\"Ana\",\"archived\":true}",
                "{\"name\":\"Ana\",\"id\":\"" + other.getId() + "\"}")) {
            create(owner, body)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value("Requisição inválida."));
        }
        assertThat(countCustomers()).isZero();
    }

    @Test
    void acceptsLongestAllowedValues() throws Exception {
        create(owner, """
                {"name":"%s","phone":"%s","notes":"%s"}""".formatted("a".repeat(150), "9".repeat(40), "x".repeat(4000)))
                .andExpect(status().isCreated());
    }

    @Test
    void singleCustomerResponsesCarryTheVersionAsETag() throws Exception {
        String id = createdId(owner, "{\"name\":\"Carlos\"}");

        mvc.perform(get("/api/customers/" + id).with(as(member)))
                .andExpect(header().string("ETag", "\"0\""))
                .andExpect(jsonPath("$.version").doesNotExist());
        mvc.perform(update(member, id, "\"0\"", "{\"name\":\"Carlos Pereira\",\"phone\":\"1199\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"1\""))
                .andExpect(jsonPath("$.name").value("Carlos Pereira"))
                .andExpect(jsonPath("$.phone").value("1199"));
        assertThat(etag(id)).isEqualTo("\"1\"");
    }

    @Test
    void staleIfMatchIsRefusedWith412AndChangesNothing() throws Exception {
        String id = createdId(owner, "{\"name\":\"Carlos\"}");
        mvc.perform(update(owner, id, "\"0\"", "{\"name\":\"Primeira edição\"}")).andExpect(status().isOk());

        mvc.perform(update(member, id, "\"0\"", "{\"name\":\"Edição atrasada\"}"))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.detail").value(
                        "Este registro foi alterado por outra pessoa. Recarregue a página para ver a versão mais recente."));
        mvc.perform(post("/api/customers/" + id + "/archive").with(as(owner)).with(csrfToken(mvc)).header("If-Match", "\"0\""))
                .andExpect(status().isPreconditionFailed());
        mvc.perform(post("/api/customers/" + id + "/restore").with(as(owner)).with(csrfToken(mvc)).header("If-Match", "\"0\""))
                .andExpect(status().isPreconditionFailed());

        mvc.perform(get("/api/customers/" + id).with(as(owner)))
                .andExpect(jsonPath("$.name").value("Primeira edição"))
                .andExpect(jsonPath("$.archived").value(false));
    }

    @Test
    void changesRequireIfMatch() throws Exception {
        String id = createdId(owner, "{\"name\":\"Carlos\"}");

        mvc.perform(put("/api/customers/" + id).with(as(owner)).with(csrfToken(mvc))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Sem versão\"}"))
                .andExpect(status().isPreconditionRequired());
        mvc.perform(post("/api/customers/" + id + "/archive").with(as(owner)).with(csrfToken(mvc)))
                .andExpect(status().isPreconditionRequired());
        mvc.perform(post("/api/customers/" + id + "/restore").with(as(owner)).with(csrfToken(mvc)))
                .andExpect(status().isPreconditionRequired());
        mvc.perform(update(owner, id, "abc", "{\"name\":\"Versão ruim\"}")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/customers/" + id).with(as(owner))).andExpect(jsonPath("$.name").value("Carlos"));
    }

    @Test
    void listsActiveCustomersNewestChangesFirstWithPagination() throws Exception {
        for (String name : List.of("Primeiro", "Segundo", "Terceiro")) {
            createdId(owner, "{\"name\":\"" + name + "\"}");
        }

        mvc.perform(get("/api/customers").param("size", "2").with(as(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].name").value("Terceiro"))
                .andExpect(jsonPath("$.items[1].name").value("Segundo"))
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.size").value(2));
        mvc.perform(get("/api/customers").param("size", "2").param("page", "1").with(as(member)))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].name").value("Primeiro"));
    }

    @Test
    void defaultPageSizeIs20AndLimitsAreEnforced() throws Exception {
        mvc.perform(get("/api/customers").with(as(owner))).andExpect(jsonPath("$.size").value(20));

        for (Map.Entry<String, String> param : List.of(
                Map.entry("size", "101"), Map.entry("size", "100000"), Map.entry("size", "0"),
                Map.entry("page", "-1"), Map.entry("status", "DELETED"), Map.entry("q", "x".repeat(101)))) {
            mvc.perform(get("/api/customers").param(param.getKey(), param.getValue()).with(as(owner)))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/customers").param("size", "100").with(as(owner))).andExpect(status().isOk());
    }

    @Test
    void searchesByNameEmailAndPhoneIgnoringCase() throws Exception {
        createdId(owner, "{\"name\":\"João da Silva\",\"email\":\"joao@obra.com.br\",\"phone\":\"11 97777-1234\"}");
        createdId(owner, "{\"name\":\"Construtora Alfa\",\"email\":\"contato@alfa.com\"}");

        assertThat(searchNames("JOÃO")).containsExactly("João da Silva");
        assertThat(searchNames("obra.com")).containsExactly("João da Silva");
        assertThat(searchNames("97777")).containsExactly("João da Silva");
        assertThat(searchNames("alfa")).containsExactly("Construtora Alfa");
        assertThat(searchNames("inexistente")).isEmpty();
    }

    @Test
    void searchTreatsWildcardsAsText() throws Exception {
        createdId(owner, "{\"name\":\"Cliente 100% satisfeito\"}");
        createdId(owner, "{\"name\":\"Outro cliente\"}");

        assertThat(searchNames("%")).containsExactly("Cliente 100% satisfeito");
        assertThat(searchNames("_")).isEmpty();
        assertThat(searchNames("' or 1=1 --")).isEmpty();
    }

    @Test
    void archivedCustomersLeaveTheActiveListAndCanBeRestored() throws Exception {
        String id = createdId(owner, "{\"name\":\"Cliente Antigo\"}");

        mvc.perform(action(admin, id, "archive", etag(id)))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"1\""))
                .andExpect(jsonPath("$.archived").value(true));
        assertThat(listNames("ACTIVE")).doesNotContain("Cliente Antigo");
        assertThat(listNames("ARCHIVED")).containsExactly("Cliente Antigo");
        assertThat(listNames("ALL")).containsExactly("Cliente Antigo");
        mvc.perform(get("/api/customers/" + id).with(as(member))).andExpect(jsonPath("$.archived").value(true));

        mvc.perform(action(owner, id, "restore", etag(id)))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"2\""))
                .andExpect(jsonPath("$.archived").value(false));
        assertThat(listNames("ACTIVE")).containsExactly("Cliente Antigo");
        assertThat(listNames("ARCHIVED")).isEmpty();
    }

    @Test
    void memberCanCreateAndEditButNotArchiveOrRestore() throws Exception {
        String id = createdId(member, "{\"name\":\"Do Membro\"}");
        mvc.perform(update(member, id, "\"0\"", "{\"name\":\"Do Membro Editado\"}")).andExpect(status().isOk());

        mvc.perform(action(member, id, "archive", etag(id))).andExpect(status().isForbidden());
        mvc.perform(action(owner, id, "archive", etag(id))).andExpect(status().isOk());
        mvc.perform(action(member, id, "restore", etag(id))).andExpect(status().isForbidden());

        assertThat(listNames("ARCHIVED")).containsExactly("Do Membro Editado");
    }

    @Test
    void writesRequireCsrfAndEverythingRequiresAuthentication() throws Exception {
        mvc.perform(post("/api/customers").with(as(owner))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Sem CSRF\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/customers")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/customers").with(csrfToken(mvc))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Anônimo\"}"))
                .andExpect(status().isUnauthorized());
        assertThat(countCustomers()).isZero();
    }

    @Test
    void unknownOrMalformedIdsAreNotFoundOrBadRequest() throws Exception {
        mvc.perform(get("/api/customers/" + java.util.UUID.randomUUID()).with(as(owner)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/customers/nao-e-uuid").with(as(owner))).andExpect(status().isBadRequest());
    }

    private ResultActions create(User user, String body) throws Exception {
        return mvc.perform(post("/api/customers").with(as(user)).with(csrfToken(mvc))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String createdId(User user, String body) throws Exception {
        String response = create(user, body).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    private org.springframework.test.web.servlet.RequestBuilder update(User user, String id, String ifMatch, String body)
            throws Exception {
        return put("/api/customers/" + id).with(as(user)).with(csrfToken(mvc))
                .header("If-Match", ifMatch)
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private org.springframework.test.web.servlet.RequestBuilder action(User user, String id, String action, String ifMatch)
            throws Exception {
        return post("/api/customers/" + id + "/" + action).with(as(user)).with(csrfToken(mvc)).header("If-Match", ifMatch);
    }

    private String etag(String id) throws Exception {
        return mvc.perform(get("/api/customers/" + id).with(as(owner))).andReturn().getResponse().getHeader("ETag");
    }

    private List<String> searchNames(String term) throws Exception {
        String body = mvc.perform(get("/api/customers").param("q", term).with(as(owner)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.items[*].name");
    }

    private List<String> listNames(String status) throws Exception {
        String body = mvc.perform(get("/api/customers").param("status", status).with(as(owner)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.items[*].name");
    }

    private int countCustomers() {
        return tenant.as(organization, () -> jdbc.queryForObject("select count(*) from customers", Integer.class));
    }
}
