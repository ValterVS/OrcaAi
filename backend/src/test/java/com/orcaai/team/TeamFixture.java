package com.orcaai.team;

import static com.orcaai.support.Authenticated.as;
import static com.orcaai.support.CsrfSupport.csrfToken;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.jayway.jsonpath.JsonPath;
import com.orcaai.support.RecordingMailSender;
import com.orcaai.users.User;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MockMvc;

/** Team endpoints over HTTP, authenticated as a given user, with a real CSRF token. */
@Component
class TeamFixture {

    static final String PASSWORD = "senha do convidado longa";

    private final MockMvc mvc;
    private final RecordingMailSender mail;

    TeamFixture(MockMvc mvc, RecordingMailSender mail) {
        this.mvc = mvc;
        this.mail = mail;
    }

    MockHttpServletResponse invite(User actor, String email, String role) throws Exception {
        return mvc.perform(post("/api/team/invitations").with(as(actor)).with(csrfToken(mvc))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"role\":\"%s\"}".formatted(email, role)))
                .andReturn().getResponse();
    }

    MockHttpServletResponse invitationAction(User actor, String invitationId, String action) throws Exception {
        return mvc.perform(post("/api/team/invitations/" + invitationId + "/" + action).with(as(actor)).with(csrfToken(mvc)))
                .andReturn().getResponse();
    }

    MockHttpServletResponse pendingInvitations(User actor) throws Exception {
        return mvc.perform(get("/api/team/invitations").with(as(actor))).andReturn().getResponse();
    }

    MockHttpServletResponse accept(String token, String name, String password) throws Exception {
        return acceptRaw("{\"token\":\"%s\",\"name\":\"%s\",\"password\":\"%s\"}".formatted(token, name, password));
    }

    MockHttpServletResponse acceptRaw(String body) throws Exception {
        return mvc.perform(post("/api/invitations/accept").with(csrfToken(mvc))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse();
    }

    MockHttpServletResponse members(User actor) throws Exception {
        return mvc.perform(get("/api/team/members").with(as(actor))).andReturn().getResponse();
    }

    MockHttpServletResponse changeRole(User actor, UUID targetId, String ifMatch, String role) throws Exception {
        var request = put("/api/team/members/" + targetId + "/role").with(as(actor)).with(csrfToken(mvc))
                .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"%s\"}".formatted(role));
        if (ifMatch != null) {
            request.header("If-Match", ifMatch);
        }
        return mvc.perform(request).andReturn().getResponse();
    }

    MockHttpServletResponse memberAction(User actor, UUID targetId, String action, String ifMatch) throws Exception {
        var request = post("/api/team/members/" + targetId + "/" + action).with(as(actor)).with(csrfToken(mvc));
        if (ifMatch != null) {
            request.header("If-Match", ifMatch);
        }
        return mvc.perform(request).andReturn().getResponse();
    }

    /** The If-Match value for a member, as the team screen would send it. */
    String memberTag(User actor, UUID targetId) throws Exception {
        List<Integer> versions = JsonPath.read(members(actor).getContentAsString(),
                "$[?(@.id == '" + targetId + "')].version");
        return "\"" + versions.getFirst() + "\"";
    }

    String invitationId(MockHttpServletResponse created) throws Exception {
        return JsonPath.read(created.getContentAsString(), "$.id");
    }

    String awaitToken(String email, int count) {
        return mail.awaitToken(email, count);
    }

    static String uniqueEmail() {
        return "convidado-" + UUID.randomUUID() + "@example.com";
    }
}
