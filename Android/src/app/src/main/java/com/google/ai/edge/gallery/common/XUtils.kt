package com.google.ai.edge.gallery.common

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object XUtils {

    inline fun <reified T> loadModels(context: Context): JsonObjAndTextContent<T>? {
        val json = context.assets.open("model_allowlist.json").readBytes().decodeToString()
        if (json.isBlank()) {
            return null
        }
        val gson = Gson()
        val type = object : TypeToken<T>() {}.type
        val jsonObj = gson.fromJson<T>(json, type)
        return JsonObjAndTextContent(jsonObj, json)
    }

}