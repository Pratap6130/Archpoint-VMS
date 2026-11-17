package com.example.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.AppThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.uiPrefsDataStore by preferencesDataStore(name = "archpoint_ui_prefs")

class ThemePreferences(private val context: Context) {
  private val themeKey = stringPreferencesKey("app_theme_mode")

  val themeMode: Flow<AppThemeMode> =
    context.uiPrefsDataStore.data.map { prefs ->
      AppThemeMode.fromWire(prefs[themeKey])
    }

  suspend fun setThemeMode(mode: AppThemeMode) {
    context.uiPrefsDataStore.edit { prefs ->
      prefs[themeKey] = mode.wireValue
    }
  }

  suspend fun hasAttemptedDefaultSeed(officeId: String): Boolean {
    val key = booleanPreferencesKey("archpoint_seed_v1_$officeId")
    return context.uiPrefsDataStore.data.first()[key] == true
  }

  suspend fun markDefaultSeedAttempted(officeId: String) {
    val key = booleanPreferencesKey("archpoint_seed_v1_$officeId")
    context.uiPrefsDataStore.edit { prefs ->
      prefs[key] = true
    }
  }
}
