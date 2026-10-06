package com.careerforge.pdf;

import com.careerforge.v2.extras.ResumePdfService;
import com.careerforge.v2.extras.ExtrasDtos.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.*;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.nio.file.*;
import java.io.IOException;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class ResumePdfLayoutTest {
    @Test
    void selectableUnicodeResumeHonorsPrivacyAndStaysWithinPageMargins() throws Exception {
        ResumeFields fields=new ResumeFields("Java Developer","Student building practical Java applications.",
                "+91 90000 00000","Mumbai","https://example.org","B.Sc. Artificial Intelligence and Data Science",
                "Academic projects and collaborative software development.","Completed a full-stack mini-project.",
                true,false,true,true,true);
        ResumeBundle bundle=new ResumeBundle(fields,new ResumeUser("\u00c9lodie Ahuja","student@example.org","AI & Data Science","Rizvi College","Second Year"),
                List.of(new LearnedSkill(1L,new SkillView(1L,"Java"),"Intermediate")),
                List.of(new ProjectView(1L,"Hidden private project","Not selected for export","Java","","COMPLETED")));
        try(PDDocument pdf=Loader.loadPDF(new ResumePdfService().render(bundle))) {
            String text=new PDFTextStripper().getText(pdf);
            assertThat(text).contains("\u00c9lodie Ahuja","Java Developer","Education","Java").doesNotContain("Hidden private project","password","sessionVersion");
            assertThat(pdf.getNumberOfPages()).isEqualTo(1);
            checkMargins(pdf);
            render(pdf,0,"resume-single-page.png");
        }
    }
    @Test
    void longResumePaginatesWithoutClippedTextOrMissingLastSection() throws Exception {
        String content=("Developed Java services, tested REST APIs, and collaborated on accessible interfaces. ").repeat(40);
        ResumeFields fields=new ResumeFields("Full Stack Java Developer",content,"","Mumbai","",
                content,content,content+" FINAL_ACHIEVEMENT",true,true,true,true,true);
        ResumeBundle bundle=new ResumeBundle(fields,new ResumeUser("Abdurrahman Khot","student@example.org","","",""),List.of(),
                List.of(new ProjectView(1L,"CareerForge",content,"Java, Spring Boot, MySQL","https://github.com/"+"a".repeat(180),"COMPLETED")));
        try(PDDocument pdf=Loader.loadPDF(new ResumePdfService().render(bundle))) {
            assertThat(pdf.getNumberOfPages()).isGreaterThan(1);
            assertThat(new PDFTextStripper().getText(pdf)).contains("FINAL_ACHIEVEMENT");
            checkMargins(pdf);
            render(pdf,0,"resume-long-first-page.png");
            render(pdf,pdf.getNumberOfPages()-1,"resume-long-last-page.png");
        }
    }
    private void checkMargins(PDDocument document) throws IOException {
        PDFTextStripper positions=new PDFTextStripper() {
            @Override protected void processTextPosition(TextPosition position) {
                assertThat(position.getXDirAdj()).isGreaterThanOrEqualTo(46);
                assertThat(position.getYDirAdj()).isBetween(46f,document.getPage(0).getMediaBox().getHeight()-46f);
                assertThat(position.getXDirAdj()+position.getWidthDirAdj()).isLessThanOrEqualTo(document.getPage(0).getMediaBox().getWidth()-46f);
                super.processTextPosition(position);
            }
        };
        positions.getText(document);
    }
    private void render(PDDocument pdf,int page,String name) throws IOException {
        Path folder=Path.of("target","pdf-qa"); Files.createDirectories(folder);
        ImageIO.write(new PDFRenderer(pdf).renderImageWithDPI(page,110),"PNG",folder.resolve(name).toFile());
    }
}
