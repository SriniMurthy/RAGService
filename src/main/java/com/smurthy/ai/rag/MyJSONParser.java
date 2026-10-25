package com.smurthy.ai.rag;

public class MyJSONParser {
    private String inputJson;
    private int position;

    public MyJSONParser(String inputJson) {
        this.inputJson = inputJson;
    }

    public Object parse() {
        return null;
    }
}
/**
 * {
 *   "key1": 1,
 *   "key2": "value",
 *   "key3": [1, 2, 3],
 *   "key4": {
 *     "k1": null,
 *     "k2": false
 *   }
 * }
 */