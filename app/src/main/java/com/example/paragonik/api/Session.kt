package com.example.paragonik.api

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.sessionStore by preferencesDataStore("session")

// Zalogowany użytkownik i jego token JWT. Zapisane w DataStore, a w pamięci trzymamy kopię,
// bo token jest potrzebny przy każdym zapytaniu. Jak user zmieni się na null, apka pokaże logowanie.
object Session {
    private val TOKEN = stringPreferencesKey("token")
    private val USER_ID = stringPreferencesKey("user_id")
    private val EMAIL = stringPreferencesKey("email")
    private val NAME = stringPreferencesKey("display_name")

    @Volatile
    var token: String? = null
        private set
    var user by mutableStateOf<UserDto?>(null)
        private set

    suspend fun load(context: Context) {
        val prefs = context.sessionStore.data.first()
        val id = prefs[USER_ID]
        token = prefs[TOKEN]
        user = if (token != null && id != null) UserDto(id, prefs[EMAIL].orEmpty(), prefs[NAME].orEmpty()) else null
    }

    suspend fun save(context: Context, auth: AuthResponse) {
        context.sessionStore.edit {
            it[TOKEN] = auth.token
            it[USER_ID] = auth.user.id
            it[EMAIL] = auth.user.email
            it[NAME] = auth.user.displayName
        }
        token = auth.token
        user = auth.user
    }

    suspend fun updateUser(context: Context, newUser: UserDto) {
        context.sessionStore.edit { it[NAME] = newUser.displayName }
        user = newUser
    }

    suspend fun clear(context: Context) {
        context.sessionStore.edit { it.clear() }
        token = null
        user = null
    }
}
