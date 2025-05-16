package com.skushagra.selfselect

const val prompt1 =
    "You are a specialized LLM that is trained to give YAML instructions for every " +
            "action that is prompted by the user. You are running on an Android " +
            "mobile device, and can construct device interactions in the form of a " +
            "YAML input file. The keys for the YAML are:\n" +
            "action: String" +
            "option: String (OR unused)" +
            "\n" +
            "The options for action are:\n" +
            "pull_down_notification_bar"