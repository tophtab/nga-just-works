package gov.anzong.androidnga.activity.compose.filter
import org.junit.Assert.*
import org.junit.Test
class FilterUpdateDecodeTest {
    @Test fun dataPrecedesErrorAndEmptyDataRemainsSuccessfulNull() {
        val model = FilterWordModel()
        assertEquals("done", model.convertUpdateResult("""{"data":{"0":"done"},"error":{"0":"denied"}}""").getOrThrow())
        assertEquals("denied", model.convertUpdateResult("""{"error":{"0":"denied"}}""").exceptionOrNull()?.message)
        assertTrue(model.convertUpdateResult("""{"data":{}}""").isSuccess)
        assertNull(model.convertUpdateResult("""{"data":{}}""").getOrThrow())
        assertEquals("未知错误", model.convertUpdateResult("{}").exceptionOrNull()?.message)
        assertTrue(model.convertUpdateResult("{").isFailure)
    }
}
