package com.traveloka.jenkins.githubcheck;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class CheckRunExternalId {
  private static final ObjectMapper mapper = new ObjectMapper();
  private static final Logger LOGGER = Logger.getLogger(EventListeners.class.getName());

  public String job;
  public int run;

  CheckRunExternalId(String job, int run) {
    this.job = job;
    this.run = run;
  }

  CheckRunExternalId() {
  }

  public String toString() {
    try {
      return mapper.writeValueAsString(this);
    } catch (JsonProcessingException e) {
      LOGGER.log(Level.WARNING, "Can not encode json", e);
      return "";
    }
  }

  public static CheckRunExternalId fromString(String str) {
    if (str == null || str.isEmpty()) {
      return null;
    }
    try {
      return mapper.readValue(str, CheckRunExternalId.class);
    } catch (JsonProcessingException e) {
      LOGGER.log(Level.WARNING, "Can not decode json", e);
      return null;
    }
  }
}
