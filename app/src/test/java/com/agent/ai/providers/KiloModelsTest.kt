package com.agent.ai.providers

import com.agent.ai.markdown.SyntaxHighlighter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The model list parser, against the documented `GET /models` response shape. */
class KiloModelsTest {

    private val body = """
        {
          "data": [
            {
              "id": "kilo-auto/free",
              "name": "Auto Free",
              "pricing": {"prompt": "0", "completion": "0"},
              "context_length": 256000,
              "description": "Rotates through available free models."
            },
            {
              "id": "z-ai/glm-5:free",
              "name": "GLM 5 (free)",
              "pricing": {"prompt": "0", "completion": "0"},
              "context_length": 128000
            },
            {
              "id": "anthropic/claude-opus-5.5",
              "name": "Claude Opus 5.5",
              "pricing": {"prompt": "0.000015", "completion": "0.000075"},
              "context_length": 200000
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `keeps only models that work without a key and puts auto free first`() {
        val models = KiloGatewayProvider.parseModels(body)
        assertEquals(listOf("kilo-auto/free", "z-ai/glm-5:free"), models.map { it.id })
        assertTrue(models.all { it.free })
        assertEquals(256_000, models.first().contextLength)
    }

    @Test
    fun `a paid model is not offered anonymously`() {
        val models = KiloGatewayProvider.parseModels(body)
        assertFalse(models.any { it.id == "anthropic/claude-opus-5.5" })
    }

    @Test
    fun `an unexpected body returns no models instead of throwing`() {
        assertTrue(KiloGatewayProvider.parseModels("<html>error</html>").isEmpty())
        assertTrue(KiloGatewayProvider.parseModels("{}").isEmpty())
    }

    @Test
    fun `language aliases normalise for the highlighter`() {
        assertEquals("kotlin", SyntaxHighlighter.normalize("kt"))
        assertEquals("typescript", SyntaxHighlighter.normalize("tsx"))
        assertEquals("bash", SyntaxHighlighter.normalize("sh"))
        assertFalse(SyntaxHighlighter.normalize("cobol") in SyntaxHighlighter.supported)
        assertTrue(SyntaxHighlighter.normalize("sql") in SyntaxHighlighter.supported)
    }
}
