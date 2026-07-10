package bg.sit_varna.sit.si.api;

import bg.sit_varna.sit.si.dto.request.CreateTemplateRequest;
import bg.sit_varna.sit.si.dto.response.UpdateTemplateRequest;
import bg.sit_varna.sit.si.entity.TemplateRecord;
import bg.sit_varna.sit.si.repository.TemplateRepository;
import bg.sit_varna.sit.si.testkit.base.ApiTestBase;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static bg.sit_varna.sit.si.testkit.assertions.ErrorResponseAssert.assertThatError;
import static bg.sit_varna.sit.si.testkit.mother.TemplateRecordMother.aTemplate;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

@QuarkusTest
class TemplateResourceApiTest extends ApiTestBase {

    @Inject
    TemplateRepository templateRepository;

    private TemplateRecord seedTemplate() {
        TemplateRecord record = aTemplate().build();
        QuarkusTransaction.requiringNew().run(() -> templateRepository.persist(record));
        return record;
    }

    // --- GET /validate ---

    @Test
    void validateTemplate_realFileTemplate_returnsExistsTrue() {
        apiRequest()
                .queryParam("template", "email/welcome")
                .queryParam("locale", "en")
                .when()
                .get("/api/v1/templates/validate")
                .then()
                .statusCode(200)
                .body("template", equalTo("email/welcome"))
                .body("locale", equalTo("en"))
                .body("exists", equalTo(true));
    }

    @Test
    void validateTemplate_missingLocaleParam_returns400ValidationError() {
        var response = apiRequest()
                .queryParam("template", "email/welcome")
                .when()
                .get("/api/v1/templates/validate")
                .thenReturn();

        assertThatError(response).hasStatus(400);
    }

    // --- GET /discovery ---

    @Test
    void getAvailableFileTemplates_noFilter_returnsAllFileTemplates() {
        apiRequest()
                .when()
                .get("/api/v1/templates/discovery")
                .then()
                .statusCode(200)
                .body("total", greaterThanOrEqualTo(1))
                .body("templates.name", org.hamcrest.Matchers.hasItem("email/welcome"));
    }

    @Test
    void getAvailableFileTemplates_unknownTypeFilter_returnsEmptyListNot400() {
        // Documents actual behavior: the resource filters in-memory rather than
        // validating the "type" query param, despite the OpenAPI doc text implying a
        // 400 for invalid types.
        apiRequest()
                .queryParam("type", "carrier-pigeon")
                .when()
                .get("/api/v1/templates/discovery")
                .then()
                .statusCode(200)
                .body("total", equalTo(0));
    }

    // --- POST /templates ---

    @Test
    void createTemplate_success_returns201WithPersistedTemplate() {
        CreateTemplateRequest request = new CreateTemplateRequest(
                "test/new-template", "en", "<p>Test content</p>");

        apiRequest()
                .body(request)
                .when()
                .post("/api/v1/templates")
                .then()
                .statusCode(201)
                .body("templateName", equalTo("test/new-template"))
                .body("locale", equalTo("en"))
                .body("active", equalTo(true));
    }

    @Test
    void createTemplate_blankContent_returns400ValidationError() {
        CreateTemplateRequest request = new CreateTemplateRequest("test/new-template", "en", "");

        var response = apiRequest()
                .body(request)
                .when()
                .post("/api/v1/templates")
                .thenReturn();

        assertThatError(response).hasStatus(400).hasCode("VALIDATION_FAILED");
    }

    @Test
    void createTemplate_duplicateNameAndLocale_returns400InvalidArgument() {
        TemplateRecord existing = seedTemplate();
        CreateTemplateRequest request = new CreateTemplateRequest(
                existing.getTemplateName(), existing.getLocale(), "<p>Different content</p>");

        var response = apiRequest()
                .body(request)
                .when()
                .post("/api/v1/templates")
                .thenReturn();

        assertThatError(response).hasStatus(400).hasCode("INVALID_ARGUMENT");
    }

    // --- GET /templates (all DB templates) ---

    @Test
    void getAllDbTemplates_returnsPersistedTemplates() {
        seedTemplate();

        apiRequest()
                .when()
                .get("/api/v1/templates")
                .then()
                .statusCode(200)
                .body("size()", greaterThanOrEqualTo(1));
    }

    // --- GET /templates/{id} ---

    @Test
    void getTemplate_existingId_returnsTemplate() {
        TemplateRecord existing = seedTemplate();

        apiRequest()
                .when()
                .get("/api/v1/templates/" + existing.getId())
                .then()
                .statusCode(200)
                .body("id", equalTo(existing.getId().toString()))
                .body("templateName", equalTo(existing.getTemplateName()));
    }

    @Test
    void getTemplate_unknownId_returns404() {
        apiRequest()
                .when()
                .get("/api/v1/templates/" + UUID.randomUUID())
                .then()
                .statusCode(404);
    }

    @Test
    void getTemplate_malformedId_returns400InvalidArgument() {
        var response = apiRequest()
                .when()
                .get("/api/v1/templates/not-a-uuid")
                .thenReturn();

        assertThatError(response).hasStatus(400).hasCode("INVALID_ARGUMENT");
    }

    // --- PUT /templates/{id} ---

    @Test
    void updateTemplate_existingId_updatesContentAndActive() {
        TemplateRecord existing = seedTemplate();
        UpdateTemplateRequest request = new UpdateTemplateRequest("<p>Updated content</p>", false);

        apiRequest()
                .body(request)
                .when()
                .put("/api/v1/templates/" + existing.getId())
                .then()
                .statusCode(200)
                .body("content", equalTo("<p>Updated content</p>"))
                .body("active", equalTo(false));
    }

    @Test
    void updateTemplate_unknownId_returns404() {
        UpdateTemplateRequest request = new UpdateTemplateRequest("<p>Updated content</p>", true);

        apiRequest()
                .body(request)
                .when()
                .put("/api/v1/templates/" + UUID.randomUUID())
                .then()
                .statusCode(404);
    }

    @Test
    void updateTemplate_blankContent_returns400ValidationError() {
        TemplateRecord existing = seedTemplate();
        UpdateTemplateRequest request = new UpdateTemplateRequest("", true);

        var response = apiRequest()
                .body(request)
                .when()
                .put("/api/v1/templates/" + existing.getId())
                .thenReturn();

        assertThatError(response).hasStatus(400).hasCode("VALIDATION_FAILED");
    }

    // --- DELETE /templates/{id} ---

    @Test
    void deleteTemplate_existingId_returns204() {
        TemplateRecord existing = seedTemplate();

        apiRequest()
                .when()
                .delete("/api/v1/templates/" + existing.getId())
                .then()
                .statusCode(204);
    }

    @Test
    void deleteTemplate_unknownId_returns404() {
        apiRequest()
                .when()
                .delete("/api/v1/templates/" + UUID.randomUUID())
                .then()
                .statusCode(404);
    }
}
