package com.hermes.client.attachment

import com.hermes.client.domain.model.AIProviderType
import com.hermes.client.domain.model.Attachment
import com.hermes.client.domain.provider.ModelCapabilityHelper
import com.hermes.client.util.FileAttachmentHelper
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class FileAttachmentHelperTest {

    @Test
    fun testIsImageMime() {
        assertTrue(FileAttachmentHelper.isImageMime("image/jpeg"))
        assertTrue(FileAttachmentHelper.isImageMime("image/png"))
        assertTrue(FileAttachmentHelper.isImageMime("image/webp"))
        assertTrue(FileAttachmentHelper.isImageMime("image/gif"))
        assertFalse(FileAttachmentHelper.isImageMime("application/pdf"))
        assertFalse(FileAttachmentHelper.isImageMime("text/plain"))
    }

    @Test
    fun testIsPdf() {
        assertTrue(FileAttachmentHelper.isPdf("application/pdf", "document.pdf"))
        assertTrue(FileAttachmentHelper.isPdf("application/octet-stream", "report.PDF"))
        assertFalse(FileAttachmentHelper.isPdf("image/png", "photo.png"))
    }

    @Test
    fun testIsTextMime() {
        assertTrue(FileAttachmentHelper.isTextMime("text/plain", "notes.txt"))
        assertTrue(FileAttachmentHelper.isTextMime("text/csv", "data.csv"))
        assertTrue(FileAttachmentHelper.isTextMime("application/json", "config.json"))
        assertTrue(FileAttachmentHelper.isTextMime("text/markdown", "README.md"))
        assertTrue(FileAttachmentHelper.isTextMime("application/octet-stream", "main.kt"))
        assertTrue(FileAttachmentHelper.isTextMime("application/octet-stream", "script.py"))
        assertFalse(FileAttachmentHelper.isTextMime("image/jpeg", "image.jpg"))
        assertFalse(FileAttachmentHelper.isTextMime("application/pdf", "document.pdf"))
    }

    @Test
    fun testFormatFileSize() {
        assertEquals("0 B", FileAttachmentHelper.formatFileSize(0))
        assertEquals("500.0 B", FileAttachmentHelper.formatFileSize(500))
        assertEquals("1.0 KB", FileAttachmentHelper.formatFileSize(1024))
        assertEquals("2.5 MB", FileAttachmentHelper.formatFileSize((2.5 * 1024 * 1024).toLong()))
    }

    @Test
    fun testModelCapabilityHelper() {
        // Google Gemini
        assertTrue(ModelCapabilityHelper.isVisionSupported(AIProviderType.GOOGLE_AI, "gemini-1.5-flash"))
        assertTrue(ModelCapabilityHelper.isVisionSupported(AIProviderType.GOOGLE_AI, "gemini-2.0-flash-exp"))
        assertTrue(ModelCapabilityHelper.supportsDirectPdf(AIProviderType.GOOGLE_AI, "gemini-1.5-pro"))

        // Anthropic Claude
        assertTrue(ModelCapabilityHelper.isVisionSupported(AIProviderType.ANTHROPIC, "claude-3-5-sonnet-latest"))
        assertTrue(ModelCapabilityHelper.isVisionSupported(AIProviderType.ANTHROPIC, "claude-3-haiku-20240307"))
        assertTrue(ModelCapabilityHelper.supportsDirectPdf(AIProviderType.ANTHROPIC, "claude-3-5-sonnet-latest"))

        // OpenAI
        assertTrue(ModelCapabilityHelper.isVisionSupported(AIProviderType.OPENAI, "gpt-4o"))
        assertTrue(ModelCapabilityHelper.isVisionSupported(AIProviderType.OPENAI, "gpt-4o-mini"))
        assertTrue(ModelCapabilityHelper.isVisionSupported(AIProviderType.OPENAI, "gpt-4-turbo"))
        assertFalse(ModelCapabilityHelper.isVisionSupported(AIProviderType.OPENAI, "gpt-3.5-turbo"))

        // OpenRouter
        assertTrue(ModelCapabilityHelper.isVisionSupported(AIProviderType.OPENROUTER, "google/gemini-2.0-flash-exp:free"))
        assertTrue(ModelCapabilityHelper.isVisionSupported(AIProviderType.OPENROUTER, "meta-llama/llama-3.2-11b-vision-instruct"))
        assertFalse(ModelCapabilityHelper.isVisionSupported(AIProviderType.OPENROUTER, "meta-llama/llama-3.1-8b-instruct"))

        // Ollama
        assertTrue(ModelCapabilityHelper.isVisionSupported(AIProviderType.OLLAMA, "llava:latest"))
        assertTrue(ModelCapabilityHelper.isVisionSupported(AIProviderType.OLLAMA, "llama3.2-vision:11b"))
        assertFalse(ModelCapabilityHelper.isVisionSupported(AIProviderType.OLLAMA, "llama3:8b"))

        // Unsupported message formatting
        val msg = ModelCapabilityHelper.getUnsupportedVisionMessage(AIProviderType.OPENAI, "gpt-3.5-turbo")
        assertTrue(msg.contains("gpt-3.5-turbo"))
        assertTrue(msg.contains("does not support image input"))
    }

    @Test
    fun testAttachmentSerialization() {
        val json = Json { ignoreUnknownKeys = true }
        val original = listOf(
            Attachment(
                id = "att_1",
                fileName = "photo.jpg",
                mimeType = "image/jpeg",
                size = 102400,
                uri = "content://media/external/images/1",
                localPath = "/data/user/0/com.hermes.client/files/attachments/att_1_photo.jpg",
                isUploaded = true
            ),
            Attachment(
                id = "att_2",
                fileName = "doc.pdf",
                mimeType = "application/pdf",
                size = 204800,
                uri = "content://media/external/docs/2",
                localPath = "/data/user/0/com.hermes.client/files/attachments/att_2_doc.pdf"
            )
        )

        val serialized = json.encodeToString(original)
        val deserialized = json.decodeFromString<List<Attachment>>(serialized)

        assertEquals(2, deserialized.size)
        assertEquals("att_1", deserialized[0].id)
        assertEquals("photo.jpg", deserialized[0].fileName)
        assertEquals("image/jpeg", deserialized[0].mimeType)
        assertEquals("/data/user/0/com.hermes.client/files/attachments/att_1_photo.jpg", deserialized[0].localPath)
        assertTrue(deserialized[0].isUploaded)

        assertEquals("att_2", deserialized[1].id)
        assertEquals("doc.pdf", deserialized[1].fileName)
        assertEquals("application/pdf", deserialized[1].mimeType)
        assertFalse(deserialized[1].isUploaded)
    }
}
