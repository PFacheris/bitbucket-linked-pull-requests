package me.facheris.bitbucket.plugins.rest;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ErrorRestResourceModel {
    public List<Map<String, String>> errors;

    public ErrorRestResourceModel(String title, String message) {
        HashMap<String, String> error = new HashMap<String, String>();
        error.put("title", title);
        error.put("message", message);
        this.errors = new ArrayList<Map<String, String>>();
        this.errors.add(error);
    }
}
