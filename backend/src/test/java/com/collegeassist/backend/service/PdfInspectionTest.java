package com.collegeassist.backend.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

public class PdfInspectionTest {

    @Test
    void inspectAcademicRegulationsPdf() throws Exception {
        Path pdfPath = Paths.get("..", "docs", "academic", "Academic-Regulations.pdf").toAbsolutePath().normalize();
        if (!pdfPath.toFile().exists()) {
            pdfPath = Paths.get("docs", "academic", "Academic-Regulations.pdf").toAbsolutePath().normalize();
        }
        System.out.println("Inspecting PDF at: " + pdfPath);
        File file = pdfPath.toFile();
        if (!file.exists()) {
            System.out.println("FILE DOES NOT EXIST AT " + pdfPath);
            return;
        }

        try (PDDocument doc = Loader.loadPDF(file)) {
            int pageCount = doc.getNumberOfPages();
            System.out.println("TOTAL PAGES: " + pageCount);
            System.out.println("IS ENCRYPTED: " + doc.isEncrypted());
            if (doc.getDocumentInformation() != null) {
                System.out.println("TITLE: " + doc.getDocumentInformation().getTitle());
                System.out.println("AUTHOR: " + doc.getDocumentInformation().getAuthor());
                System.out.println("SUBJECT: " + doc.getDocumentInformation().getSubject());
            }

            PDFTextStripper stripper = new PDFTextStripper();
            for (int p = 16; p <= 18; p++) {
                stripper.setStartPage(p);
                stripper.setEndPage(p);
                String text = stripper.getText(doc);
                System.out.println("==================== PAGE " + p + " ====================");
                System.out.println(text);
            }






        }
    }
}
