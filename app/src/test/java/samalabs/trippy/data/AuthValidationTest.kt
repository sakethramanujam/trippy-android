package samalabs.trippy.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthValidationTest {
    @Test fun email() {
        assertTrue(AuthValidation.email("alex@example.com"))
        assertFalse(AuthValidation.email("alex"))
        assertFalse(AuthValidation.email("alex@"))
    }

    @Test fun password() {
        assertNotNull(AuthValidation.passwordProblem("short"))
        assertNull(AuthValidation.passwordProblem("long-enough-1"))
    }

    @Test fun airport() {
        assertTrue(AuthValidation.airport(""))
        assertTrue(AuthValidation.airport("sfo"))
        assertFalse(AuthValidation.airport("SF"))
        assertFalse(AuthValidation.airport("S1O"))
    }

    @Test fun errorMessageReadsDetail() {
        assertEquals("Use at least 8 characters for your password", ApiClient.errorMessage(400, Fixtures.read("signup_error.json")))
        assertEquals("Field required", ApiClient.errorMessage(422, """{"detail":[{"loc":["body","email"],"msg":"field required"}]}"""))
        assertEquals("Wrong email or password.", ApiClient.errorMessage(401, "not json"))
        assertEquals("The server had a problem (HTTP 502). Try again in a moment.", ApiClient.errorMessage(502, ""))
    }
}
