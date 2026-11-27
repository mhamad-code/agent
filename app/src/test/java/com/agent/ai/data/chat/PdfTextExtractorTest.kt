package com.agent.ai.data.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The local PDF text extractor, against a hand written, uncompressed PDF. */
class PdfTextExtractorTest {

    private val pdf = """
        %PDF-1.4
        1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj
        2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >> endobj
        3 0 obj << /Type /Page /Parent 2 0 R /Contents 4 0 R >> endobj
        4 0 obj << /Length 60 >>
        stream
        BT /F1 12 Tf 72 720 Td (Hello from a PDF) Tj ET
        endstream
        endobj
        trailer << /Root 1 0 R >>
        %%EOF
    """.trimIndent()

    @Test
    fun `text is extracted from a content stream`() {
        assertEquals("Hello from a PDF", PdfTextExtractor.extract(pdf.toByteArray()))
    }

    @Test
    fun `escaped parentheses stay inside the text`() {
        val body = """
            %PDF-1.4
            4 0 obj << /Length 40 >>
            stream
            BT (A \(quoted\) word) Tj ET
            endstream
            endobj
        """.trimIndent()
        assertTrue(PdfTextExtractor.extract(body.toByteArray()).contains("A (quoted) word"))
    }

    @Test
    fun `a PDF without a text layer yields nothing`() {
        val scan = "%PDF-1.4\n1 0 obj << /Type /XObject /Subtype /Image >> endobj\n%%EOF"
        assertEquals("", PdfTextExtractor.extract(scan.toByteArray()))
    }

    @Test
    fun `garbage does not throw`() {
        assertFalse(PdfTextExtractor.extract(byteArrayOf(0, 1, 2, 3)).isNotEmpty())
    }
}
