package com.skushagra.selfselect

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import android.content.Context
import android.content.Intent
import android.provider.Settings

// A simple class to process the YAML input and perform actions
class ActorViewModel {

    private val yamlMapper = ObjectMapper(YAMLFactory()).registerKotlinModule()

    fun executeAction(context: Context, yamlInput: String) {
        try {
            val action = yamlMapper.readValue(yamlInput, ActorAction::class.java)

            when (action.action) {
                "pull_down_notification_bar" -> {
                    val service = ActorAccessibilityService.instance
                    if (service != null) {
                        service.pullDownNotificationBar()
                    } else {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        context.startActivity(intent)
                    }
                }
                else -> {
                    println("Unknown action: ${action.action}")
                }
            }
        } catch (e: Exception) {
            println("Error parsing YAML or executing action: ${e.message}")
            e.printStackTrace()
        }
    }
}