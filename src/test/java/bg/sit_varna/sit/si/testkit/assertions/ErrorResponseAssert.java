package bg.sit_varna.sit.si.testkit.assertions;

import io.restassured.response.Response;
import org.assertj.core.api.AbstractAssert;

import java.util.List;
import java.util.Objects;

/**
 * Asserts against this service's actual error envelope (code/title/message/category/
 * timestamp/details) - not RFC 7807, which this codebase does not implement.
 */
public class ErrorResponseAssert extends AbstractAssert<ErrorResponseAssert, Response> {

    private ErrorResponseAssert(Response actual) {
        super(actual, ErrorResponseAssert.class);
    }

    public static ErrorResponseAssert assertThatError(Response actual) {
        return new ErrorResponseAssert(actual);
    }

    public ErrorResponseAssert hasStatus(int expectedStatus) {
        isNotNull();
        int actualStatus = actual.statusCode();
        if (actualStatus != expectedStatus) {
            failWithMessage("Expected error response status <%s> but was <%s>. Body: %s",
                    expectedStatus, actualStatus, actual.asString());
        }
        return this;
    }

    public ErrorResponseAssert hasCode(String expectedCode) {
        isNotNull();
        String actualCode = actual.jsonPath().getString("code");
        if (!Objects.equals(actualCode, expectedCode)) {
            failWithMessage("Expected error code <%s> but was <%s>", expectedCode, actualCode);
        }
        return this;
    }

    public ErrorResponseAssert hasCategory(String expectedCategory) {
        isNotNull();
        String actualCategory = actual.jsonPath().getString("category");
        if (!Objects.equals(actualCategory, expectedCategory)) {
            failWithMessage("Expected error category <%s> but was <%s>", expectedCategory, actualCategory);
        }
        return this;
    }

    public ErrorResponseAssert hasMessageContaining(String expectedSubstring) {
        isNotNull();
        String actualMessage = actual.jsonPath().getString("message");
        if (actualMessage == null || !actualMessage.contains(expectedSubstring)) {
            failWithMessage("Expected error message to contain <%s> but was <%s>",
                    expectedSubstring, actualMessage);
        }
        return this;
    }

    public ErrorResponseAssert hasDetailContaining(String expectedSubstring) {
        isNotNull();
        List<String> details = actual.jsonPath().getList("details", String.class);
        boolean found = details != null && details.stream().anyMatch(d -> d.contains(expectedSubstring));
        if (!found) {
            failWithMessage("Expected details %s to contain an entry with <%s>", details, expectedSubstring);
        }
        return this;
    }
}
