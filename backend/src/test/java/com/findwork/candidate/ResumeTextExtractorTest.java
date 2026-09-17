package com.findwork.candidate;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class ResumeTextExtractorTest {
    private final ResumeTextExtractor extractor = new ResumeTextExtractor();

    @Test
    void extractsTextFromPdf() throws Exception {
        byte[] content;
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(PDType1Font.HELVETICA, 12);
                stream.newLineAtOffset(72, 720);
                stream.showText("Java backend engineer");
                stream.endText();
            }
            document.save(output);
            content = output.toByteArray();
        }

        assertThat(extractor.extract("resume.pdf", content)).contains("Java backend engineer");
    }

    @Test
    void extractsParagraphAndTableTextFromDocx() throws Exception {
        byte[] content;
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("Solutions engineer");
            var row = document.createTable(1, 1).getRow(0);
            row.getCell(0).setText("Spring Boot");
            document.write(output);
            content = output.toByteArray();
        }

        assertThat(extractor.extract("resume.docx", content)).contains("Solutions engineer", "Spring Boot");
    }
}
