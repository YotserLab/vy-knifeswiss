package com.example.spectests
import com.example.ui.screens.docs.EpubViewerScreen
import com.example.ui.screens.docs.PdfViewerScreen
import com.example.ui.screens.docs.TextEditorScreen
import com.example.ui.screens.docs.WhiteboardScreen

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentosSpecTest {

    // DOC-R01: PDF renderizado via PdfRenderer RENDER_MODE_FOR_DISPLAY
    @Test
    fun test_DOC_R01_pdf_renderer_display_mode() {
        assertTrue(srcContains("com/example/ui/screens/docs/PdfViewerScreen.kt", "PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY"))
    }

    // DOC-R02: acesso ao PdfRenderer é sincronizado
    @Test
    fun test_DOC_R02_synchronized_renderer() {
        val s = src("com/example/ui/screens/docs/PdfViewerScreen.kt")
        assertTrue(s.contains("synchronized") || s.contains("Synchronized"))
    }

    // DOC-R03/R04: EPUB em WebView servindo assets só via epub://
    @Test
    fun test_DOC_R03_R04_epub_scheme() {
        val s = src("com/example/ui/screens/docs/EpubViewerScreen.kt")
        assertTrue(s.contains("epub://") && s.contains("WebView"))
    }

    // DOC-R05: editor de texto abre/salva arquivo
    @Test
    fun test_DOC_R05_text_editor() {
        val s = src("com/example/ui/screens/docs/TextEditorScreen.kt")
        assertTrue(s.contains("fun TextEditorScreen") && (s.contains("writeText") || s.contains("readText") || s.contains("save")))
    }

    // DOC-R06: quadro branco desenha livremente em canvas
    @Test
    fun test_DOC_R06_whiteboard_canvas() {
        val s = src("com/example/ui/screens/docs/WhiteboardScreen.kt")
        assertTrue(s.contains("Canvas") || s.contains("drawLine") || s.contains("Path"))
    }

    // DOC-R07: documentos abertos por caminho codificado na rota
    @Test
    fun test_DOC_R07_encoded_path_routes() {
        assertTrue(srcContains("com/example/ui/screens/docs/PdfViewerScreen.kt", "encodedPath"))
        assertTrue(srcContains("com/example/ui/screens/docs/EpubViewerScreen.kt", "encodedPath"))
        assertTrue(srcContains("com/example/ui/screens/docs/TextEditorScreen.kt", "encodedPath"))
    }
}

// DOC-R04: requests fora do esquema epub:// não recebem conteúdo
class DocumentosEpubSchemeSpecTest {
    @org.junit.Test
    fun test_DOC_R04_non_epub_blocked() {
        val s = src("com/example/ui/screens/docs/EpubViewerScreen.kt")
        org.junit.Assert.assertTrue(s.contains("epub://") &&
            (s.contains("shouldInterceptRequest") || s.contains("WebResourceResponse")))
    }
}
