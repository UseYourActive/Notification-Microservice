package bg.sit_varna.sit.si.testkit.base;

import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

import static io.restassured.RestAssured.given;

public abstract class ApiTestBase extends DatabaseTestBase {

    protected RequestSpecification apiRequest() {
        return given().contentType(ContentType.JSON);
    }
}
