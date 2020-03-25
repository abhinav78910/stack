package me.tylerbwong.stack.data.auth

import me.tylerbwong.stack.data.auth.LogOutResult.LogOutError
import me.tylerbwong.stack.data.auth.LogOutResult.LogOutSuccess
import me.tylerbwong.stack.data.model.User
import me.tylerbwong.stack.data.network.ServiceProvider
import me.tylerbwong.stack.data.network.service.AuthService
import me.tylerbwong.stack.data.network.service.UserService
import me.tylerbwong.stack.data.persistence.StackDatabase
import me.tylerbwong.stack.data.persistence.dao.AnswerDraftDao
import me.tylerbwong.stack.data.persistence.dao.SearchDao
import timber.log.Timber

class AuthRepository(
    private val answerDraftDao: AnswerDraftDao = StackDatabase.getInstance().getAnswerDraftDao(),
    private val searchDao: SearchDao = StackDatabase.getInstance().getSearchDao(),
    private val userService: UserService = ServiceProvider.userService,
    private val authService: AuthService = ServiceProvider.authService,
    private val authStore: AuthStore = AuthStore
) {
    suspend fun logOut(): LogOutResult {
        val accessToken = authStore.accessToken

        return try {
            if (!accessToken.isNullOrBlank()) {
                authService.logOut(accessToken = accessToken)
                answerDraftDao.clearDrafts()
                searchDao.clearSearches()
                authStore.clear()
                LogOutSuccess
            } else {
                throw IllegalStateException("Could not log user out for null access token")
            }
        } catch (ex: Exception) {
            Timber.e(ex)
            LogOutError
        }
    }

    /**
     * Fetches the currently logged in user.
     *
     * @return A [User] instance if there is a valid accessToken, otherwise null
     */
    suspend fun getCurrentUser(): User? {
        return try {
            if (authStore.isAuthenticatedLiveData.value == true) {
                userService.getCurrentUser().items.firstOrNull()
            } else {
                null
            }
        } catch (ex: Exception) {
            Timber.e(ex)
            null
        }
    }
}

sealed class LogOutResult {
    object LogOutSuccess : LogOutResult()
    object LogOutError : LogOutResult()
}
