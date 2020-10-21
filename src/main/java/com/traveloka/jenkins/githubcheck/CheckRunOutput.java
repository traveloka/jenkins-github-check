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

  public void merge(CheckRunOutput source) {
    if (source.title != null)
      this.title = source.title;
    if (source.summary != null)
      this.summary = source.summary;
    if (source.text != null)
      this.text = source.text;
    if (source.annotations != null)
      this.annotations = source.annotations;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class AnnotationJSON {
    public String path;
    public int start_line;
    public int end_line;
    public String annotation_level;
    public String message;
    public Integer start_column;
    public Integer end_column;
    public String title;
    public String raw_details;

    Annotation toBuilder() {
      AnnotationLevel level = annotation_level != null ? AnnotationLevel.valueOf(annotation_level.toUpperCase())
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
