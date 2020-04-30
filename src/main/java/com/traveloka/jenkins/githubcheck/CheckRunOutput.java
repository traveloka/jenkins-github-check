package com.traveloka.jenkins.githubcheck;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import org.kohsuke.github.GHCheckRun.AnnotationLevel;
import org.kohsuke.github.GHCheckRunBuilder.Annotation;
import org.kohsuke.github.GHCheckRunBuilder.Output;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CheckRunOutput implements Cloneable {
  public String title;
  public String summary;
  public String text;
  public List<AnnotationJSON> annotations;

  public Output toBuilder() {
    Output output = new Output(title, summary);
    output.withText(text);
    if (annotations != null) {
      for (AnnotationJSON a : annotations) {
        output.add(a.toBuilder());
      }
    }
    return output;
  }

  public Object clone() throws CloneNotSupportedException {
    return super.clone();
  }

  public static class AnnotationJSON {
    String path;
    int start_line;
    int end_line;
    String annotation_level;
    String message;
    Integer start_column;
    Integer end_column;
    String title;
    String raw_details;

    Annotation toBuilder() {
      AnnotationLevel level = annotation_level != null ? AnnotationLevel.valueOf(annotation_level)
          : AnnotationLevel.NOTICE;
      Annotation a = new Annotation(path, start_line, end_line, level, message);
      if (start_column != null) {
        a.withStartColumn(start_column);
      }
      if (end_column != null) {
        a.withEndColumn(end_column);
      }
      if (title != null) {
        a.withTitle(title);
      }
      if (raw_details != null) {
        a.withRawDetails(raw_details);
      }
      return a;
    }
  }
}
