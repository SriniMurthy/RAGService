package com.smurthy.ai.rag.messages;

import org.springframework.ai.chat.messages.AbstractMessage;
import org.springframework.ai.chat.messages.MessageType;

import java.util.Map;

public class FunctionMessage extends AbstractMessage {

    private final String name;

    public FunctionMessage(String content, String name) {
        super(MessageType.TOOL, content, Map.of("name", name));
        this.name = name;
    }

    public String getName() {
        return this.name;
    }

}