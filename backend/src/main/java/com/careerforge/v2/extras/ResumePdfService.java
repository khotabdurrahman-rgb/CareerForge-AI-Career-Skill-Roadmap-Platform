package com.careerforge.v2.extras;

import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;
import org.springframework.stereotype.Service;
import org.springframework.core.io.ClassPathResource;
import java.io.*;
import java.text.Normalizer;
import java.util.*;
import static com.careerforge.v2.extras.ExtrasDtos.*;

@Service
public class ResumePdfService {
    public byte[] render(ResumeBundle bundle) {
        try(PDDocument document=new PDDocument(); ByteArrayOutputStream output=new ByteArrayOutputStream()) {
            document.getDocumentInformation().setTitle("Resume");
            document.getDocumentInformation().setAuthor(bundle.user().name());
            try(Layout layout=new Layout(document)) {
                ResumeFields p=bundle.profile();
                layout.text(bundle.user().name(),20,true);
                layout.text(p.headline(),12,true);
                layout.text(join(bundle.user().email(),p.phone(),p.location(),p.website()),10,false);
                layout.section("Summary",p.summary());
                if(Boolean.TRUE.equals(p.includeSkills()) && !bundle.skills().isEmpty()) {
                    layout.heading("Skills");
                    layout.text(String.join(", ",bundle.skills().stream().map(s->s.skill().name()).toList()),10,false);
                }
                if(Boolean.TRUE.equals(p.includeProjects()) && !bundle.projects().isEmpty()) {
                    layout.heading("Projects");
                    for(ProjectView project:bundle.projects()) {
                        layout.text(project.name(),11,true);
                        layout.text(project.description(),10,false);
                        layout.text(join(project.technology(),project.githubUrl()),10,false);
                        layout.space(6);
                    }
                }
                if(Boolean.TRUE.equals(p.includeEducation())) layout.section("Education",p.education());
                if(Boolean.TRUE.equals(p.includeExperience())) layout.section("Experience",p.experience());
                if(Boolean.TRUE.equals(p.includeAchievements())) layout.section("Achievements",p.achievements());
            }
            document.save(output);
            return output.toByteArray();
        } catch(IOException ex) { throw new IllegalStateException("Cannot generate resume PDF",ex); }
    }
    private static String join(String... values) {
        return Arrays.stream(values).filter(v->v!=null && !v.isBlank()).collect(java.util.stream.Collectors.joining(" | "));
    }
    private static String safe(String input,PDFont font) throws IOException {
        if(input==null) return "";
        boolean fallback=font instanceof PDType1Font;
        String normalized=fallback ? Normalizer.normalize(input,Normalizer.Form.NFKD) : input;
        normalized=normalized.replace("\r\n","\n").replace('\r','\n');
        StringBuilder result=new StringBuilder();
        for(int cp:normalized.codePoints().toArray()) {
            if(fallback && Character.getType(cp)==Character.NON_SPACING_MARK) continue;
            if(cp=='\n') result.append('\n');
            else if(Character.isWhitespace(cp)) result.append(' ');
            else if(Character.isISOControl(cp) || Character.getType(cp)==Character.FORMAT) continue;
            else {
                String glyph=new String(Character.toChars(cp));
                try { font.encode(glyph); result.append(glyph); }
                catch(IllegalArgumentException ex) {
                    if(cp==0x2018 || cp==0x2019) result.append('\'');
                    else if(cp==0x201c || cp==0x201d) result.append('"');
                    else if(cp==0x2013 || cp==0x2014 || cp==0x2022) result.append('-');
                    else result.append('?');
                }
            }
        }
        return result.toString();
    }
    private static final class Layout implements AutoCloseable {
        private static final float MARGIN=48, WIDTH=PDRectangle.A4.getWidth()-2*MARGIN;
        private final PDDocument document;
        private final PDFont regular;
        private final PDFont bold;
        private PDPageContentStream stream;
        private float y;
        Layout(PDDocument document) throws IOException {
            this.document=document;
            regular=font("NotoSans-Regular.ttf",Standard14Fonts.FontName.HELVETICA);
            bold=font("NotoSans-Bold.ttf",Standard14Fonts.FontName.HELVETICA_BOLD);
            page();
        }
        private PDFont font(String name,Standard14Fonts.FontName fallback) throws IOException {
            ClassPathResource resource=new ClassPathResource("fonts/"+name);
            if(!resource.exists()) return new PDType1Font(fallback);
            try(InputStream input=resource.getInputStream()) { return PDType0Font.load(document,input,true); }
        }
        private void page() throws IOException {
            if(stream!=null) stream.close();
            PDPage page=new PDPage(PDRectangle.A4); document.addPage(page);
            stream=new PDPageContentStream(document,page); y=PDRectangle.A4.getHeight()-MARGIN;
        }
        void space(float amount) { y-=amount; }
        void heading(String title) throws IOException {
            if(y-MARGIN<48) page();
            space(12); text(title,12,true); space(3);
        }
        void section(String title,String content) throws IOException {
            if(content==null || content.isBlank()) return;
            heading(title); text(content,10,false);
        }
        void text(String raw,float size,boolean strong) throws IOException {
            if(raw==null || raw.isBlank()) return;
            PDFont font=strong ? bold : regular;
            for(String paragraph:safe(raw,font).split("\n",-1)) {
                StringBuilder line=new StringBuilder();
                for(String word:paragraph.strip().split("\\s+")) {
                    if(word.isEmpty()) continue;
                    String candidate=line.isEmpty() ? word : line+" "+word;
                    if(width(candidate,font,size)<=WIDTH) { line.setLength(0); line.append(candidate); continue; }
                    if(!line.isEmpty()) { draw(line.toString(),font,size); line.setLength(0); }
                    // Split long URLs and unbroken words by measured glyph widths.
                    for(int cp:word.codePoints().toArray()) {
                        String glyph=new String(Character.toChars(cp));
                        if(width(line.toString()+glyph,font,size)>WIDTH && !line.isEmpty()) {
                            draw(line.toString(),font,size); line.setLength(0);
                        }
                        line.append(glyph);
                    }
                }
                if(!line.isEmpty()) draw(line.toString(),font,size);
                else space(size*0.5f);
            }
        }
        private float width(String text,PDFont font,float size) throws IOException { return font.getStringWidth(text)*size/1000; }
        private void draw(String text,PDFont font,float size) throws IOException {
            float height=size*1.45f;
            if(y-height<MARGIN) page();
            stream.beginText(); stream.setFont(font,size); stream.newLineAtOffset(MARGIN,y-size);
            stream.showText(text); stream.endText(); y-=height;
        }
        @Override public void close() throws IOException { if(stream!=null) stream.close(); }
    }
}
