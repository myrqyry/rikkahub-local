package me.rerere.ai.provider.providers

import kotlinx.coroutines.runBlocking
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderSetting
import okhttp3.Interceptor
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class GoogleModelPaginationTest {
    private fun provider(respond: (String?) -> Pair<Int, String>, requests: MutableList<String>): GoogleProvider {
        val client = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            requests.add(chain.request().url.toString())
            val token = chain.request().url.queryParameter("pageToken")
            val (status, json) = respond(token)
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(status)
                .message(if (status == 200) "OK" else "Error")
                .body(json.toResponseBody("application/json".toMediaType()))
                .build()
        }).build()
        return GoogleProvider(client)
    }
    private val settings = ProviderSetting.Google(apiKey = "fake-no-network-key")

    @Test fun followsNextPageTokenAndKeepsEmbedOnlyModels() = runBlocking {
        val requests = mutableListOf<String>()
        val api = provider({ token ->
            when (token) {
                null -> 200 to """{"models":[{"name":"models/first","displayName":"First","supportedGenerationMethods":["generateContent"]}],"nextPageToken":"next token +"}"""
                "next token +" -> 200 to """{"models":[{"name":"models/embed","displayName":"Embedding","supportedGenerationMethods":["embedContent"]}]}"""
                else -> error("Unexpected page $token")
            }
        }, requests)
        val models = api.listModels(settings)
        assertEquals(listOf("first", "embed"), models.map { it.modelId })
        assertEquals(ModelType.EMBEDDING, models.last().type)
        assertEquals(2, requests.size)
        assertEquals("next token +", requests[1].toHttpUrl().queryParameter("pageToken"))
        assertEquals("100", requests[1].toHttpUrl().queryParameter("pageSize"))
    }

    @Test fun repeatedNextPageTokenFailsInsteadOfLoopingForever() = runBlocking {
        val requests = mutableListOf<String>()
        val api = provider({ _ ->
            200 to """{"models":[{"name":"models/same","displayName":"Same","supportedGenerationMethods":["generateContent"]}],"nextPageToken":"repeat"}"""
        }, requests)
        try {
            api.listModels(settings)
            fail("Must reject repeated pagination token")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty().contains("page", ignoreCase = true))
        }
        assertTrue(requests.size <= 3)
    }

    @Test fun failedLaterPageDoesNotReturnMisleadingPartialModelList() = runBlocking {
        val requests = mutableListOf<String>()
        val api = provider({ token ->
            if (token == null) 200 to """{"models":[{"name":"models/first","displayName":"First","supportedGenerationMethods":["generateContent"]}],"nextPageToken":"more"}"""
            else 503 to """{"error":{"message":"temporary failure"}}"""
        }, requests)
        try {
            api.listModels(settings)
            fail("Must surface incomplete provider listing")
        } catch (expected: IOException) {
            assertTrue(expected.message.orEmpty().contains("503"))
        }
        assertEquals(2, requests.size)
    }

    @Test fun duplicateModelIdsAcrossPagesHaveOneStableEntry() = runBlocking {
        val requests = mutableListOf<String>()
        val api = provider({ token ->
            if (token == null) 200 to """{"models":[{"name":"models/a","displayName":"A","supportedGenerationMethods":["generateContent"]}],"nextPageToken":"more"}"""
            else 200 to """{"models":[{"name":"models/a","displayName":"Duplicate","supportedGenerationMethods":["generateContent"]},{"name":"models/b","displayName":"B","supportedGenerationMethods":["generateContent"]}]}"""
        }, requests)
        assertEquals(listOf("a", "b"), api.listModels(settings).map { it.modelId })
    }
}
